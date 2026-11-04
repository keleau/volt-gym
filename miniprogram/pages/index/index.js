const api = require('../../services/api')
const auth = require('../../services/auth')
const dateUtil = require('../../utils/date')
const navUtil = require('../../utils/nav')
const tab = require('../../utils/tab')
const format = require('../../utils/format')
const share = require('../../utils/share')

Page({
  data: {
    nav: {},
    loading: true,
    error: '',
    member: null,
    stats: {},
    nextBooking: null,
    todayBooking: null,
    challenge: null,
    challengePercent: 0,
    events: [],
    nextEvent: null,
    passCells: [],
    pendingCard: null,
    expiresShort: '',
    entryVisible: false,
    entryBooking: null,
    purchasePromptVisible: false,
    joiningChallenge: false,
    joiningEventId: '',
    brand: {},
    messagesUnread: 0,
    greeting: '',
    nextDayText: '',
    isLocked: false,
    nextIsToday: false,
    loginVisible: false,
    navSolid: false,
    refreshing: false,
    scrollTop: 0,
  },

  onLoad() {
    this._alive = true
    this.setData({ nav: navUtil.getNavMetrics(), greeting: dateUtil.greeting() })
    this.load()
  },

  onShow() {
    tab.select(this, 0)
    tab.setHidden(this, false)
    this.setData({ greeting: dateUtil.greeting() })
    if (this.loadedOnce) this.load(false)
  },

  onHide() {
    this.stopLockTicker()
    clearTimeout(this.purchasePromptTimer)
    tab.setHidden(this, false)
    if (this.data.entryVisible) this.setData({ entryVisible: false })
    if (this.data.purchasePromptVisible) this.setData({ purchasePromptVisible: false })
  },

  onUnload() {
    this._alive = false
    this.stopLockTicker()
    clearTimeout(this.purchasePromptTimer)
  },

  onRefresh() {
    this.setData({ refreshing: true })
    this.load(false).then(() => {
      setTimeout(() => this.setData({ refreshing: false }), 260)
    })
  },

  onScroll(event) {
    const top = event.detail.scrollTop
    const solid = top > 80
    if (solid !== this.data.navSolid) this.setData({ navSolid: solid })
  },

  load(withLoading) {
    const showLoading = withLoading !== false
    if (showLoading) this.setData({ loading: true, error: '' })
    return Promise.all([api.home.get(), api.community.get().catch(() => null)])
      .then(([data, community]) => {
        if (!this._alive) return
        const next = data.nextBooking
        const challenge = community ? community.challenge : null
        const challengePercent = challenge
          ? Math.min(
              100,
              Math.round(
                (Number(challenge.myProgress || 0) / Math.max(1, Number(challenge.sessions || 1))) *
                  100,
              ),
            )
          : 0
        const events = community
          ? (community.events || []).map((event) =>
              Object.assign({}, event, {
                monthShort: String(event.month || '').split('·')[0].trim(),
              }),
            )
          : []
        const nextEvent = events[0] || null
        const creditsUsed = Math.max(0, data.member.creditsTotal - data.member.credits)
        const passCells = []
        for (let index = 1; index <= data.member.creditsTotal; index += 1) {
          passCells.push({ no: index, used: index <= creditsUsed })
        }
        this.loadedOnce = true
        this.setData({
          loading: false,
          error: '',
          member: data.member,
          stats: data.stats,
          nextBooking: next,
          todayBooking: data.todayBooking,
          brand: data.brand,
          challenge,
          challengePercent,
          events,
          nextEvent,
          passCells,
          pendingCard: (data.cardOrders || []).find((order) => order.status === 'pending') || null,
          expiresShort: String(data.member.expiresAt || '').replace(/-/g, '.'),
          messagesUnread: data.messagesUnread,
          greeting: dateUtil.greeting(),
          nextDayText: next
            ? `${dateUtil.relativeDay(next.date)} · ${dateUtil.weekdayCn(next.date)}`
            : '',
          isLocked: !!(next && next.status === 'locked'),
          nextIsToday: !!(next && next.date === dateUtil.todayISO()),
          entryBooking: data.todayBooking || null,
        })
        this.startLockTicker()
        this.maybePromptPurchase(data)
        if (auth.isLoggedIn() && data.member) auth.refreshUser(data.member)
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ loading: false, error: format.errorText(error) })
      })
  },

  maybePromptPurchase(data) {
    const member = data.member || {}
    const hasPending = (data.cardOrders || []).some((order) => order.status === 'pending')
    if (Number(member.credits) > 0 || hasPending || this.purchasePromptShown) return
    let shown = false
    try {
      shown = Boolean(wx.getStorageSync('volt.purchasePromptShown'))
    } catch (error) {
      shown = false
    }
    if (shown) return
    this.purchasePromptShown = true
    this.purchasePromptTimer = setTimeout(() => {
      if (this._alive) this.setData({ purchasePromptVisible: true })
    }, 420)
  },

  markPurchasePromptSeen() {
    try {
      wx.setStorageSync('volt.purchasePromptShown', true)
    } catch (error) {
      // storage blocked; still hide for this session
    }
  },

  closePurchasePrompt() {
    this.markPurchasePromptSeen()
    this.setData({ purchasePromptVisible: false })
  },

  goPurchase() {
    this.markPurchasePromptSeen()
    this.setData({ purchasePromptVisible: false })
    if (this.data.member && this.data.member.phoneBound === false) {
      wx.navigateTo({ url: '/pages/bind-phone/index' })
      return
    }
    wx.navigateTo({ url: '/pages/pass/index?buy=1' })
  },

  startLockTicker() {
    this.stopLockTicker()
    const next = this.data.nextBooking
    if (!next || next.status !== 'locked' || !next.lockExpiresAt) return
    this.lockTimer = setInterval(() => {
      const remain = next.lockExpiresAt - Date.now()
      if (remain <= 0) {
        this.stopLockTicker()
        this.load(false)
        return
      }
      this.setData({ 'nextBooking.lockText': dateUtil.formatLock(remain) })
    }, 1000)
  },

  stopLockTicker() {
    if (this.lockTimer) {
      clearInterval(this.lockTimer)
      this.lockTimer = null
    }
  },

  onQuickScan() {
    tab.switchTab('/pages/checkin/index', { autoScan: true })
  },

  onQuickBook() {
    tab.switchTab('/pages/schedule/index')
  },

  onQuickArchive() {
    tab.switchTab('/pages/archive/index')
  },

  onQuickMine() {
    tab.switchTab('/pages/mine/index')
  },

  goCheckin() {
    tab.switchTab('/pages/checkin/index')
  },

  goMessages() {
    wx.navigateTo({ url: '/pages/messages/index' })
  },

  goSchedule() {
    tab.switchTab('/pages/schedule/index')
  },

  goProgram(event) {
    const programCode = event.currentTarget.dataset.code
    tab.switchTab('/pages/schedule/index', { schedulePreset: { programCode } })
  },

  goMine() {
    tab.switchTab('/pages/mine/index')
  },

  goBookings() {
    wx.navigateTo({ url: '/pages/bookings/index' })
  },

  onCancelNext() {
    const booking = this.data.nextBooking
    if (!booking) return
    wx.showModal({
      title: '取消这节预约？',
      content: `${dateUtil.relativeDay(booking.date)} ${booking.time} ${booking.programName}；开课前可取消，不扣充电次数。`,
      confirmText: '取消预约',
      cancelText: '保留',
      confirmColor: '#A33B2B',
      success: (res) => {
        if (!res.confirm) return
        wx.showLoading({ title: '正在释放名额', mask: true })
        api.bookings
          .cancel(booking.id)
          .then(() => {
            wx.hideLoading()
            wx.showToast({ title: '名额已释放', icon: 'none' })
            this.load(false)
          })
          .catch((error) => {
            wx.hideLoading()
            wx.showToast({ title: format.errorText(error), icon: 'none' })
          })
      },
    })
  },

  onConfirmNext() {
    const booking = this.data.nextBooking
    if (!booking) return
    wx.showLoading({ title: '确认中', mask: true })
    api.bookings
      .confirm(booking.id)
      .then(() => {
        wx.hideLoading()
        wx.showToast({ title: '预约已确认', icon: 'success' })
        this.load(false)
      })
      .catch((error) => {
        wx.hideLoading()
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },


  goCommunity() {
    wx.navigateTo({ url: '/pages/community/index' })
  },

  goPass() {
    wx.navigateTo({ url: '/pages/pass/index' })
  },

  goEvents() {
    wx.navigateTo({ url: '/pages/events/index' })
  },

  openEntry() {
    if (!this.data.entryBooking) return
    tab.setHidden(this, true)
    this.setData({ entryVisible: true })
  },

  closeEntry() {
    tab.setHidden(this, false)
    this.setData({ entryVisible: false })
  },

  goCheckinFromEntry() {
    this.closeEntry()
    tab.switchTab('/pages/checkin/index')
  },

  onJoinChallenge() {
    if (!this.data.challenge || this.data.challenge.joinedByMe || this.data.joiningChallenge) return
    this.setData({ joiningChallenge: true })
    api.community
      .joinChallenge()
      .then(() => {
        wx.showToast({ title: '已加入挑战', icon: 'success' })
        return this.load(false)
      })
      .catch((error) => wx.showToast({ title: format.errorText(error), icon: 'none' }))
      .finally(() => this.setData({ joiningChallenge: false }))
  },

  onJoinEvent(event) {
    const eventId = event.currentTarget.dataset.id
    const item = this.data.events.find((eventItem) => eventItem.id === eventId)
    if (!item || item.mySigned || item.soldOut || this.data.joiningEventId) return
    this.setData({ joiningEventId: eventId })
    api.community
      .joinEvent(eventId)
      .then(() => {
        wx.showToast({ title: '报名成功', icon: 'success' })
        return this.load(false)
      })
      .catch((error) => wx.showToast({ title: format.errorText(error), icon: 'none' }))
      .finally(() => this.setData({ joiningEventId: '' }))
  },

  onCall() {
    wx.makePhoneCall({ phoneNumber: this.data.brand.phone || '4009006688' })
  },

  onOpenLogin() {
    tab.setHidden(this, true)
    this.setData({ loginVisible: true })
  },

  onLoginSuccess() {
    tab.setHidden(this, false)
    this.setData({ loginVisible: false })
    this.load(false)
  },

  onLoginCancel() {
    tab.setHidden(this, false)
    this.setData({ loginVisible: false })
  },

  onShareAppMessage() {
    const member = this.data.member
    const next = this.data.nextBooking
    return share.inviteShare({
      ref: member ? member.referralCode : '',
      program: next ? next.programCode : 'P01',
      from: member ? member.nickname : '',
      title: member
        ? `${member.nickname} 已连续训练 ${(this.data.stats && this.data.stats.streakWeeks) || 0} 周，来 VOLT 一起通电`
        : 'VOLT 电压健身｜按次付费，不办年卡',
      imageUrl: '/assets/images/program-boxing.webp',
    })
  },

  onShareTimeline() {
    const member = this.data.member
    return {
      title: 'VOLT 会员终端｜按次付费，不办年卡',
      query: `ref=${(member && member.referralCode) || ''}`,
      imageUrl: '/assets/images/hero.jpg',
    }
  },
})
