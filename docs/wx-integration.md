# VOLT 微信小程序后端接入文档

> 2026-11 更新：小程序已移除 `mock-db.js` 和 `USE_MOCK`，只连接真实后端。本文中 Mock 相关段落仅作历史参考。
>
> 适用代码：`miniprogram/`（原生微信小程序，15 个页面，自定义 tabBar）。
> 契约来源：[shared-backend-api-final.md](shared-backend-api-final.md)（v2-final）。本文只展开小程序侧的切换步骤、页面调用、聚合响应和状态机；领域模型、数据库与并发设计以最终契约为准。
> 版本：2026-11-02（当前已接入本地 `backend`）。
> 官网侧见 [web-integration.md](web-integration.md)。

## 1. 接入现状

| 项目 | 当前实现 |
| --- | --- |
| 运行形态 | 原生小程序，导入 `miniprogram/` 目录 |
| 页面 | 首页 / 预约 / 进场 / 记录 / 我的 + 预约台账 / 消息 / 邀请 / 社区 / 门店 / 记录簿 / 日历 / 会员卡 / 活动 / 故事 |
| API 门面 | `miniprogram/services/api.js`，只走真实请求 |
| 登录 | 首次启动自动 `wx.login`；请求层等待会话建立后带 token 请求；401 自动重登一次 |
| 配置 | `miniprogram/config/index.js` |
| 后端地址 | `http://127.0.0.1:8080/api/v1`（本地 Spring Boot） |
| 锁位 | 10 分钟（后端 `VoltContract.LOCK_MINUTES`） |
| 签到围栏 | 2000 m（`GEOFENCE_METERS`） |
| 订阅消息 | 模板 ID 为占位，`USE_REAL_SUBSCRIBE: false` |

### 1.1 本地存储

| 键 | 用途 | 真实后端下的处理 |
| --- | --- | --- |
| `volt.db.v1` | Mock 业务库（会员、场次、预约、记录、消息等） | 不再读写；可从代码中保留演示分支 |
| `volt.session` | `{ token, mode, createdAt, user }` | 必须保留，请求层读取 token |
| `volt.bootstrapped` | 首次启动演示会话标记 | 真实模式下不再写入 |
| `volt.lastVenue` | 上次选中的门店 | 可以保留，纯 UI 偏好 |

### 1.2 关键文件

| 文件 | 作用 |
| --- | --- |
| `miniprogram/services/api.js` | 页面调用的唯一门面；只发真实 `wx.request`，401 自动重登一次 |
| `miniprogram/services/auth.js` | 会话、`wx.login`、token 过期检查、刷新用户 |
| `miniprogram/services/api-error.js` | 统一 `ApiError`（code/message/status/fields/requestId） |
| `miniprogram/services/geo.js` | 定位、场馆距离校验、导航 |
| `miniprogram/services/scan.js` | 扫码 / 开发工具模拟场馆码 |
| `miniprogram/services/subscribe.js` | 订阅消息授权 |
| `miniprogram/config/index.js` | 后端地址、订阅开关、签到围栏 |
| `miniprogram/utils/view.js` | 课程/门店图片、记录摘要等共享展示映射 |
| `miniprogram/data/volt.js` | 场馆坐标、订阅模板 ID |
| `miniprogram/data/catalog.js` | 由 `scripts/sync-catalog.mjs` 从官网同步的课程/门店字典 |

## 2. 切换步骤

### 2.1 微信公众平台

1. 开发管理 → 开发设置 → 服务器域名 → **request 合法域名**：加入 `https://api.volt.fit`（或实际 API 域名）。
   - 必须 HTTPS、443 端口、已备案；
   - 不能填 IP、localhost、通配符；
   - 开发工具可临时勾选「不校验合法域名」，真机必须配置。
2. 如果后端返回绝对图片地址，把图片域名加入 **downloadFile 合法域名**。
3. 用户隐私保护指引：声明「位置信息」用途（扫码签到场校验）；`app.json` 已声明 `scope.userLocation` 和 `requiredPrivateInfos: ["getLocation"]`。
4. 订阅消息：在公众平台申请 `classReminder`、`classChange`、`waitlist` 三个模板，替换 `miniprogram/data/volt.js` 中的占位 ID。

### 2.2 `miniprogram/config/index.js`

```js
module.exports = {
  API_BASE: 'http://127.0.0.1:8080/api/v1', // 真机改局域网 IP
  USE_REAL_SUBSCRIBE: false,   // 模板 ID 配好后开启
  GEOFENCE_METERS: 2000,
}
```

注意：

- `API_BASE` 必须包含 `/api/v1`，`request()` 只做字符串拼接。
- 开发者工具需关闭「校验合法域名」（`project.private.config.json` 的 `setting.urlCheck` 为 `false`）。
- 锁位时间以服务端 `lockExpiresAt` 为准；预约窗口 60 天。

### 2.3 登录

`auth.bootstrap()` 在真实模式下会：

1. 清理旧的 Mock `demo-bootstrap` 会话；
2. 没有会话时自动调用 `wx.login`，请求 `POST /auth/wx/login`；
3. 把请求层挂到 `api.setAuthGate()` 的 Promise 上，会员请求等 token 落盘后再发；
4. 收到 401 时清除 `volt.session` 并返回错误。

当前后端没有配置微信 AppSecret，`/auth/wx/login` 会映射到演示会员 `MBR-DEMO`；正式上线必须换成后端 `jscode2session` + openid 绑定。

### 2.4 演示数据重置

- 真实模式 `GET /member/account` 返回 `isDemo: false`，「我的 → 重置演示数据」入口自动隐藏。
- 本地要恢复演示基线：执行 `backend/src/main/resources/db/demo-reset.sql`，重启后端让 `VoltDataSeeder` 重建会员、记录、消息、身体数据和下一次预约。

