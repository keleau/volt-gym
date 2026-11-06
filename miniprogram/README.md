# VOLT 会员小程序

配套 VOLT 官网的微信会员端，定位是**会员私域工具**：微信登录、预约、服务通知、到店扫码核销、训练档案、消息与邀请。

当前只连接本地 `backend/`（Spring Boot + MySQL），不再提供离线 Mock 数据库。两端契约见 [`../docs/shared-backend-api-final.md`](../docs/shared-backend-api-final.md)。

## 功能

- **首页**：下一节课、进场码、次卡余次、本周挑战、近期活动。
- **预约**：先选门店 → 按教练/按时间 → 7 天场次 → 10 分钟锁位 → 确认；支持候补、分享单个场次。
- **进场**：`wx.scanCode` 扫场馆码、定位校验（失败自动降级为场馆码校验）、按次扣课、自由训练、进场码。
- **记录**：次数/时长/体重三种 Canvas 趋势、最近一次训练、身体数据；全部训练记录与排行榜、打卡日历在独立子页。
- **我的**：会员卡、预约台账、消息、微信提醒、门店与客服、手机号绑定、邀请好友、微信授权、退出登录；头像旁切换/添加会员卡。
- **会员档案**：一个微信可关联多个手机号会员档案；手机号唯一，不同档案余额与流水隔离；同一手机号的多张卡订单汇总到同一会员。
- **个人资料**：头像栏进入资料页，可改昵称/目标/身高体重，支持本地头像上传（文件存后端 `uploads/avatars`）。
- **账号**：退出后进入独立登录页（非 tab，无底部导航），可重新微信登录；也可用手机号验证码登录其他会员账号。微信小程序无法直接切换底层微信号，需在微信内切换账号后再进入。
- **社区**：本周挑战、活动报名、排行榜、训练者故事。
- **门店**：三店地址、营业时间、今日可约数、定位与导航。
- **微信能力**：`wx.login`、`wx.requestSubscribeMessage`、`wx.scanCode`、`wx.getLocation`、`chooseAvatar`、`makePhoneCall`、`onShareAppMessage / onShareTimeline`。
- **两端一致**：`C01–C06` 教练、`V-01–V-03` 门店、`P01–P06` 课程、`PLAN-01–03` 方案、场次与预约编号全部与官网同源。

## 运行

1. 启动后端：`cd ../backend && mvn spring-boot:run`（默认 `http://localhost:8080`）。
2. 微信开发者工具导入本目录（不是仓库根目录），关闭「校验合法域名」。
3. AppID 已写在 `project.config.json`；直接编译即可，无需 `npm install`。
4. 首次打开会自动 `wx.login`。本地没有配置微信 AppSecret 时，在 `backend/.env` 设置 `WX_DEMO_FALLBACK=true`，会落到种子会员「阿电」。

联调说明：

- [`config/index.js`](config/index.js) 默认 `API_BASE: 'http://127.0.0.1:8080/api/v1'`；真机联调改成电脑局域网 IP。
- 预约固定流程：先选门店，再按教练或时间；门店不可跳过，自动记住上次门店。
- 锁位 10 分钟，确认后才算预约成功；确认、取消都会写入消息中心；取消沿用「开课前 2 小时」限制。
- 到场扫码会消耗 1 次；没有待核销课程时可记录「自由训练」，不扣次。
- 开发者工具里「手动选择场馆」可走完整进场流程；真机默认打开相机扫码。

## 目录

```text
app.js / app.json             全局启动、页面与 tabBar 配置
app.wxss                      暖白主题与共享样式
custom-tab-bar/               自绘底部导航（含扫码中键）
components/                   VOLT UI：nav / sheet / calendar / login / chart
pages/                        18 个页面（含登录页、社区、门店、记录簿、日历、会员卡等子页）
services/api.js               页面调用的唯一 API 门面（catalog / member / booking / checkin / community）
services/auth.js              会话、wx.login、401 自动重登、退出标记、手机号登录会话
services/api-error.js         统一 ApiError
services/geo.js               定位 + 场馆距离校验
services/scan.js              扫码 / 演示场馆码
services/subscribe.js         订阅消息授权
utils/view.js                 课程/门店图片、故事图、记录摘要等共享展示映射
data/catalog.js               由官网同步的课程 / 门店字典（只含展示字段）
data/volt.js                  场馆坐标、图片、订阅模板 ID
scripts/sync-catalog.mjs      从 website/ 同步字典与图片
```

## 与官网共用数据

课程、门店、价格、教练来自官网 `website/src/data/*.js`，通过同步脚本生成小程序字典并复制图片：

```bash
cd miniprogram
node scripts/sync-catalog.mjs
```

会生成 `data/catalog.js` 并复制官网课程/门店/教练图片到 `assets/images/`。官网数据更新后执行一次即可，不要手工改 `data/catalog.js`。

## 已知限制

- 后端 `/auth/wx/login` 在未配置 AppSecret 时映射到演示会员；生产上线需接 `jscode2session` 与 openid 绑定。
- 未接入真实微信支付；购卡登记与本地「演示支付并开卡」可用，正式收款仍需微信支付商户号。
- 订阅消息模板 ID 为占位值；`config.USE_REAL_SUBSCRIBE` 默认关闭，避免演示时弹授权失败。
- 手机号绑定/登录使用短信 OTP：本地 `VOLT_EXPOSE_OTP=true` 显示 `devCode`，生产需接真实短信通道或微信手机号快速验证。
- 官网 49 元体验课可以在小程序核销：体验课不扣次卡，同一手机号只能预约一次首次体验。
- 首页对未绑定手机号的会员弹出验证弹窗，验证后按 `audience` 识别已开卡 / 体验课 / 路人；路人可从引导弹窗进入课表体验模式预约 ¥49 体验课，或登记购卡。
- 退出登录清除本地会话并阻止下次启动自动微信登录；后端 token 未做主动吊销，正式上线应补 `POST /auth/logout`。
- 小程序主包约 1.5MB，已移除 ECharts/ec-canvas，改用原生 Canvas 2D。
