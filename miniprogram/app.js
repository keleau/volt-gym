const auth = require('./services/auth')

App({
  onLaunch() {
    auth.bootstrap()

    if (wx.getUpdateManager) {
      const updateManager = wx.getUpdateManager()
      updateManager.onUpdateReady(() => {
        wx.showModal({
          title: '新版本已就绪',
          content: 'VOLT 会员终端已更新，是否立即重启？',
          confirmText: '立即重启',
          confirmColor: '#A33B2B',
          success(res) {
            if (res.confirm) updateManager.applyUpdate()
          },
        })
      })
    }
  },

  globalData: {
    version: '1.1.0',
    schedulePreset: null,
    autoScan: false,
  },
})
