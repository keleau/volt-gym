const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')
const view = require('../../utils/view')

Page({
  data: {
    nav: {},
    loading: true,
    community: null,
    events: [],
    ranking: [],
    rankingPreview: [],
    stories: [],
    stats: [],
    challenge: null,
    challengePercent: 0,
    joiningEventId: '',
    joiningChallenge: false,
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  onShow() {
    if (this.loadedOnce) this.load(false)
  },

  load(withLoading) {
    if (withLoading !== false) this.setData({ loading: true })
    return api.community
      .get()
      .then((community) => {
        this.loadedOnce = true
        const stories = (community.stories || []).map((story) =>
          Object.assign({}, story, { imageUrl: view.storyImage(story.img) }),
        )
        const events = (community.events || []).map((event) =>
          Object.assign({}, event, {
            monthShort: view.eventMonthShort(event.month),
          }),
        )
        const challenge = community.challenge || null
        const challengePercent = challenge
          ? Math.min(
              100,
              Math.round(
                (Number(challenge.myProgress || 0) / Math.max(1, Number(challenge.sessions || 1))) * 100,
              ),
            )
          : 0
        this.setData({
          loading: false,
          community,
          events,
          ranking: community.ranking || [],
          rankingPreview: (community.ranking || []).slice(0, 3),
          stories,
          stats: community.stats || [],
          challenge,
          challengePercent,
        })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onJoinChallenge() {
    if (this.data.joiningChallenge || !this.data.challenge) return
    if (this.data.challenge.joinedByMe) return
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

  goEvents() {
    wx.navigateTo({ url: '/pages/events/index' })
  },

  goStories() {
    wx.navigateTo({ url: '/pages/stories/index' })
  },

  goRanking() {
    wx.navigateTo({ url: '/pages/logs/index?tab=ranking' })
  },

  onGoSchedule(event) {
    const venueCode = event.currentTarget.dataset.venue || ''
    const app = getApp()
    app.globalData.schedulePreset = venueCode ? { venueCode } : null
    wx.switchTab({ url: '/pages/schedule/index' })
  },

  onShareAppMessage() {
    return {
      title: 'VOLT 社区｜本周挑战与活动',
      path: '/pages/community/index',
      imageUrl: '/assets/images/hero.jpg',
    }
  },

  onShareTimeline() {
    return {
      title: 'VOLT 社区｜本周挑战与活动',
      imageUrl: '/assets/images/hero.jpg',
    }
  },
})
