module.exports = {
  // 开发者工具直连本地 Spring Boot；真机联调改成电脑局域网 IP（如 http://192.168.1.10:8080/api/v1）。
  API_BASE: 'http://127.0.0.1:8080/api/v1',
  // 发布前在公众平台申请订阅消息模板，替换 data/volt.js 中的占位 ID。
  USE_REAL_SUBSCRIBE: false,
  GEOFENCE_METERS: 2000,
}
