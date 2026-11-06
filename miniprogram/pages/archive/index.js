const api = require('../../services/api')
const auth = require('../../services/auth')
const dateUtil = require('../../utils/date')
const navUtil = require('../../utils/nav')
const tab = require('../../utils/tab')
const format = require('../../utils/format')
const share = require('../../utils/share')
const view = require('../../utils/view')

Page({
  data: {
    nav: {},
    loading: true,
    member: {},
    stats: {},
    latestRecord: null,
    totalSessions: 0,
    sessionTotal: 0,
    durationTotal: 0,
    latestWeight: null,
    weightDelta: '0.0',
    chartMode: 'sessions',
    chartPoints: [],
    chartStat: {
      meta: 'SESSIONS / WEEKS',
      value: '—',
      unit: '次',
      label: '近期训练次数',
      note: '',
      foot: 'VOLT / TREND',
    },
    sessionTrend: [],
    durationTrend: [],
    weightTrend: [],
    latestBody: null,
    bodySheetVisible: false,
    form: { heightCm: '', weightKg: '' },
    bmiPreview: '',
    saving: false,
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    if (auth.isLoggedIn()) this.load()
    else this.setData({ loading: false })
  },

  onShow() {
    if (!auth.isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    tab.select(this, 3)
    tab.setHidden(this, false)
    if (!this.data.loading) this.load(!this.loadedOnce)
  },

  redirectToLogin() {
    if (this._redirectingLogin) return
    this._redirectingLogin = true
    wx.reLaunch({ url: '/pages/login/index' })
    wx.showToast({ title: '登录后查看', icon: 'none' })
  },

  load(withLoading) {
    const showLoading = withLoading !== false
    if (showLoading) this.setData({ loading: true })
    return api.archive
      .get()
      .then((data) => {
        const records = (data.records || []).map((record) =>
          Object.assign({}, record, {
            image: view.recordImage(record),
            name: record.programName || view.programName(record.programNo || record.programCode),
            dateShort: dateUtil.shortDate(record.date),
            dateLabel: `${dateUtil.weekdayCn(record.date)} ${record.startTime || record.time}`,
            modeLabel: record.mode === 'free' ? 'FREE TRAIN' : 'CLASS',
          }),
        )
        const weights = data.bodyLogs || []
        const member = data.member || {}
        const firstWeight = weights.length ? weights[0].weightKg : member.weightKg
        const latestWeight = weights.length
          ? weights[weights.length - 1].weightKg
          : member.weightKg
        const delta = Number((latestWeight - firstWeight).toFixed(1))
        const sessionTotal = (data.sessionTrend || []).reduce((sum, item) => sum + Number(item.value), 0)
        const durationTotal = (data.durationTrend || []).reduce(
          (sum, item) => sum + Number(item.value),
          0,
        )
        this.loadedOnce = true
        this.setData(
          {
            loading: false,
            member,
            stats: data.stats || {},
            latestRecord: records[0] || null,
            totalSessions: (data.stats && data.stats.totalSessions) || 0,
            sessionTotal,
            durationTotal,
            latestWeight,
            weightDelta: `${delta > 0 ? '+' : ''}${delta.toFixed(1)}`,
            sessionTrend: data.sessionTrend || [],
            durationTrend: data.durationTrend || [],
            weightTrend: data.weightTrend || [],
            latestBody: weights.length ? weights[weights.length - 1] : null,
          },
          () => this.refreshChart(),
        )
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  refreshChart() {
    const mode = this.data.chartMode
    let points =
      mode === 'weight'
        ? this.data.weightTrend
        : mode === 'duration'
          ? this.data.durationTrend
          : this.data.sessionTrend
    if (mode !== 'weight' && points.length && Number(points[points.length - 1].value) === 0) {
      points = points.slice(0, -1)
    }
    this.setData({ chartPoints: points, chartStat: this.buildChartStat(mode, points.length) })
  },

  buildChartStat(mode, weeks) {
    const stats = this.data.stats || {}
    if (mode === 'weight') {
      const latest = this.data.latestWeight
      return {
        meta: 'BODY LOG / 体重',
        value: latest ? Number(latest).toFixed(1) : '—',
        unit: 'kg',
        label: '最近一次体重',
        note: latest ? `较首次记录 ${this.data.weightDelta} kg` : '还没有身体数据',
        foot: 'VOLT / BODY',
      }
    }
    if (mode === 'duration') {
      const avg = this.data.sessionTotal
        ? Math.round(this.data.durationTotal / this.data.sessionTotal)
        : 0
      return {
        meta: `MINUTES / ${weeks} WEEKS`,
        value: this.data.durationTotal,
        unit: '分钟',
        label: `近 ${weeks} 周训练时长`,
        note: `本周 ${stats.weekMinutes || 0} 分钟 · 平均每次 ${avg} 分钟`,
        foot: `VOLT / ${weeks}W`,
      }
    }
    return {
      meta: `SESSIONS / ${weeks} WEEKS`,
      value: this.data.sessionTotal,
      unit: '次',
      label: `近 ${weeks} 周训练次数`,
      note: `本周 ${stats.weekSessions || 0} 次 · 连续通电 ${stats.streakWeeks || 0} 周`,
      foot: `VOLT / ${weeks}W`,
    }
  },

  onToggleChart(event) {
    const mode = event.currentTarget.dataset.mode
    if (mode === this.data.chartMode) return
    this.setData({ chartMode: mode }, () => this.refreshChart())
  },

  openBodySheet() {
    tab.setHidden(this, true)
    const member = this.data.member
    this.setData({
      bodySheetVisible: true,
      form: {
        heightCm: String(member.heightCm || ''),
        weightKg: String(member.weightKg || ''),
      },
      bmiPreview: member.bmi ? String(member.bmi) : '',
    })
  },

  onCloseBodySheet() {
    tab.setHidden(this, false)
    this.setData({ bodySheetVisible: false })
  },

  onFormInput(event) {
    const field = event.currentTarget.dataset.field
    const value = event.detail.value
    const form = Object.assign({}, this.data.form, { [field]: value })
    const height = Number(form.heightCm)
    const weight = Number(form.weightKg)
    let bmiPreview = this.data.bmiPreview
    if (height > 0 && weight > 0) {
      bmiPreview = (weight / Math.pow(height / 100, 2)).toFixed(1)
    }
    this.setData({ form, bmiPreview })
  },

  onSaveBody() {
    if (this.data.saving) return
    const heightCm = Number(this.data.form.heightCm)
    const weightKg = Number(this.data.form.weightKg)
    if (!(heightCm >= 120 && heightCm <= 230)) {
      wx.showToast({ title: '身高需在 120–230 cm', icon: 'none' })
      return
    }
    if (!(weightKg >= 30 && weightKg <= 250)) {
      wx.showToast({ title: '体重需在 30–250 kg', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    api.archive
      .addBodyLog({ heightCm, weightKg })
      .then(() => {
        tab.setHidden(this, false)
        this.setData({ saving: false, bodySheetVisible: false })
        wx.showToast({ title: '身体数据已记录', icon: 'success' })
        this.load(false)
      })
      .catch((error) => {
        this.setData({ saving: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onGoCheckin() {
    wx.switchTab({ url: '/pages/checkin/index' })
  },

  onGoLogs() {
    wx.navigateTo({ url: '/pages/logs/index' })
  },

  goRanking() {
    wx.navigateTo({ url: '/pages/logs/index?tab=ranking' })
  },

  goCalendar() {
    wx.navigateTo({ url: '/pages/calendar/index' })
  },

  onShareAppMessage() {
    const member = this.data.member || {}
    const stats = this.data.stats || {}
    return share.inviteShare({
      program: 'P01',
      ref: member.referralCode,
      from: member.nickname,
      title: `我的 VOLT 训练档案：${stats.totalSessions || 0} 次训练 / 连续 ${stats.streakWeeks || 0} 周，来一起通电`,
      imageUrl: '/assets/images/hero.jpg',
    })
  },

  onShareTimeline() {
    return {
      title: '我的 VOLT 训练档案｜按次付费，不办年卡',
      query: `ref=${(this.data.member && this.data.member.referralCode) || ''}`,
      imageUrl: '/assets/images/hero.jpg',
    }
  },
})
