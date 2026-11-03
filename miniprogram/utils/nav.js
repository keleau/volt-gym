let cached = null

function getNavMetrics() {
  if (cached) return cached
  let info
  try {
    info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
  } catch (error) {
    info = { windowWidth: 375, statusBarHeight: 20 }
  }
  let menu = null
  try {
    menu = wx.getMenuButtonBoundingClientRect()
  } catch (error) {
    menu = null
  }
  const statusBarHeight = info.statusBarHeight || 20
  const navBarHeight = menu && menu.height && menu.top
    ? (menu.top - statusBarHeight) * 2 + menu.height
    : 44
  const windowWidth = info.windowWidth || 375
  const capsuleGap = menu && menu.left ? windowWidth - menu.left : 96
  cached = {
    statusBarHeight,
    navBarHeight,
    totalHeight: statusBarHeight + navBarHeight,
    windowWidth,
    capsuleGap,
    navRight: capsuleGap + 8,
  }
  return cached
}

module.exports = {
  getNavMetrics,
}
