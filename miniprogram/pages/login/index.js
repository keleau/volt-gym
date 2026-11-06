const auth = require('../../services/auth')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')

Page({
  data: {
    nav: {},
    loading: false,
  },

  onLoad() {
    this._alive = true
    this.setData({ nav: navUtil.getNavMetrics() })
    if (auth.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/mine/index' })
    }
  },

  onUnload() {
    this._alive = false
  },

  onWechatLogin() {
    if (this.data.loading) return
    this.setData({ loading: true })
    auth
      .loginWithWechat(null)
      .then((session) => {
        if (!this._alive) return
        const member = session && session.user
        if (member && member.phoneBound === false) {
          wx.reLaunch({ url: '/pages/bind-phone/index?from=login' })
          return
        }
        wx.reLaunch({ url: '/pages/mine/index' })
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onPhoneLogin() {
    wx.navigateTo({ url: '/pages/bind-phone/index?mode=login' })
  },

  onContact() {
    wx.makePhoneCall({ phoneNumber: '4009006688' })
  },
})
