const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')
const view = require('../../utils/view')

Page({
  data: {
    nav: {},
    loading: true,
    leadStory: null,
    otherStories: [],
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  load() {
    return api.community
      .get()
      .then((community) => {
        const stories = (community.stories || []).map((story) =>
          Object.assign({}, story, { imageUrl: view.storyImage(story.img) }),
        )
        this.setData({
          loading: false,
          leadStory: stories[0] || null,
          otherStories: stories.slice(1),
        })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },
})
