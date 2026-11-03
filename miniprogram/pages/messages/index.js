const api = require('../../services/api')
const dateUtil = require('../../utils/date')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')

const TYPE_META = {
  reminder: { label: '开课提醒', code: 'REM' },
  change: { label: '课程变更', code: 'CHG' },
  waitlist: { label: '候补递补', code: 'QUE' },
  checkin: { label: '签到记录', code: 'SCAN' },
  system: { label: '系统通知', code: 'SYS' },
}

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'reminder', label: '提醒' },
  { key: 'change', label: '变更' },
  { key: 'waitlist', label: '候补' },
  { key: 'system', label: '系统' },
]

Page({
  data: {
    nav: {},
    loading: true,
    scrollTop: 0,
    messages: [],
    filtered: [],
    unread: 0,
    filter: 'all',
    filters: FILTERS,
    subscriptions: {},
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  onShow() {
    if (this.loadedOnce) this.load(false)
  },

  load(withLoading) {
    const showLoading = withLoading !== false
    if (showLoading) this.setData({ loading: true })
    return api.messages
      .list()
      .then((data) => {
        const messages = (data.messages || []).map((item) => {
          const meta = TYPE_META[item.type] || TYPE_META.system
          return Object.assign({}, item, {
            typeLabel: meta.label,
            typeCode: meta.code,
            stamp: dateUtil.stamp(item.createdAt),
          })
        })
        this.loadedOnce = true
        this.setData(
          {
            loading: false,
            messages,
            unread: data.unread || 0,
            subscriptions: data.subscriptions || {},
          },
          () => this.applyFilter(),
        )
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  applyFilter() {
    const filter = this.data.filter
    const filtered =
      filter === 'all'
        ? this.data.messages
        : this.data.messages.filter((item) => item.type === filter)
    this.setData({ filtered })
  },

  onFilter(event) {
    const key = event.currentTarget.dataset.key
    this.setData({ filter: key }, () => this.applyFilter())
  },

  onMessage(event) {
    const id = event.currentTarget.dataset.id
    const message = this.data.messages.find((item) => item.id === id)
    if (!message) return
    const navigate = () => {
      if (!message.action) return
      const [path, query = ''] = message.action.split('?')
      if (path === '/pages/schedule/index') {
        const params = {}
        query.split('&').forEach((pair) => {
          const [key, value] = pair.split('=')
          if (key) params[decodeURIComponent(key)] = decodeURIComponent(value || '')
        })
        if (Object.keys(params).length) {
          getApp().globalData.schedulePreset = params
        }
        wx.switchTab({ url: path })
        return
      }
      if (
        path === '/pages/index/index' ||
        path === '/pages/checkin/index' ||
        path === '/pages/archive/index' ||
        path === '/pages/mine/index'
      ) {
        wx.switchTab({ url: path })
      } else {
        wx.navigateTo({ url: message.action })
      }
    }
    if (message.read) {
      navigate()
      return
    }
    api.messages
      .markRead(id)
      .then((res) => {
        const messages = this.data.messages.map((item) =>
          item.id === id ? Object.assign({}, item, { read: true }) : item,
        )
        this.setData({ messages, unread: res.unread }, () => this.applyFilter())
        navigate()
      })
      .catch(() => wx.showToast({ title: '标记失败，请重试', icon: 'none' }))
  },

  onMarkAll() {
    if (!this.data.unread) return
    api.messages
      .markRead('all')
      .then((res) => {
        const messages = this.data.messages.map((item) =>
          Object.assign({}, item, { read: true }),
        )
        this.setData({ messages, unread: res.unread }, () => this.applyFilter())
        wx.showToast({ title: '已全部已读', icon: 'none' })
      })
      .catch((error) => wx.showToast({ title: format.errorText(error), icon: 'none' }))
  },

  onManagePush() {
    wx.switchTab({ url: '/pages/mine/index' })
  },

  onGoBookings() {
    wx.navigateTo({ url: '/pages/bookings/index?tab=waitlist' })
  },

  onGoSchedule() {
    wx.switchTab({ url: '/pages/schedule/index' })
  },

  onShareAppMessage() {
    return {
      title: 'VOLT 会员终端｜课程提醒与签到记录',
      path: '/pages/index/index',
      imageUrl: '/assets/images/program-boxing.webp',
    }
  },
})