### 2.5 开发工具与真机

| 场景 | 设置 |
| --- | --- |
| 本地联调 | 开发工具「不校验合法域名、web-view、TLS 版本」可临时开启 |
| 真机预览 | 必须配置合法域名；HTTP 不可用 |
| 登录 | 真机才能稳定走 `wx.login`；开发工具可用测试号 |
| 扫码 | 开发工具可走「手动选择场馆」；真机调用相机 |
| 定位 | 真机需要授权；拒绝后仍可凭场馆码核销（最终契约要求定位不阻塞） |

## 3. 请求层

### 3.1 现有实现

`miniprogram/services/api.js`：

```js
function request(path, options) {
  const session = wx.getStorageSync('volt.session')
  wx.request({
    url: `${config.API_BASE}${path}`,
    method: opts.method || 'GET',
    data: opts.data || {},
    header: {
      'content-type': 'application/json',
      ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
    },
    success(res) { /* 2xx resolve；否则 ApiError */ },
    fail() { /* NETWORK_ERROR */ },
  })
}
```

已经具备：

- token 自动注入；
- 2xx 统一 resolve；
- HTTP 错误取 `body.code` / `body.message`；
- 网络失败统一 `NETWORK_ERROR`；
- GET 的 `data` 由 `wx.request` 自动拼 query。

### 3.2 必须补充

| 项 | 要求 |
| --- | --- |
| 幂等键 | 锁位、确认、取消、候补、扫码核销、自由训练支持 `Idempotency-Key`；`miniprogram/services/api.js` 按操作生成键并缓存 10 分钟，网络重试复用，成功后清除 |
| 401 | 清除 `volt.session`，提示登录；不要无限重试，也不要静默创建新会话 |
| 错误字段 | 保留 `fields`、`requestId`；页面至少能读到 `fields` |
| HTTP 兜底 | 响应无 `code` 时按状态码映射：401→`UNAUTHORIZED`，404→`NOT_FOUND`，429→`RATE_LIMITED`，5xx→`SERVER_ERROR` |
| 超时 | 建议 15s；`wx.request` 默认 60s，签到/锁位不宜等太久 |
| 日志 | 失败时 `console.warn({ path, code, requestId })`，便于对账 |
| 路径 | `API_BASE` 已含 `/api/v1`，`path` 不要再带前缀 |
| 响应 | 不套 `data` 外壳，直接使用业务对象 |

`ApiError` 当前定义为 `(code, message)`，并带有 `.status` / `.fields` / `.requestId`（见 `miniprogram/services/api-error.js`）。如需扩展：

```js
class ApiError extends Error {
  constructor(code, message, extra = {}) {
    super(message)
    this.code = code
    this.status = extra.status || 0
    this.fields = extra.fields || null
    this.requestId = extra.requestId || ''
  }
}
```

页面已有 `format.errorText(error)`，优先显示 `error.message`；需要字段错误时再读 `error.fields`。

### 3.3 错误体

```json
{
  "code": "SLOT_FULL",
  "message": "这场已经约满，换一天或换一家门店试试。",
  "fields": null,
  "requestId": "req_01J..."
}
```

小程序只依赖 `code`、`message`、`fields`。`requestId` 不上屏，但必须能打进日志。

## 4. 鉴权与账号

### 4.1 `wx.login` 链路

```
小程序                        后端
  | wx.login() → code           |
  | POST /auth/wx/login         |
  | { code, profile? }          |
  | --------------------------->| jscode2session
  |                             | openid / unionid
  | <---------------------------| { token, member }
  | 存 volt.session             |
```

要求：

- `code` 一次性，不能重复使用；
- AppSecret、`session_key` 只能存在服务端，不能下发；
- `token` 建议 JWT 或随机 opaque token，服务端可撤销；
- 返回 Member 字段与最终契约 3.1 一致；
- openid / unionid / 手机号各自唯一。

当前 `auth.loginWithWechat()`（`miniprogram/services/auth.js:39-76`）已经按这个链路调用 `api.auth.login({ code, profile })`，拿到 `result.token` 和 `result.member` 后写入 `volt.session`。

登录成功后还会调用 `api.mine.updateProfile(profile)` 写入昵称/头像（`auth.js:58-65`），失败被静默吞掉。真实模式下建议：

- 头像昵称走 `chooseAvatar` + `nickname` 输入；
- `PATCH /member/me` 失败时提示但不阻断登录；
- 不要因为资料更新失败而丢弃 token。

### 4.2 401 处理

统一在 `request()` 里做：

1. 收到 401 `UNAUTHORIZED`；
2. `wx.removeStorageSync('volt.session')`；
3. `wx.showToast` 或打开登录组件；
4. 当前请求 reject，页面停止后续动作。

不要自动重新 `wx.login` 后重放写请求，避免用户无感知地生成第二张预约。

### 4.3 手机号绑定

微信登录后，未绑定手机号的会员会进入 `pages/bind-phone/index`，完成“手机号 + 短信验证码”绑定：

1. `wx.login` → 后端按 openid 找到/创建会员，`phoneBound=false`；
2. 绑定页输入手机号，调用 `POST /auth/phone/send`（需 token）；
3. 输入验证码，调用 `POST /auth/link-phone`，body 为 `{ phone, code }`；
4. 后端校验 `purpose=bind` 的 OTP，再绑定/合并会员，返回 `{ member, merged, token? }`。

```js
api.auth.sendBindCode(phone)
api.auth.linkPhone({ phone, code })
```

本地联调设置 `VOLT_EXPOSE_OTP=true`，`/auth/phone/send` 会在响应里返回 `devCode`；生产接短信通道。微信手机号快速验证组件 `getPhoneNumber` 要求非个人主体小程序，且按成功调用计费，当前 Demo 不使用。

