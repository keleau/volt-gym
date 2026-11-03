const catalog = require('../data/catalog')

function parseVenueCode(payload) {
  const raw = String(payload || '')
  const direct = raw.match(/V-\d{2}/i)
  if (!direct) return ''
  const code = direct[0].toUpperCase()
  return catalog.venues.some((venue) => venue.code === code) ? code : ''
}

function scanCode() {
  return new Promise((resolve, reject) => {
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success(res) {
        const code = parseVenueCode(res.result)
        if (!code) {
          reject(new Error('NOT_VOLT_QR'))
          return
        }
        resolve({ code, raw: res.result })
      },
      fail(err) {
        const message = String((err && err.errMsg) || '')
        reject(new Error(message.indexOf('auth deny') >= 0 ? 'CAMERA_DENIED' : 'SCAN_CANCEL'))
      },
    })
  })
}

function pickDemoVenue(todayVenueCode) {
  return new Promise((resolve, reject) => {
    const items = catalog.venues.map((venue) => {
      const suffix = venue.code === todayVenueCode ? ' · 今日有课' : ''
      return `${venue.code} ${venue.name}${suffix}`
    })
    wx.showActionSheet({
      itemList: items,
      success(res) {
        resolve(catalog.venues[res.tapIndex].code)
      },
      fail() {
        reject(new Error('PICK_CANCEL'))
      },
    })
  })
}

module.exports = {
  parseVenueCode,
  scanCode,
  pickDemoVenue,
}
