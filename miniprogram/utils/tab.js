let selectToken = 0

function select(page, index) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (!tabBar) return
  // switchTab 过程中自定义 tabBar 属于复用组件，延迟到本次渲染完成后再改高亮；
  // token 防止快速切 Tab 时旧页面的延迟任务覆盖新页面。
  const token = ++selectToken
  setTimeout(() => {
    if (token !== selectToken) return
    try {
      if (tabBar.data && tabBar.data.selected !== index) {
        tabBar.setData({ selected: index })
      }
    } catch (error) {
      // tabBar 已销毁时忽略
    }
  }, 40)
}

function setHidden(page, hidden) {
  if (!page || typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (!tabBar) return
  const next = !!hidden
  if (tabBar.data && tabBar.data.hidden === next) return
  tabBar.setData({ hidden: next })
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