绑定后：

- 返回 `{ member, merged, token? }`：`merged=true` 表示该手机号已有官网会员，后端已把微信会员合并过去；如果换了主账号，会额外返回新 `token`，小程序要写回 `volt.session`。
- 返回最新 Member，页面刷新 `phoneBound`、`phone`；
- 同一手机号的官网游客预约必须能认领；
- 认领幂等，不重复创建预约、不重复占库存；
- 手机号唯一；如果手机号已绑定其他微信，返回 409 `PHONE_TAKEN`，提示联系前台处理。两边都有次卡时会合并次数，不会丢卡。

### 4.4 官网购卡单

官网 `/card-orders` 生成的登记单会随 `/member/home`、`/member/account` 的 `cardOrders[]` 返回：

- `status=pending`：会员卡页显示「待开卡 · 到店付款后生效」，不显示“余额不足”；
- `status=activated` 且 `applied=true`：次数已并入会员，正常展示 `credits`；
- 小程序登录、绑定手机号、进入首页/会员卡页时，后端会自动领取已开卡但未入账的权益，不需要单独点“同步”。

小程序会员卡页也可以直接登记购卡：

- 点次卡方案行 → 底部弹层选择开卡门店 → `POST /card-orders`；
- 登记成功后显示「待开卡」，到店付款后由店员开卡；
- 会员卡页同时读取 `GET /member/credits/ledger`，展示 `CARD` / `CHECKIN` 等次卡流水；
- 新用户（无次卡、无待开卡）首次进入首页会收到一次购卡提示，关闭后仍可从「我的 → 购买会员卡」进入。

本地演示打开 `VOLT_DEMO_PAY=true` 后，会员卡页会出现「演示支付并开卡」：

- 按钮会二次确认，并明确标注“演示用 · 不会真实扣款”；
- 调用 `POST /demo/card-orders/{orderNo}/pay`，只把购卡单标记为已开卡，不调用 `wx.requestPayment`；
- 成功后刷新会员卡页，次数自动到账。生产必须关闭该开关，不能把它当真实微信支付。

### 4.4 邀请绑定

落地页 `GET /invites/:ref?program=P01` 不需要登录。`miniprogram/pages/invite/index.js` 的 `onClaim()` 在登录后调用 `api.invite.accept(ref)`；未登录时先拉起登录，登录成功回调继续绑定。

正式流程：

1. 打开邀请页，读 `ref`、`program`；
2. 未登录先登录；
3. 登录后 `POST /invites/:ref/accept`，同一被邀请人只绑定一次；
4. 绑定成功后跳转预约，并带上 `programCode`；
5. `GET /invites/me` 拉取我的邀请记录（也可以继续用 `/member/account` 里的 `invites`）。

`ref` 为空时不要调用 accept；分享链接里的 `ref` 需要 `encodeURIComponent`。

## 5. 页面 → 接口总表

| 页面/动作 | 当前调用位置 | 适配函数 | 最终接口 |
| --- | --- | --- | --- |
| 首页加载 | `miniprogram/pages/index/index.js:94` | `api.home.get()` + `api.community.get()` | `GET /member/home` + `GET /community` |
| 首页确认下一节 | `miniprogram/pages/index/index.js:241` | `api.bookings.confirm(id)` | `POST /bookings/:id/confirm` |
| 首页取消下一节 | `miniprogram/pages/index/index.js:222` | `api.bookings.cancel(id)` | `POST /bookings/:id/cancel` |
| 首页加入挑战 | `miniprogram/pages/index/index.js:282` | `api.community.joinChallenge()` | `POST /challenges/current/join` |
| 首页活动报名 | `miniprogram/pages/index/index.js:297` | `api.community.joinEvent(id)` | `POST /events/:id/signups` |
| 预约页加载 | `miniprogram/pages/schedule/index.js:86` | `api.schedule.get()` | `GET /catalog/schedule` |
| 锁位 | `miniprogram/pages/schedule/index.js:534` | `api.bookings.create(sessionId, { reminderOptIn })` | `POST /sessions/:id/bookings` |
| 确认锁位 | `miniprogram/pages/schedule/index.js:590` | `api.bookings.confirm(id)` | `POST /bookings/:id/confirm` |
| 取消锁位/预约 | `miniprogram/pages/schedule/index.js:625`、`688` | `api.bookings.cancel(id)` | `POST /bookings/:id/cancel` |
| 加入候补 | `miniprogram/pages/schedule/index.js:651` | `api.waitlist.join(sessionId)` | `POST /sessions/:id/waitlist` |
| 退出候补 | `miniprogram/pages/schedule/index.js:687` | `api.waitlist.cancel(id)` | `DELETE /waitlist/:id` |
| 预约台账 | `miniprogram/pages/bookings/index.js:50` | `api.bookings.list()` | `GET /member/bookings` |
| 台账确认 | `miniprogram/pages/bookings/index.js:169` | `api.bookings.confirm(id)` | `POST /bookings/:id/confirm` |
| 台账取消 | `miniprogram/pages/bookings/index.js:194` | `api.bookings.cancel(id)` | `POST /bookings/:id/cancel` |
| 台账退出候补 | `miniprogram/pages/bookings/index.js:216` | `api.waitlist.cancel(id)` | `DELETE /waitlist/:id` |
| 进场页加载 | `miniprogram/pages/checkin/index.js:78` | `api.checkin.info()` | `GET /member/checkin` |
| 扫码核销 | `miniprogram/pages/checkin/index.js:174` | `api.checkin.auto(venueCode, { distanceMeters })` | `POST /checkins/scan` |
| 自由训练 | `miniprogram/pages/checkin/index.js:278` | `api.checkin.free(venueCode, { distanceMeters })` | `POST /checkins/free` |
| 记录页加载 | `miniprogram/pages/archive/index.js:67` | `api.archive.get()` | `GET /member/archive?month=` |
| 记录身体数据 | `miniprogram/pages/archive/index.js:261` | `api.archive.addBodyLog({ heightCm, weightKg })` | `POST /member/body-logs` |
| 我的加载 | `miniprogram/pages/mine/index.js:59` | `api.mine.get()` | `GET /member/account` |
| 手机号绑定 | `pages/bind-phone/index.js` | `api.auth.sendBindCode` → `api.auth.linkPhone({phone, code})` | `POST /auth/phone/send` → `POST /auth/link-phone` |
| 提醒偏好 | `miniprogram/pages/mine/index.js:197` | `api.messages.setSubscription(key, value)` | `PATCH /member/subscriptions` |
| 重置演示数据 | `miniprogram/pages/mine/index.js:243` | `api.demo.reset()` | 真实模式禁用 |
| 消息列表 | `miniprogram/pages/messages/index.js:47` | `api.messages.list()` | `GET /member/messages` |
| 单条已读 | `miniprogram/pages/messages/index.js:112` | `api.messages.markRead(id)` | `POST /member/messages/:id/read` |
| 全部已读 | `miniprogram/pages/messages/index.js:126` | `api.messages.markRead('all')` | `POST /member/messages/:id/read`（`:id=all`） |
| 社区加载 | `miniprogram/pages/community/index.js:37` | `api.community.get()` | `GET /community` |
| 社区加入挑战 | `miniprogram/pages/community/index.js:69` | `api.community.joinChallenge()` | `POST /challenges/current/join` |
| 社区活动报名 | `miniprogram/pages/community/index.js:84` | `api.community.joinEvent(id)` | `POST /events/:id/signups` |
| 门店列表 | `miniprogram/pages/venues/index.js:21` | `api.catalog.venues()` | `GET /catalog/venues` |
| 邀请落地 | `miniprogram/pages/invite/index.js:42` | `api.invite.get(ref, programCode)` | `GET /invites/:ref?program=` |
| 邀请绑定 | `miniprogram/pages/invite/index.js` 的 `claimAndGo()` | `api.invite.accept(ref)` | `POST /invites/:ref/accept` |

