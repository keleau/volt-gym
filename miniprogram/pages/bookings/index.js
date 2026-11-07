const api = require('../../services/api')
const dateUtil = require('../../utils/date')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')
const share = require('../../utils/share')
const tab = require('../../utils/tab')

const TAB_LABELS = {
  upcoming: '待上课',
  waitlist: '候补',
  history: '历史',
}

Page({
  data: {
    nav: {},
    loading: true,
    tab: 'upcoming',
    tabs: [],
    bookings: { upcoming: [], waitlists: [], history: [] },
    currentList: [],
    counts: {},
    member: {},
    passVisible: false,
    passBooking: null,
    scrollTop: 0,
  },

  onLoad(options) {
    const tabName = options && TAB_LABELS[options.tab] ? options.tab : 'upcoming'
    this.setData({ nav: navUtil.getNavMetrics(), tab: tabName })
    this.load()
  },

  onShow() {
    if (this.loadedOnce) this.load(false)
  },

  onHide() {
    this.stopTicker()
  },

  onUnload() {
    this.stopTicker()
  },

  load(withLoading) {
    const showLoading = withLoading !== false
    if (showLoading) this.setData({ loading: true })
    return api.bookings
      .list()
      .then((data) => {
        const upcoming = (data.upcoming || []).map((item) => this.decorate(item, 'upcoming'))
        const waitlists = (data.waitlists || []).map((item) => this.decorate(item, 'waitlist'))
        const history = (data.history || []).map((item) => this.decorate(item, 'history'))
        const tabs = [
          { key: 'upcoming', label: '待上课', count: upcoming.length },
          { key: 'waitlist', label: '候补', count: waitlists.length },
          { key: 'history', label: '历史', count: history.length },
        ]
        this.loadedOnce = true
        this.setData(
          {
            loading: false,
            bookings: { upcoming, waitlists, history },
            tabs,
            counts: data.counts || {},
          },
          () => {
            this.applyTab()
            this.startTicker()
          },
        )
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  decorate(item, group) {
    let statusLabel = ''
    let statusTone = 'paper'
    if (group === 'waitlist') {
      statusLabel = `候补 ${item.position}`
      statusTone = 'wait'
    } else if (item.status === 'locked') {
      statusLabel = '锁位中'
      statusTone = 'lock'
    } else if (item.status === 'confirmed') {
      statusLabel = '已确认'
      statusTone = 'confirmed'
    } else if (item.status === 'checked-in') {
      statusLabel = item.isTrial ? '已核销' : '已签到'
      statusTone = 'done'
    } else if (item.status === 'cancelled') {
      statusLabel = '已取消'
      statusTone = 'muted'
    } else if (item.status === 'expired') {
      statusLabel = '已释放'
      statusTone = 'muted'
    }
    return Object.assign({}, item, {
      statusLabel,
      statusTone,
      dateText: `${dateUtil.relativeDay(item.date)} ${dateUtil.weekdayCn(item.date)}`,
      shortDate: dateUtil.shortDate(item.date),
      isToday: item.date === dateUtil.todayISO(),
      lockText: item.lockText || '',
      lockExpiresAt: item.lockExpiresAt || 0,
      group,
    })
  },

  applyTab() {
    const key = this.data.tab
    let list = []
    if (key === 'upcoming') list = this.data.bookings.upcoming
    else if (key === 'waitlist') list = this.data.bookings.waitlists
    else list = this.data.bookings.history
    this.setData({ currentList: list })
  },

  onSelectTab(event) {
    const key = event.currentTarget.dataset.key
    if (!key || key === this.data.tab) return
    this.setData({ tab: key }, () => this.applyTab())
  },

  startTicker() {
    this.stopTicker()
    const locked = this.data.bookings.upcoming.filter((item) => item.lockExpiresAt)
    if (!locked.length) return
    this.lockTimer = setInterval(() => {
      let expired = false
      const upcoming = this.data.bookings.upcoming.map((item) => {
        if (!item.lockExpiresAt) return item
        const remain = item.lockExpiresAt - Date.now()
        if (remain <= 0) {
          expired = true
          return Object.assign({}, item, { lockText: '00:00', lockRemaining: 0 })
        }
        return Object.assign({}, item, {
          lockText: dateUtil.formatLock(remain),
          lockRemaining: remain,
        })
      })
      this.setData({ 'bookings.upcoming': upcoming }, () => {
        if (this.data.tab === 'upcoming') this.applyTab()
      })
      if (expired) {
        this.stopTicker()
        this.load(false)
      }
    }, 1000)
  },

  stopTicker() {
    if (this.lockTimer) {
      clearInterval(this.lockTimer)
      this.lockTimer = null
    }
  },

  onConfirm(event) {
    const id = event.currentTarget.dataset.id
    if (!id) return
    wx.showLoading({ title: '确认中', mask: true })
    api.bookings
      .confirm(id)
      .then(() => {
        wx.hideLoading()
        wx.showToast({ title: '预约已确认', icon: 'success' })
        this.load(false)
      })
      .catch((error) => {
        wx.hideLoading()
        wx.showToast({ title: format.errorText(error), icon: 'none' })
        this.load(false)
      })
  },

  onCancelBooking(event) {
    const id = event.currentTarget.dataset.id
    if (!id) return
    wx.showModal({
      title: '取消这节预约？',
      content: '开课前取消不扣充电次数，名额会立即释放。',
      confirmText: '取消预约',
      cancelText: '保留',
      confirmColor: '#A33B2B',
      success: (res) => {
        if (!res.confirm) return
        api.bookings
          .cancel(id)
          .then(() => {
            wx.showToast({ title: '预约已取消', icon: 'none' })
            this.load(false)
          })
          .catch((error) => wx.showToast({ title: format.errorText(error), icon: 'none' }))
      },
    })
  },

  onCancelWaitlist(event) {
    const id = event.currentTarget.dataset.id
    if (!id) return
    wx.showModal({
      title: '退出候补？',
      content: '退出后不再为你保留顺位。',
      confirmText: '退出候补',
      cancelText: '保留',
      confirmColor: '#A33B2B',
      success: (res) => {
        if (!res.confirm) return
        api.waitlist
          .cancel(id)
          .then(() => {
            wx.showToast({ title: '已退出候补', icon: 'none' })
            this.load(false)
          })
          .catch((error) => wx.showToast({ title: format.errorText(error), icon: 'none' }))
      },
    })
  },

  onOpenPass(event) {
    const id = event.currentTarget.dataset.id
    const booking = this.data.currentList.find((item) => item.id === id)
    if (!booking) return
    this.setData({ passBooking: booking, passVisible: true })
  },

  closePass() {
    this.setData({ passVisible: false })
  },

  goCheckinFromPass() {
    this.setData({ passVisible: false })
    tab.switchTab('/pages/checkin/index')
  },

  onGoCheckin() {
    this.setData({ passVisible: false })
    tab.switchTab('/pages/checkin/index')
  },

  onGoSchedule() {
    tab.switchTab('/pages/schedule/index')
  },

  onBookAgain(event) {
    const id = event.currentTarget.dataset.id
    const item = this.data.currentList.find((entry) => entry.id === id)
    if (!item) return
    getApp().globalData.schedulePreset = {
      venueCode: item.venueCode,
      coachId: item.coachId,
      programNo: item.programNo,
    }
    tab.switchTab('/pages/schedule/index')
  },

  onGoMessages() {
    wx.navigateTo({ url: '/pages/messages/index' })
  },

  onShareAppMessage() {
    return share.inviteShare({
      program: 'P01',
      title: 'VOLT 会员终端｜按次付费，不办年卡',
    })
  },
})
