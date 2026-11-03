const config = require('../config/index')
const volt = require('../data/volt')

function templateIds(keys) {
  return keys
    .map((key) => volt.subscriptionTemplates[key])
    .filter((id) => id && id.indexOf('REPLACE_WITH_') !== 0)
}

function requestSubscribe(keys) {
  return new Promise((resolve) => {
    const tmplIds = templateIds(keys)
    if (!config.USE_REAL_SUBSCRIBE || !tmplIds.length) {
      resolve({
        mock: true,
        accepted: true,
        message: '演示环境已记录订阅偏好',
      })
      return
    }
    wx.requestSubscribeMessage({
      tmplIds,
      success(res) {
        const accepted = tmplIds.every((id) => res[id] === 'accept')
        resolve({ mock: false, accepted, result: res })
      },
      fail() {
        resolve({ mock: true, accepted: false, message: '未订阅，可在「我的」重新开启提醒' })
      },
    })
  })
}

module.exports = {
  templateIds,
  requestSubscribe,
}
