const api = require('../../services/api')
const dateUtil = require('../../utils/date')
const navUtil = require('../../utils/nav')
const tab = require('../../utils/tab')
const format = require('../../utils/format')
const share = require('../../utils/share')
const scan = require('../../services/scan')
const geo = require('../../services/geo')
const view = require('../../utils/view')

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

Page({
  data: {
    nav: {},
    loading: true,
    platform: '',
    phase: 'idle',
    checkStage: 0,
    checkVenue: null,
    member: { credits: 0 },
    stats: {},
    todayBooking: null,
    todayVenueCode: '',
    todayRecord: null,
    recentRecords: [],
    venues: [],
    brand: {},
    success: null,
    shareRecord: null,
    shareImage: '',
    checkingCode: '',
    scrollTop: 0,
  },

  onLoad() {
    this._alive = true
    const nav = navUtil.getNavMetrics()
    let platform = ''
    try {
      platform = wx.getSystemInfoSync().platform
    } catch (error) {
      platform = ''
    }
    this.setData({ nav, platform })
    this.load()
  },

  onShow() {
    tab.select(this, 2)
    tab.setHidden(this, this.data.phase !== 'idle')
    const app = getApp()
    if (app.globalData.autoScan) {
      app.globalData.autoScan = false
      this.pendingAutoScan = true
    }
    if (this.loadedOnce && this.data.phase === 'idle') this.load(false)
  },

  onHide() {
    this.clearStageTimers()
    tab.setHidden(this, false)
  },

  onUnload() {
    this._alive = false
    this.clearStageTimers()
  },

  load(withLoading) {
    const showLoading = withLoading !== false
    if (showLoading) this.setData({ loading: true })
    return api.checkin
      .info()
      .then((data) => {
        if (!this._alive) return
        const recentRecords = (data.recentRecords || []).map((record) =>
          Object.assign({}, record, {
            image: view.recordImage(record),
            dateShort: dateUtil.shortDate(record.date),
            stamp: dateUtil.stamp(record.checkedAt),
          }),
        )
        this.loadedOnce = true
        this.setData({
          loading: false,
          member: data.member,
          stats: data.stats || {},
          todayBooking: data.todayBooking,
          todayVenueCode: data.todayBooking ? data.todayBooking.venueCode : '',
          todayRecord: data.todayRecord,
          recentRecords,
          venues: data.venues,
          brand: data.brand,
        })
        if (this.pendingAutoScan) {
          this.pendingAutoScan = false
          setTimeout(() => this.triggerScan(), 480)
        }
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  triggerScan() {
    if (this.data.phase === 'checking') return
    if (this.data.platform === 'devtools') {
      this.onSimulate()
      return
    }
    this.onScan()
  },

  onScan() {
    if (this.data.phase === 'checking') return
    scan
      .scanCode()
      .then((res) => this.processVenue(res.code))
      .catch((error) => {
        if (error.message === 'SCAN_CANCEL') return
        if (error.message === 'CAMERA_DENIED') {
          wx.showModal({
            title: '相机权限未开启',
            content: '可以在右上角设置里允许相机，或直接手动选择场馆完成演示。',
            confirmText: '选择场馆',
            cancelText: '知道了',
            confirmColor: '#A33B2B',
            success: (res) => {
              if (res.confirm) this.onSimulate()
            },
          })
          return
        }
        if (error.message === 'NOT_VOLT_QR') {
          wx.showModal({
            title: '不是 VOLT 场馆码',
            content: '请扫描前台带有 VOLT 标识的二维码。演示环境也可以手动选择场馆。',
            confirmText: '选择场馆',
            cancelText: '继续扫描',
            confirmColor: '#A33B2B',
            success: (res) => {
              if (res.confirm) this.onSimulate()
              else this.onScan()
            },
          })
          return
        }
        wx.showToast({ title: '相机未打开，可手动选择场馆', icon: 'none' })
      })
  },

  onSimulate() {
    if (this.data.phase === 'checking') return
    const todayVenue = this.data.todayBooking ? this.data.todayBooking.venueCode : ''
    scan
      .pickDemoVenue(todayVenue)
      .then((code) => this.processVenue(code))
      .catch(() => {})
  },

  processVenue(code) {
    tab.setHidden(this, true)
    const venue = this.data.venues.find((item) => item.code === code)
    this.setData({
      phase: 'checking',
      checkStage: 0,
      checkVenue: venue || null,
      checkingCode: code,
    })
    this.clearStageTimers()
    this.stageTimers = [
      setTimeout(() => this.setData({ checkStage: 1 }), 420),
      setTimeout(() => this.setData({ checkStage: 2 }), 940),
    ]
    Promise.all([geo.verifyVenue(code), delay(1180)])
      .then((results) => {
        const location = results[0]
        this.locationMeta = location
        return api.checkin.auto(code, { distanceMeters: location.distanceMeters })
      })
      .then((result) => {
        if (!this._alive) return
        this.clearStageTimers()
        this.handleCheckinResult(result, code)
      })
      .catch((error) => {
        if (!this._alive) return
        this.clearStageTimers()
        tab.setHidden(this, false)
        this.setData({ phase: 'idle' })
        if (error && error.code === 'NO_CREDITS') {
          wx.showModal({
            title: '充电次数不足',
            content: '次卡余额为 0，本次课程无法核销。可以先记录一次自由训练，不消耗次卡。',
            confirmText: '自由训练',
            cancelText: '取消',
            confirmColor: '#A33B2B',
            success: (res) => {
              if (res.confirm) this.freeCheckin(code)
            },
          })
          return
        }
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  handleCheckinResult(result, code) {
    const self = this
    if (result.mode === 'class') {
      this.showSuccess({
        mode: 'class',
        member: result.member,
        stats: result.stats,
        record: result.record,
        venue: result.venue,
        locationLabel: result.locationLabel,
      })
      return
    }
    if (result.mode === 'free') {
      this.showSuccess({
        mode: 'free',
        member: result.member,
        stats: result.stats,
        record: result.record,
        venue: result.venue,
        locationLabel: result.locationLabel,
      })
      return
    }
    if (result.mode === 'already') {
      this.showSuccess({
        mode: 'already',
        member: result.member,
        stats: result.stats,
        record: result.record,
        venue: result.venue,
        locationLabel: this.locationMeta ? this.locationMeta.label : '场馆码已校验',
      })
      return
    }
    if (result.mode === 'wrong-venue') {
      const booking = result.booking
      this.setData({ phase: 'idle' })
      wx.showModal({
        title: '今日课程在另一家场馆',
        content: `今天的「${booking.programName}」在${booking.venueName} ${booking.time}。要在这里记录一次自由训练吗？`,
        confirmText: '自由训练',
        cancelText: '暂不',
        confirmColor: '#A33B2B',
        success(res) {
          if (res.confirm) self.freeCheckin(code)
        },
      })
      return
    }
    if (result.mode === 'free-ready') {
      this.setData({ phase: 'idle' })
      wx.showModal({
        title: '今日没有待核销课程',
        content: `在${result.venue.name}记录一次 45 分钟自由训练？不会消耗充电次数。`,
        confirmText: '记录训练',
        cancelText: '取消',
        confirmColor: '#A33B2B',
        success(res) {
          if (res.confirm) self.freeCheckin(code)
        },
      })
    }
  },

  freeCheckin(code) {
    tab.setHidden(this, true)
    const venue = this.data.venues.find((item) => item.code === code)
    this.setData({
      phase: 'checking',
      checkStage: 2,
      checkVenue: venue || null,
      checkingCode: code,
    })
    this.clearStageTimers()
    const distance = this.locationMeta ? this.locationMeta.distanceMeters : undefined
    delay(720)
      .then(() => api.checkin.free(code, { distanceMeters: distance }))
      .then((result) => {
        if (!this._alive) return
        this.showSuccess({
          mode: 'free',
          member: result.member,
          record: result.record,
          venue: result.venue,
          locationLabel: result.locationLabel,
        })
      })
      .catch((error) => {
        if (!this._alive) return
        tab.setHidden(this, false)
        this.setData({ phase: 'idle' })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  showSuccess(payload) {
    if (!this._alive) return
    this.clearStageTimers()
    const record = Object.assign({}, payload.record || {}, {
      stamp: dateUtil.stamp((payload.record && payload.record.checkedAt) || Date.now()),
    })
    const image = view.recordImage(record)
    const recentRecords = [Object.assign({}, record, { image })].concat(
      this.data.recentRecords.filter((item) => item.id !== record.id),
    )
    const stats = payload.stats || this.data.stats || {}
    this.setData({
      phase: 'success',
      success: Object.assign({}, payload, { record, stats }),
      stats,
      shareRecord: record,
      shareImage: image,
      member: payload.member,
      recentRecords: recentRecords.slice(0, 5),
      todayRecord: payload.mode === 'class' || payload.mode === 'free' ? record : this.data.todayRecord,
    })
    if (wx.vibrateShort) wx.vibrateShort({ type: 'medium' })
  },

  onCloseSuccess() {
    tab.setHidden(this, false)
    this.setData({ phase: 'idle', success: null })
    this.load(false)
  },

  onScanAgain() {
    this.setData({ phase: 'idle', success: null })
    setTimeout(() => {
      if (this._alive) this.triggerScan()
    }, 300)
  },

  onGoArchive() {
    tab.setHidden(this, false)
    this.setData({ phase: 'idle', success: null })
    tab.switchTab('/pages/archive/index')
  },

  onGoBook() {
    tab.switchTab('/pages/schedule/index')
  },

  onOpenVenue(event) {
    geo.openVenueMap(event.currentTarget.dataset.code)
  },

  onCall() {
    wx.makePhoneCall({ phoneNumber: this.data.brand.phone || '4009006688' })
  },

  clearStageTimers() {
    if (this.stageTimers) {
      this.stageTimers.forEach((timer) => clearTimeout(timer))
      this.stageTimers = null
    }
  },

  onShareAppMessage() {
    const record = this.data.shareRecord
    const member = this.data.member || {}
    if (this.data.phase === 'success' && record) {
      return share.inviteShare({
        program: record.programCode,
        ref: member.referralCode,
        from: member.nickname,
        title: `我在 VOLT 完成「${record.programName}」，${record.durationMin} 分钟 / ${record.kcal} kcal，来一起通电`,
        imageUrl: this.data.shareImage,
      })
    }
    return share.inviteShare({
      program: this.data.todayBooking ? this.data.todayBooking.programCode : 'P01',
      ref: member.referralCode,
      from: member.nickname,
      title: 'VOLT 场馆扫码进场｜到店即核销，按次付费',
    })
  },

  onShareTimeline() {
    const record = this.data.shareRecord
    return {
      title: record
        ? `我在 VOLT 完成「${record.programName}」，来一起通电`
        : 'VOLT 会员终端｜到店扫码签到',
      query: `program=${record ? record.programCode : 'P01'}`,
      imageUrl: this.data.shareImage || '/assets/images/gym-space.webp',
    }
  },
})
