const api = require('../../services/api')
const auth = require('../../services/auth')
const dateUtil = require('../../utils/date')
const navUtil = require('../../utils/nav')
const tab = require('../../utils/tab')
const format = require('../../utils/format')
const share = require('../../utils/share')
const subscribe = require('../../services/subscribe')

Page({
  data: {
    nav: {},
    loading: true,
    refreshing: false,
    scrollTop: 0,
    member: {},
    memberInitial: '电',
    stats: {},
    brand: {},
    plans: [],
    venues: [],
    subscriptions: {},
    messagesUnread: 0,
    invites: { records: [] },
    counts: {},
    nextBooking: null,
    nextDayText: '',
    expiryDays: 0,
    planVisible: false,
    reminderVisible: false,
    phoneVisible: false,
    phoneInput: '',
    bindingPhone: false,
    loginVisible: false,
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  onShow() {
    tab.select(this, 4)
    tab.setHidden(this, false)
    if (this.loadedOnce) this.load(false)
  },

  onRefresh() {
    this.setData({ refreshing: true })
    this.load(false).then(() => {
      setTimeout(() => this.setData({ refreshing: false }), 260)
    })
  },

  load(withLoading) {
    const showLoading = withLoading !== false
    if (showLoading) this.setData({ loading: true })
    return api.mine
      .get()
      .then((data) => {
        const next = data.nextBooking
        this.loadedOnce = true
        this.setData({
          loading: false,
          member: data.member,
          memberInitial: data.member.avatarText || format.initials(data.member.nickname),
          stats: data.stats,
          brand: data.brand,
          plans: data.plans,
          venues: data.venues || [],
          subscriptions: data.subscriptions,
          messagesUnread: data.messagesUnread,
          invites: data.invites,
          counts: data.counts,
          nextBooking: next,
          nextDayText: next
            ? `${dateUtil.relativeDay(next.date)} ${next.time} · ${next.venueName}`
            : '',
          expiryDays: format.daysLeft(data.member.expiresAt),
        })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  openPlans() {
    tab.setHidden(this, true)
    this.setData({ planVisible: true })
  },

  closePlans() {
    tab.setHidden(this, false)
    this.setData({ planVisible: false })
  },

  openReminders() {
    tab.setHidden(this, true)
    this.setData({ reminderVisible: true })
  },

  closeReminders() {
    tab.setHidden(this, false)
    this.setData({ reminderVisible: false })
  },

  onContact() {
    wx.makePhoneCall({ phoneNumber: this.data.brand.phone || '4009006688' })
  },

  onAbout() {
    wx.showModal({
      title: 'VOLT 电压健身',
      content: '按次付费，不办年卡。广州天河 / 海珠 / 番禺三店直营，专业教练带练，没有推销。',
      showCancel: false,
      confirmText: '知道了',
      confirmColor: '#A33B2B',
    })
  },

  onGoBookings(event) {
    const tabName = event.currentTarget.dataset.tab || 'upcoming'
    wx.navigateTo({ url: `/pages/bookings/index?tab=${tabName}` })
  },

  onGoMessages() {
    wx.navigateTo({ url: '/pages/messages/index' })
  },

  onGoCheckin() {
    tab.switchTab('/pages/checkin/index')
  },

  goPass() {
    wx.navigateTo({ url: '/pages/pass/index' })
  },

  onGoArchive() {
    tab.switchTab('/pages/archive/index')
  },

  onGoSchedule() {
    tab.switchTab('/pages/schedule/index')
  },

  onGoVenues() {
    wx.navigateTo({ url: '/pages/venues/index' })
  },

  openPhoneBind() {
    tab.setHidden(this, true)
    this.setData({
      phoneVisible: true,
      phoneInput: '',
    })
  },

  closePhoneBind() {
    tab.setHidden(this, false)
    this.setData({ phoneVisible: false })
  },

  onPhoneInput(event) {
    this.setData({ phoneInput: event.detail.value })
  },

  bindPhone() {
    if (this.data.bindingPhone) return
    const phone = String(this.data.phoneInput || '').trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '请填写正确的 11 位手机号', icon: 'none' })
      return
    }
    this.setData({ bindingPhone: true })
    api.auth
      .linkPhone({ phone })
      .then((member) => {
        auth.refreshUser(member)
        tab.setHidden(this, false)
        this.setData({ bindingPhone: false, phoneVisible: false, member })
        wx.showToast({ title: '手机号已绑定', icon: 'success' })
        this.load(false)
      })
      .catch((error) => {
        this.setData({ bindingPhone: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onToggleSub(event) {
    const key = event.currentTarget.dataset.key
    const value = event.detail.value
    const keysBySub = {
      classReminder: ['classReminder', 'classChange'],
      classChange: ['classChange'],
      waitlist: ['waitlist', 'classChange'],
    }
    const apply = () => {
      api.messages
        .setSubscription(key, value)
        .then((subscriptions) => {
          this.setData({ subscriptions })
          wx.showToast({
            title: value ? '提醒已开启' : '提醒已关闭',
            icon: 'none',
          })
        })
        .catch((error) => {
          this.setData({ [`subscriptions.${key}`]: !value })
          wx.showToast({ title: format.errorText(error), icon: 'none' })
        })
    }
    if (!value) {
      apply()
      return
    }
    subscribe.requestSubscribe(keysBySub[key] || [key]).then(apply)
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
    return share.inviteShare({
      program: 'P01',
      ref: member.referralCode,
      from: member.nickname,
      title: `${member.nickname} 邀请你来 VOLT 通电，首次体验 ¥49`,
      imageUrl: '/assets/images/program-boxing.webp',
    })
  },

  onShareTimeline() {
    return {
      title: 'VOLT 电压健身｜按次付费，不办年卡',
      query: `ref=${(this.data.member && this.data.member.referralCode) || ''}`,
      imageUrl: '/assets/images/hero.jpg',
    }
  },
})
