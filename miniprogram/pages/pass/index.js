const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')

Page({
  data: {
    nav: {},
    loading: true,
    member: {},
    plans: [],
    brand: {},
    passCells: [],
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  load() {
    return api.mine
      .get()
      .then((data) => {
        const member = data.member || {}
        const total = Math.max(0, Number(member.creditsTotal) || 0)
        const used = Math.max(0, total - (Number(member.credits) || 0))
        const passCells = []
        for (let index = 1; index <= total; index += 1) {
          passCells.push({
            no: index,
            used: index <= used,
            label: index < 10 ? `0${index}` : String(index),
          })
        }
        this.setData({
          loading: false,
          member,
          plans: data.plans || [],
          brand: data.brand || {},
          passCells,
        })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onContact() {
    wx.makePhoneCall({ phoneNumber: this.data.brand.phone || '4009006688' })
  },
})