已封装但当前没有页面调用：

- `api.schedule.detail(sessionId)` → `GET /sessions/:id`，预留给分享单个场次；
- `api.invite.me()` → `GET /invites/me`；
- `api.catalog.coaches(filters)` → `GET /catalog/coaches`；
- `api.catalog.sessions(filters)` → `GET /catalog/sessions`；
- `api.catalog.programs()` / `api.catalog.plans()`。

## 6. 关键接口契约

### 6.1 GET /catalog/schedule（v1 兼容聚合）

最终契约允许后端优先实现该聚合，或把适配层改为按门店调用 `/catalog/sessions`（§6.1）。当前页面只认下面这个形状，字段名不能变：

```json
{
  "days": [
    {
      "iso": "2026-11-01",
      "cn": "周日",
      "short": "11/01",
      "isToday": true,
      "relative": "今天",
      "tag": "TODAY"
    }
  ],
  "sessions": [
    {
      "id": "S003-20261102",
      "code": "S003",
      "date": "2026-11-02",
      "dateShort": "11/02",
      "dayCn": "周一",
      "startTime": "19:30",
      "endTime": "20:30",
      "time": "19:30",
      "venueCode": "V-01",
      "venueName": "天河工场",
      "venueImage": "/assets/images/station-tianhe.webp",
      "programNo": "P01",
      "programCode": "P01",
      "programName": "光影搏击",
      "programEn": "SHADOW BOXING",
      "programImage": "/assets/images/program-boxing.webp",
      "intensity": "HIGH",
      "durationMin": 60,
      "kcal": 620,
      "coachId": "C01",
      "coachName": "阿K",
      "coach": "阿K",
      "capacity": 16,
      "left": 4,
      "booked": 12,
      "soldOut": false,
      "isPast": false,
      "myStatus": "confirmed",
      "myBookingId": "BK-202611010001",
      "myLockExpiresAt": 0,
      "myWaitlistId": "",
      "waitlistPosition": 0
    }
  ],
  "venues": [],
  "member": {},
  "programs": [],
  "coaches": []
}
```

约定：

- `days` 固定 7 天，从今天起算；
- `sessions` 只返回今天及以后、未取消的场次，按日期 + `startTime` 升序；
- `myStatus`：`"" | locked | confirmed | waitlisted | checked-in`；
- `myLockExpiresAt` 用 epoch 毫秒，页面倒计时直接使用；
- `venues[].todaySlots` = 当日未开始且 `left > 0` 的场次数；
- `member` 至少包含 `id/nickname/credits/creditsTotal/creditPercent/planName/expiresAt`；
- `programs` / `coaches` 用于本地筛选，字段与最终契约 3.3 / 3.4 一致。

如果后端选择分段接口，适配层需要在 `schedule.get(filters)` 内并行请求 `/catalog/venues`、`/catalog/sessions`、`/catalog/coaches`、`/member/account`，再组装成上面的对象；页面不改。

### 6.2 GET /catalog/venues

```json
{
  "venues": [
    {
      "code": "V-01",
      "name": "天河工场",
      "en": "TIANHE STATION",
      "address": "体育西路 103 号 3F",
      "metro": "地铁 1/3 号线体育西路站",
      "hours": "07:00 — 23:00",
      "imageUrl": "/assets/images/station-tianhe.webp",
      "latitude": 23.1319,
      "longitude": 113.3214,
      "todaySlots": 1
    }
  ]
}
```

