function select(page, index) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (tabBar) tabBar.setData({ selected: index })
}

function setHidden(page, hidden) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (tabBar) tabBar.setData({ hidden: !!hidden })
}

function switchTab(path, payload) {
  const app = getApp()
  if (payload) {
    if (payload.schedulePreset) app.globalData.schedulePreset = payload.schedulePreset
    if (payload.autoScan) app.globalData.autoScan = true
  }
  wx.switchTab({ url: path })
}

module.exports = {
  select,
  setHidden,
  switchTab,
}
