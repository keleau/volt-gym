const navUtil = require('../../utils/nav')

Component({
  properties: {
    index: { type: String, value: '' },
    title: { type: String, value: '' },
    en: { type: String, value: '' },
    back: { type: Boolean, value: false },
    logo: { type: Boolean, value: false },
    theme: { type: String, value: 'ink' },
    placeholder: { type: Boolean, value: true },
  },

  data: {
    nav: {
      statusBarHeight: 20,
      navBarHeight: 44,
      totalHeight: 64,
      navRight: 104,
    },
  },

  lifetimes: {
    attached() {
      this.setData({ nav: navUtil.getNavMetrics() })
    },
  },

  methods: {
    onBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        wx.navigateBack({ delta: 1 })
      } else {
        wx.switchTab({ url: '/pages/index/index' })
      }
    },
  },
})