小程序门店页读取 `data.venues`，并优先用 `latitude/longitude` 计算距离；当前页面用 `miniprogram/data/volt.js` 的本地坐标兜底。

### 6.3 GET /member/home

页面直接消费以下键（`miniprogram/pages/index/index.js:94-140`）：

```json
{
  "today": "2026-11-01",
  "member": {
    "id": "MBR-2049",
    "nickname": "阿电",
    "avatarUrl": "",
    "avatarText": "电",
    "phone": "138****6688",
    "phoneBound": true,
    "credits": 8,
    "creditsTotal": 10,
    "creditPercent": 80,
    "planName": "10 次充电卡",
    "expiresAt": "2027-03-09",
    "tier": "VOLT MEMBER"
  },
  "stats": { "weekSessions": 2, "weekKcal": 980, "streakWeeks": 4 },
  "nextBooking": {
    "id": "BK-202611010001",
    "status": "confirmed",
    "lockExpiresAt": 0,
    "programName": "光影搏击",
    "programImage": "/assets/images/program-boxing.webp",
    "coachName": "阿K",
    "venueCode": "V-01",
    "venueName": "天河工场",
    "date": "2026-11-02",
    "time": "19:30",
    "durationMin": 60,
    "canCheckin": false
  },
  "todayBooking": null,
  "week": [
    { "iso": "2026-11-01", "day": "11/01", "cn": "日", "status": "booked" }
  ],
  "programs": [],
  "venues": [],
  "brand": { "name": "VOLT", "phone": "400-900-6688" },
  "messagesUnread": 2,
  "subscriptions": { "classReminder": true, "classChange": true, "waitlist": true }
}
```

`week[].status` 取值：`done | booked | today | rest`；`today` 优先于其他状态，与现有页面一致。`nextBooking` 必须是完整展示对象，不能只给 `sessionId`。

### 6.4 锁位 / 确认 / 取消 / 候补

#### 锁位

```http
POST /api/v1/sessions/S003-20261102/bookings
Authorization: Bearer <token>
Idempotency-Key: 5b1e...
```

```json
{ "reminderOptIn": true }
```

201：

```json
{
  "booking": {
    "id": "BK-202611010002",
    "orderNo": "V2026110165505",
    "entryCode": "V2026110165505",
    "status": "locked",
    "lockExpiresAt": 1761991800000,
    "seatNo": 6,
    "sessionId": "S003-20261102"
  },
  "session": { "id": "S003-20261102", "left": 3 },
  "duplicate": false
}
```

- 已有有效预约：200 返回当前预约并标记 `duplicate: true`，不要报错；
- 满员：409 `SLOT_FULL`，页面自动切候补；
- 次数为 0 / 次卡过期：409 `NO_CREDITS` / `PASS_EXPIRED`；
- 场次已开始：409 `PAST_SESSION`；
- 锁位占库存，确认只改状态；
- 页面倒计时使用 `booking.lockExpiresAt`（epoch 毫秒），不要用本地时间重算。

当前页面仍保留了旧错误码 `SESSION_FULL` 的分支（`schedule/index.js:547`）；最终契约统一为 `SLOT_FULL`，后端不要再返回 `SESSION_FULL`。

#### 确认

```http
POST /api/v1/bookings/BK-202611010002/confirm
```

- 仅 `locked` 可确认；过期返回 409 `LOCK_EXPIRED`；
- 重复确认幂等返回当前预约；
- 成功写提醒消息（如启用订阅消息，由服务端发送）。

#### 取消

```http
POST /api/v1/bookings/BK-202611010002/cancel
```

- `locked` / `confirmed` 可取消；
- `confirmed` 距开课不足 2 小时返回 409 `CANCEL_WINDOW_CLOSED`；
- 释放库存，写消息，按 `createdAt` FIFO 递补；
- 响应包含完整 booking、session 和可选 `promotedWaitlistId`；
- 页面会重新拉取首页/台账，不依赖响应里的余位做长期状态。

#### 候补

```http
POST /api/v1/sessions/S012-20261105/waitlist
```

- 只有满员时可加入，有座返回 409 `HAS_SEAT`；
- 重复加入幂等返回原顺位；
- `DELETE /waitlist/:id` 退出候补；
- 递补生成的新预约是 `locked`，10 分钟内确认，超时继续递补下一位；
- 页面当前只展示 `position`，不轮询；依赖首页/台账刷新和订阅消息。

### 6.5 签到核销

#### 进场页数据

```http
GET /api/v1/member/checkin
```

返回：

```json
{
  "member": {},
  "todayBooking": {},
  "todayRecord": null,
  "stats": { "weekSessions": 2, "weekKcal": 980, "streakWeeks": 4 },
  "recentRecords": [],
  "venues": [],
  "brand": { "name": "VOLT", "phone": "400-900-6688" }
}
```

#### 扫码核销

```http
POST /api/v1/checkins/scan
Authorization: Bearer <token>
Idempotency-Key: ...
```

```json
{
  "venueCode": "V-01",
  "distanceMeters": 86,
  "signature": "optional"
}
```

响应必须同时包含：

```json
{
  "mode": "class",
  "venue": {},
  "booking": {},
  "record": {},
  "member": {},
  "stats": { "weekSessions": 3, "weekKcal": 1560, "streakWeeks": 4 },
  "locationLabel": "定位已校验 · 距场馆 86 m"
}
```

`mode`：

| mode | 含义 | 页面行为 |
| --- | --- | --- |
| `class` | 核销今日该馆 confirmed 课次，扣 1 次 | 成功页展示课次、热量、剩余次数 |
| `free-ready` | 今日无课 | 弹窗询问是否记录自由训练 |
| `already` | 同一预约今天已签到 | 幂等成功页，不重复扣次 |
| `wrong-venue` | 今日预约在另一家店 | 提示并可选记录自由训练 |

