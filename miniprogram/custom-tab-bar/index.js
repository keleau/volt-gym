Component({
  data: {
    selected: 0,
    hidden: false,
    list: [
      { pagePath: '/pages/index/index', text: '首页', icon: 'home' },
      { pagePath: '/pages/schedule/index', text: '预约', icon: 'schedule' },
      { pagePath: '/pages/checkin/index', text: '进场', icon: 'scan', center: true },
      { pagePath: '/pages/archive/index', text: '记录', icon: 'archive' },
      { pagePath: '/pages/mine/index', text: '我的', icon: 'mine' },
    ],
  },

  methods: {
    onTap(event) {
      const index = Number(event.currentTarget.dataset.index)
      const item = this.data.list[index]
      if (!item) return
      this.setData({ selected: index })
      wx.switchTab({ url: item.pagePath })
    },
  },
})
