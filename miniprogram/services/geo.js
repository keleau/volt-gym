const config = require('../config/index')
const volt = require('../data/volt')

function toRad(value) {
  return (value * Math.PI) / 180
}

function distanceMeters(a, b) {
  const earth = 6371000
  const dLat = toRad(b.latitude - a.latitude)
  const dLon = toRad(b.longitude - a.longitude)
  const lat1 = toRad(a.latitude)
  const lat2 = toRad(b.latitude)
  const h =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
  return Math.round(2 * earth * Math.asin(Math.sqrt(h)))
}

function locate() {
  return new Promise((resolve) => {
    let settled = false
    const finish = (result) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(result)
    }
    const timer = setTimeout(() => finish({ ok: false, reason: 'timeout' }), 2600)
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      success(res) {
        finish({ ok: true, latitude: res.latitude, longitude: res.longitude })
      },
      fail() {
        finish({ ok: false, reason: 'denied' })
      },
    })
  })
}

function verifyVenue(venueCode) {
  const target = volt.venueGeo[venueCode]
  if (!target) {
    return Promise.resolve({ status: 'unknown', label: '场馆码已校验' })
  }
  return locate().then((location) => {
    if (!location.ok) {
      return { status: 'denied', label: '未授权定位 · 已采用场馆码校验' }
    }
    const meters = distanceMeters(location, target)
    const inside = meters <= config.GEOFENCE_METERS
    return {
      status: inside ? 'inside' : 'outside',
      distanceMeters: meters,
      latitude: location.latitude,
      longitude: location.longitude,
      label: inside
        ? `定位已校验 · 距场馆 ${Math.max(10, meters)} m`
        : '定位超出范围 · 已采用场馆码校验',
    }
  })
}

function openVenueMap(venueCode) {
  const geo = volt.venueGeo[venueCode]
  if (!geo) return
  wx.openLocation({
    latitude: geo.latitude,
    longitude: geo.longitude,
    name: `VOLT ${geo.name}`,
    address: 'VOLT 电压健身',
    scale: 17,
  })
}

module.exports = {
  distanceMeters,
  locate,
  verifyVenue,
  openVenueMap,
}