事务要求：

1. 校验场馆码、距离（定位失败不阻塞，按最终契约）；
2. 找到今日该馆 `confirmed` 预约；
3. 条件更新 `markCheckedInIfConfirmed`，按 `booking_id` 判重；
4. 条件扣 1 次（`credits > 0`）；
5. 写训练记录；
6. 写 `volt_credit_ledger`；
7. 返回最新 booking、record、member。

重复扫码必须返回 `already` 或同一记录，不能重复扣次。次数为 0 返回 409 `NO_CREDITS`；页面会提示转自由训练。

#### 自由训练

```http
POST /api/v1/checkins/free
```

```json
{ "venueCode": "V-01", "distanceMeters": 86 }
```

- 不扣次；
- 同一天同馆重复提交幂等；
- `record.mode = "free"`、`sessionId = null`、`programNo = "FREE"`；
- 响应同样带 `member`、`record`、`stats`、`locationLabel`。

### 6.6 我的 / 记录 / 身体数据

#### GET /member/account

页面读取（`miniprogram/pages/mine/index.js:59-95`）：

```json
{
  "member": {},
  "stats": {},
  "brand": {},
  "plans": [],
  "subscriptions": { "classReminder": true, "classChange": true, "waitlist": true },
  "messagesUnread": 2,
  "invites": { "code": "VLT8K3M", "invitedCount": 2, "trialCount": 1, "records": [] },
  "counts": { "upcoming": 2, "waitlist": 1, "history": 18 },
  "nextBooking": {},
  "venues": [],
  "isDemo": false
}
```

真实模式 `isDemo` 必须为 `false`，页面据此隐藏「重置演示数据」。`counts` 与 `/member/bookings` 的 `counts` 保持一致。

#### GET /member/archive?month=YYYY-MM

当前适配层没有传 `month`：

```js
archive: {
  get() {
    return call('/member/archive', () => mock.getArchive())
  }
}
```

真实接入时建议改为 `get(month)` 并拼 query；如果后端默认返回当前月，也可以不改。页面依赖：

```json
{
  "member": {},
  "stats": {},
  "records": [],
  "recordDates": [],
  "bodyLogs": [],
  "sessionTrend": [{ "label": "10/20", "value": 3 }],
  "durationTrend": [{ "label": "10/20", "value": 180 }],
  "weightTrend": [{ "label": "10/20", "value": 74.2 }],
  "programs": [],
  "credits": { "left": 8, "total": 10, "percent": 80, "planName": "10 次充电卡", "expiresAt": "2027-03-09" },
  "month": "2026-11"
}
```

`records[]` 字段按最终契约 3.8；`mode=free` 时 `programNo=FREE`、`sessionId=null`。

#### POST /member/body-logs

当前页面只传身高体重：

```json
{ "heightCm": 178, "weightKg": 74.2 }
```

要求：

- 后端补当天日期；
- 同一天覆盖，不新增重复行；
- BMI 服务端计算并返回；
- 响应 `{ "bodyLog": {}, "member": {} }`。

#### PATCH /member/me

请求子集：`nickname / avatarUrl / goal / heightCm / weightKg`。返回最新 Member。

### 6.7 消息与订阅

#### GET /member/messages

```json
{
  "messages": [
    {
      "id": "MSG-REMIND",
      "type": "reminder",
      "title": "光影搏击即将开课",
      "body": "今天 19:30 在天河工场开课，提前 15 分钟到场。",
      "action": "/pages/checkin/index",
      "createdAt": "2026-11-01T18:42:00+08:00",
      "read": false
    }
  ],
  "unread": 2,
  "subscriptions": { "classReminder": true, "classChange": true, "waitlist": true }
}
```

`type`：`reminder | change | waitlist | checkin | system`。`action` 必须是小程序可跳转路径；tab 页用 `switchTab`，其他用 `navigateTo`（页面已有这段逻辑）。

#### 已读

```http
POST /api/v1/member/messages/MSG-REMIND/read
POST /api/v1/member/messages/all/read
```

第二行即 `:id=all` 的字面路径。返回 `{ "unread": 1 }`。

#### 订阅偏好

最终契约定义 Subscription 为布尔对象。当前适配层发送的是 `{ key, value }`：

```js
setSubscription(key, value) {
  return call('/member/subscriptions', () => mock.setSubscription(key, value), {
    method: 'PATCH',
    data: { key, value },
  })
}
```

建议二选一并在联调前定稿：

- 推荐：适配层改成发送部分布尔对象 `{ "classReminder": false }`，后端返回完整对象；
- 兼容：后端同时接受 `{ key, value }`，并返回完整对象。

页面只使用返回值覆盖 `subscriptions`，两种方式都能工作，但不要长期并行两套。

### 6.8 社区

`GET /community` 带 token 时返回 `mySigned`、`joinedByMe`、`membership`；匿名时均为空/false。

报名和加入挑战都不需要请求体（使用当前 Member）：

```http
POST /api/v1/events/EV-1104/signups
POST /api/v1/challenges/current/join
```

错误：

- 重复报名：409 `DUPLICATE_SIGNUP`；
- 活动满员：409 `EVENT_FULL`；
- 挑战重复加入：200 `already: true`，不新增记录。

页面报名成功后会整页重新加载社区数据（`community/index.js:84-93`），所以 `/community` 的 `signed/remaining/soldOut/mySigned` 必须立即反映写入结果。活动卡片还依赖 `desc / day / month / time / venueName`；缺少 `desc` 时描述区为空。

### 6.9 邀请

```http
GET /api/v1/invites/VLT8K3M?program=P01
```

返回：`inviterName / isSelf / program / trialPrice / benefits / referralCode / brand`。

