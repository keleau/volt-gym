module.exports = {
  // 开发者工具直连本地 Spring Boot；真机联调改成电脑局域网 IP（如 http://192.168.1.10:8080/api/v1）。
  // 当前电脑连接的是手机热点，WLAN 地址为 10.158.252.228；换网络后重新 ipconfig 查看并更新。
  API_BASE: 'http://10.158.252.228:8080/api/v1',
  // 演示：每次冷启动都清掉旧会话、先走微信登录页；生产演示完改回 false。
  FORCE_LOGIN: true,
  // 没有本地 session 时是否静默 wx.login；FORCE_LOGIN 打开时该项不生效。
  AUTO_LOGIN: false,
  // 发布前在公众平台申请订阅消息模板，替换 data/volt.js 中的占位 ID。
  USE_REAL_SUBSCRIBE: false,
  GEOFENCE_METERS: 2000,
}
