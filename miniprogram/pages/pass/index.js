const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')

const LEDGER_LABELS = {
  CHECKIN: '核销扣次',
  REVOKE: '撤销返还',
  CARD: '购卡开卡',
}

function ledgerDate(value) {
  const text = String(value || '')
  return text ? text.replace('T', ' ').slice(5, 16) : ''
}

function mapLedger(ledger) {
  return ((ledger && ledger.entries) || []).slice(0, 8).map((entry) => ({
    id: entry.id,
    label: LEDGER_LABELS[entry.reason] || '次卡变动',
    date: ledgerDate(entry.createdAt),
    change: entry.changeAmount > 0 ? `+${entry.changeAmount}` : String(entry.changeAmount),
    positive: entry.changeAmount > 0,
    balance: entry.balanceAfter,
    note: entry.note || '',
  }))
}

Page({
  data: {
    nav: {},
    loading: true,
    member: {},
    plans: [],
    venues: [],
    brand: {},
    passCells: [],
    pendingCard: null,
    demoPayEnabled: false,
    paying: false,
    ledgerEntries: [],
    buySheetVisible: false,
    buyPlan: null,
    buyVenueCode: '',
    buyVenueName: '',
    buying: false,
  },

  onLoad(options) {
    this._alive = true
    this.autoBuy = Boolean(options && options.buy === '1')
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  onShow() {
    if (this.loadedOnce) this.load()
  },

  onUnload() {
    this._alive = false
  },

  load() {
    return Promise.all([
      api.mine.get(),
      api.credits.ledger().catch(() => null),
    ])
      .then(([data, ledger]) => {
        if (!this._alive) return
        this.loadedOnce = true
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
        const venues = data.venues || []
        const pendingCard = (data.cardOrders || []).find((order) => order.status === 'pending') || null
        const plans = (data.plans || []).filter((plan) => plan.code !== 'PLAN-01')
        const currentPlan = this.data.buyPlan && plans.find((plan) => plan.code === this.data.buyPlan.code)
        const buyPlan = currentPlan || plans.find((plan) => plan.code === 'PLAN-03') || plans[0] || null
        const currentVenue = venues.find((venue) => venue.code === this.data.buyVenueCode)
        const buyVenue = currentVenue || venues[0] || null
        this.setData(
          {
            loading: false,
            member,
            plans,
            venues,
            brand: data.brand || {},
            passCells,
            pendingCard,
            demoPayEnabled: Boolean(data.demoPayEnabled),
            ledgerEntries: mapLedger(ledger),
            buyPlan,
            buyVenueCode: buyVenue ? buyVenue.code : '',
            buyVenueName: buyVenue ? buyVenue.name : '',
          },
          () => {
            if (!this.autoBuy) return
            this.autoBuy = false
            if (!pendingCard) this.setData({ buySheetVisible: true })
          },
        )
      })
      .catch((error) => {
        if (!this._alive) return
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onContact() {
    wx.makePhoneCall({ phoneNumber: this.data.brand.phone || '4009006688' })
  },

  onBuyPlan(event) {
    const code = event.currentTarget.dataset.code
    const plan = this.data.plans.find((item) => item.code === code)
    if (!plan) return
    this.setData({ buyPlan: plan, buySheetVisible: true })
  },

  closeBuySheet() {
    this.setData({ buySheetVisible: false })
  },

  onVenueChange(event) {
    const venue = this.data.venues[Number(event.detail.value)]
    if (!venue) return
    this.setData({ buyVenueCode: venue.code, buyVenueName: venue.name })
  },

  goBindPhone() {
    wx.navigateTo({ url: '/pages/bind-phone/index' })
  },

  submitBuy() {
    if (this.data.buying) return
    const plan = this.data.buyPlan
    if (!plan) {
      wx.showToast({ title: '请选择次卡方案', icon: 'none' })
      return
    }
    if (!this.data.member.phoneBound) {
      wx.showModal({
        title: '先绑定手机号',
        content: '购卡需要手机号归属，绑定后回到会员卡页即可登记。',
        confirmText: '去绑定',
        success: (res) => {
          if (res.confirm) this.goBindPhone()
        },
      })
      return
    }
    if (!this.data.buyVenueCode) {
      wx.showToast({ title: '请选择开卡门店', icon: 'none' })
      return
    }
    this.setData({ buying: true })
    api.card
      .create({ planCode: plan.code, venueCode: this.data.buyVenueCode })
      .then(() => {
        if (!this._alive) return
        wx.showToast({ title: '购卡已登记', icon: 'success' })
        this.setData({ buySheetVisible: false })
        return this.load()
      })
      .catch((error) => {
        if (!this._alive) return
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
      .then(() => {
        if (this._alive) this.setData({ buying: false })
      })
  },

  onDemoPay() {
    const order = this.data.pendingCard
    if (!order || this.data.paying) return
    wx.showModal({
      title: '演示支付',
      content: '这是演示支付，不会真实扣款。确认模拟支付并开卡？',
      confirmText: '模拟支付',
      success: (res) => {
        if (!res.confirm) return
        this.setData({ paying: true })
        api.card
          .demoPay(order.orderNo)
          .then(() => {
            if (!this._alive) return
            wx.showToast({ title: '演示支付成功', icon: 'success' })
            return this.load()
          })
          .catch((error) => {
            if (!this._alive) return
            wx.showToast({ title: format.errorText(error), icon: 'none' })
          })
          .then(() => {
            if (this._alive) this.setData({ paying: false })
          })
      },
    })
  },
})