```http
POST /api/v1/invites/VLT8K3M/accept
Authorization: Bearer <token>
```

绑定邀请关系，同一被邀请人只能绑定一次。响应 `{ "bound": true, "already": false }`；重复领取返回 `already: true`，页面照常进入选课。

```http
GET /api/v1/invites/me
```

返回 `code / invitedCount / trialCount / records / referralCode`；当前也可以继续从 `/member/account.invites` 取。

## 7. 状态机与客户端同步

### 7.1 预约

```
locked ──确认──> confirmed ──核销──> checked-in
  │                  │
  ├─取消/超时─> cancelled / expired
  └─取消──────> cancelled
```

页面状态映射（`miniprogram/pages/bookings/index.js:71-95`）：

| status | 标签 | 页面行为 |
| --- | --- | --- |
| `locked` | 锁位中 | 展示倒计时，可确认/释放 |
| `confirmed` | 已确认 | 可取消、可查看进场码 |
| `checked-in` | 已签到 | 只读 |
| `cancelled` | 已取消 | 只读 |
| `expired` | 已释放 | 只读 |

同步规则：

- 锁位成功后用响应 `session.left` 更新场次，不要先本地减 1 再等服务端；
- `lockExpiresAt` 到期秒表归零后重新拉取场次/台账；
- 确认/取消后重新拉取首页、预约台账、场次，避免多处状态不一致；
- `BAD_STATE` / `LOCK_EXPIRED` 统一提示并刷新；
- 候补递补不是客户端轮询，依赖首页/台账刷新和订阅消息。

### 7.2 候补

```
waiting ──有空位──> converted（生成 locked 预约）
   │
   ├─用户退出─> cancelled
   └─过期────> expired
```

递补按 `createdAt` FIFO；被递补者收到消息，10 分钟内确认，否则释放并继续下一位。

### 7.3 签到

- `checked-in` 后预约不可再取消；
- 同馆同日重复扫码返回 `already`；
- 自由训练同馆同日幂等；
- 次数扣减只能由服务端事务完成，客户端不本地扣。

## 8. 字段兼容

| 场景 | 保留字段 | 新代码字段 | 说明 |
| --- | --- | --- | --- |
| 场次时间 | `time` | `startTime` | 两者都要返回，页面部分位置仍读 `time` |
| 课程编码 | `programCode` | `programNo` | 两者都要返回 |
| 教练 | `coach` | `coachId` + `coachName` | `coach` 供旧展示 |
| 课程图片 | `programImage` | `image` / `imageUrl` | 小程序优先本地映射 |
| 场馆图片 | `venueImage` | `imageUrl` | 同上 |
| 记录课程 | `programCode` | `programNo` | 记录页两者都读 |
| 记录时间 | `startTime` | `time` | 页面用 `startTime || time` |
| 预约单号 | `orderNo` | `entryCode` | 当前阶段相等；核销以服务端为准 |
| 手机号 | `phone`（脱敏） | `phoneBound` | 不返回 `phoneFull` |
| 锁位到期 | `myLockExpiresAt` / `lockExpiresAt` | epoch 毫秒 | 不要返回 ISO 字符串 |
| 消息时间 | `createdAt` | ISO 或 epoch | 页面 `dateUtil.stamp` 两者都支持 |

时间约定：

- `date`：`YYYY-MM-DD`；
- `startTime` / `endTime`：`HH:mm`；
- `lockExpiresAt`：epoch 毫秒；
- `checkedAt`、Waitlist `createdAt`：最终契约给的是 epoch 毫秒；
- Booking `createdAt`、Community `joinedAt`：ISO 8601；
- 一律 `Asia/Shanghai`。

## 9. 错误码与页面处理

| code | HTTP | 当前页面行为 | 建议 |
| --- | --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 多数只 toast `message` | 手机号/身高体重读取 `fields` 标注输入 |
| `DATE_OUT_OF_RANGE` | 400 | 预约页没有日期输入，靠后端兜底 | toast + 刷新 7 天窗口 |
| `UNAUTHORIZED` | 401 | 当前无专门处理 | 清会话、打开登录 |
| `NOT_FOUND` | 404 | toast | 刷新对应列表 |
| `DUPLICATE_BOOKING` | 409 | 预约页有旧分支 | 新契约锁位返回 200 + `duplicate: true`；保留分支兼容旧后端 |
| `SLOT_FULL` | 409 | 自动切候补；`SESSION_FULL` 也识别 | 后端只返回 `SLOT_FULL` |
| `LOCK_EXPIRED` | 409 | toast | 关闭弹层并刷新场次 |
| `PAST_SESSION` | 409 | 落通用 toast | 刷新场次，隐藏预约按钮 |
| `NO_CREDITS` | 409 | 预约弹窗；进场弹窗可转自由训练 | 保持 |
| `PASS_EXPIRED` | 409 | 预约弹窗 | 引导续卡（本期只展示） |
| `HAS_SEAT` | 409 | toast | 刷新后允许直接预约 |
| `CANCEL_WINDOW_CLOSED` | 409 | 取消失败弹窗 | 保持 |
| `DUPLICATE_SIGNUP` | 409 | toast | 重新拉社区数据，按钮置已报名 |
| `EVENT_FULL` | 409 | toast | 重新拉社区数据，按钮置满 |
| `BAD_STATE` | 409 | toast | 刷新当前资源 |
| `RATE_LIMITED` | 429 | toast | 禁用按钮数秒 |
| `SERVER_ERROR` | 500 | toast | 保留操作入口，允许重试 |
| `NETWORK_ERROR` | - | 本地 toast | 保留操作入口，幂等键复用 |

## 10. 微信能力与配置

