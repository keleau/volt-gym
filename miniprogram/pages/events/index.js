const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')
const view = require('../../utils/view')

Page({
  data: {
    nav: {},
    loading: true,
    events: [],
    joiningEventId: '',
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  load() {
    return api.community
      .get()
      .then((community) => {
        const events = (community.events || []).map((event) =>
          Object.assign({}, event, {
            monthShort: view.eventMonthShort(event.month),
          }),
        )
        this.setData({ loading: false, events })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
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
        return this.load()
      })
      .catch((error) => wx.showToast({ title: format.errorText(error), icon: 'none' }))
      .finally(() => this.setData({ joiningEventId: '' }))
  },
})
