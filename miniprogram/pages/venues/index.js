const api = require('../../services/api')
const navUtil = require('../../utils/nav')
const format = require('../../utils/format')
const geo = require('../../services/geo')

Page({
  data: {
    nav: {},
    loading: true,
    locating: false,
    venues: [],
  },

  onLoad() {
    this.setData({ nav: navUtil.getNavMetrics() })
    this.load()
  },

  load() {
    api.catalog
      .venues()
      .then((data) => {
        this.setData({ loading: false, venues: data.venues || [] })
      })
      .catch((error) => {
        this.setData({ loading: false })
        wx.showToast({ title: format.errorText(error), icon: 'none' })
      })
  },

  onLocate() {
    if (this.data.locating) return
    this.setData({ locating: true })
    geo
      .locate()
      .then((location) => {
        if (!location.ok) {
          wx.showToast({ title: '定位未授权', icon: 'none' })
          return
        }
        const venues = this.data.venues
          .map((venue) => {
            const target = geo.venuePoint(venue.code, venue)
            if (!target) return venue
            const meters = geo.distanceMeters(location, target)
            return Object.assign({}, venue, {
              distanceMeters: meters,
              distanceText: meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(1)} km`,
            })
          })
          .sort((a, b) => (a.distanceMeters || 999999) - (b.distanceMeters || 999999))
        this.setData({ venues })
      })
      .finally(() => this.setData({ locating: false }))
  },

  onGoBook(event) {
    const venueCode = event.currentTarget.dataset.code
    getApp().globalData.schedulePreset = { venueCode }
    wx.switchTab({ url: '/pages/schedule/index' })
  },

  onNavigate(event) {
    const code = event.currentTarget.dataset.code
    const venue = this.data.venues.find((item) => item.code === code)
    geo.openVenueMap(code, venue)
  },

  onShareAppMessage() {
    return {
      title: 'VOLT 广州三店｜先选门店，再约课',
      path: '/pages/venues/index',
      imageUrl: '/assets/images/station-tianhe.webp',
    }
  },
})