| 能力 | 现状 | 接后端要求 |
| --- | --- | --- |
| `wx.login` | 已有 | code 只换一次；后端换 openid/session_key |
| `wx.requestSubscribeMessage` | 已有，模板占位 | 申请 3 个模板，替换 `data/volt.js`，开启 `USE_REAL_SUBSCRIBE` |
| `wx.scanCode` | 已有 | 场馆码内容映射到 `venueCode`；可加 `signature` |
| `wx.getLocation` | 已有，2.6s 超时 | 隐私指引声明；失败不阻塞核销 |
| `chooseAvatar` | 登录组件使用 | `PATCH /member/me` 上传/保存头像 URL |
| `wx.makePhoneCall` | 已有 | 品牌电话与后端配置保持一致 |
| 分享 | `onShareAppMessage` / `onShareTimeline` | 携带 `ref` / `program`；落地页读取后绑定邀请 |
| 图片 | 本地 `assets/images` | 绝对 URL 需配 downloadFile 域名；相对路径需映射到本地/CDN |

订阅消息与业务事件的对应关系：

| 业务事件 | 模板 key | 最终契约消息 type |
| --- | --- | --- |
| 预约成功 / 开课提醒 | `classReminder` | `reminder` |
| 教练或时间调整 | `classChange` | `change` |
| 候补递补 | `waitlist` | `waitlist` |
| 签到成功 | 暂无模板 | `checkin`（站内消息） |
| 系统通知 | 无 | `system` |

服务端发送订阅消息需要用户的 `openid`、模板 ID 和跳转路径；`session_key` 不下发。订阅授权是用户一次性的选择，服务端不能假设永远可发。

## 11. Mock 与真实后端的差异

| 行为 | Mock | 真实后端 |
| --- | --- | --- |
| 登录 | 首次启动自动演示会话 | `wx.login` + `/auth/wx/login` |
| 库存 | 本地 `volt.db.v1` | `class_sessions` + 事务 |
| 锁位 | 本地 10 分钟 | 服务端 `lockExpiresAt` + 过期任务 |
| 扣次 | 本地 `member.credits` | `volt_credit_ledger` + 条件扣减 + 事务；预约不扣、核销扣 1 |
| 核销 | 本地写记录 | `markCheckedInIfConfirmed` 条件更新 + `(booking_id, reason)` 唯一流水 |
| 候补 | 本地 FIFO | 数据库 FIFO + 消息 |
| 社区 | 本地 `community` | 服务端活动/挑战表 |
| 重置 | `api.demo.reset()` | 不存在，必须隐藏 |
| 消息 | 本地生成 | 服务端写消息 + 订阅消息 |
| 数据共享 | 与官网不互通 | 同一 Member/库存/账本 |

Mock 清理：

```js
wx.removeStorageSync('volt.db.v1')
wx.removeStorageSync('volt.session')
wx.removeStorageSync('volt.bootstrapped')
wx.removeStorageSync('volt.lastVenue')
```

清除 Storage 会重新进入首次演示态；正式包不要依赖这个行为。

## 12. 联调验收（小程序）

1. 配置真实域名与 `USE_MOCK=false` 后，首页、预约、进场、记录、我的全部走网络请求。
2. `wx.login` 成功，`volt.session.token` 非空；后续请求带 `Authorization`。
3. 官网用手机号预约 → 小程序绑定同一手机号 → 「我的预约」看到同一 `orderNo`。
4. 选门店 → 按教练/时间 → 锁位 → 确认；首页、台账、进场码三处状态一致。
5. 锁位 10 分钟不确认 → 场次恢复可售；候补第一位收到递补消息并进入锁位。
6. 满员候补 → 取消后 FIFO 递补 → 候补者收到通知，库存不超卖。
7. 扫码核销 → 次数 -1、`credit_ledger` 有记录、记录页新增；重复扫码不重复扣。
8. 次数 0 / 次卡过期 → 锁位与核销都被拒绝，错误码分别正确。
9. 自由训练不扣次，同日同馆重复提交幂等。
10. 社区报名/挑战与官网同会员状态一致；重复报名/满员返回 409。
11. 未带 token 调用会员接口返回 401，页面清会话并打开登录。
12. 超出预约窗口返回 `DATE_OUT_OF_RANGE`；并发锁位不超卖。
13. 真实模式隐藏「重置演示数据」，`isDemo=false`。
14. 订阅消息模板配置后，确认/变更/递补能收到对应通知。
15. 图片、定位、扫码在真机可用；拒绝定位仍可核销。

## 13. 本期不做

- 微信支付、退款、在线收款；
- 改期、转赠、多人同行、优惠券；
- 管理后台、短信通道；
- 跨城门店；
- 课程评价；
- 官网会员中心与小程序账户的更多合并场景（已预留接口）。

## 14. 待后端确认

| 项 | 小程序现状 | 需要确认 |
| --- | --- | --- |
| 列表响应 | 已写入最终契约 §2：`{ venues }` / `{ programs }` | 后端按此实现，页面无需改动 |
| `/catalog/schedule` | 页面依赖 7 天聚合 | v1 实现聚合，还是适配层拆成 sessions |
| `GET /member/home` 等聚合 | 页面直接消费固定键 | 按本文 6.3 / 6.6 实现 |
| 手机号绑定 | 已调用 `{ phone }`，页面在成功后刷新本地 session | 生产可改为短信验证码换取手机号 |
| 订阅偏好 | 当前 `{ key, value }`，后端按此实现 | 保持兼容即可 |
| 邀请绑定 | 已在领取体验课时调用 accept | 邀请奖励规则未实现 |
| `isDemo` | 已移除 | 不再返回演示标记 |
| 图片 | 本地 assets | 绝对 URL 域名或相对路径映射 |
| `POST /checkins/scan` | 传 `distanceMeters` | `signature` 是否启用、围栏失败策略 |
