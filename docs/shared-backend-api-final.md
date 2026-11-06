# VOLT 两端共用后端契约（最终版）

> 2026-11 更新：官网与小程序均已移除 Mock，只连接真实后端。文中仍出现 `USE_MOCK` / `mock-db` 的段落属于历史说明，以根目录 README 和当前代码为准。
>
> 适用端：官网 Web（`website/src/`）与微信会员小程序（`miniprogram/`）。
> 状态：契约已落地到本仓库的 `backend/`（Spring Boot + MyBatis + MySQL），官网与小程序均已接通真实后端。
> 版本：2026-11-02 / v2.1。
>
> 配套接入文档（只展开各端的切换步骤、页面映射与联调项，冲突时以本文为准）：
> - 官网：[web-integration.md](web-integration.md)
> - 微信小程序：[wx-integration.md](wx-integration.md)
> - 后端实现与运行：[../backend/README.md](../backend/README.md)

## 0. 一句话

同一套 Member、门店、课程、教练、场次、预约、课时账本和社区数据；官网与小程序只是两个入口，预约与次数必须在同一套库存和账本上结算。

## 1. 当前实现与切换

| 端 | 页面入口 | 数据来源 | 后端地址 |
| --- | --- | --- | --- |
| 官网 | 5 屏 + 社区页；按门店/课程/日期选场次预约 | `website/src/services/api/index.js`，只走真实 `/api/v1` | `VITE_API_BASE`，默认 `http://127.0.0.1:8080/api/v1` 或同源 `/api/v1` |
| 小程序 | 首页 / 预约 / 进场 / 记录 / 我的；社区、门店、消息、邀请 | `miniprogram/services/api.js`，只走真实 `/api/v1` | 默认 `http://127.0.0.1:8080/api/v1`；真机改成局域网 IP |

约束：
- 页面不直接写 `fetch` / `wx.request`；只通过各自的 `services/api` 门面。
- 小程序 `services/api.js` 自动读取 `volt.session.token` 并附加 `Authorization: Bearer <token>`。
- 官网和小程序共用同一套契约、编码和 MySQL 种子数据。
- 真实后端即 `backend/`；官网与小程序均已接通，包含 OTP/微信登录、预约锁位、候补、核销和次卡流水。

## 2. 通用约定

- Base URL：`/api/v1`；HTTPS only。
- JSON 字段 `camelCase`；数据库字段 `snake_case`。
- 日期 `YYYY-MM-DD`，时间 `HH:mm`，时区 `Asia/Shanghai`；时间戳 ISO 8601；`lockExpiresAt` 用 epoch 毫秒（两端倒计时直接使用）。
- 金额整数元；次数整数。
- 不套 `data` 外壳，直接返回业务对象；列表接口返回命名键对象（`{ "venues": [...] }`、`{ "programs": [...] }`、`{ "plans": [...] }`、`{ "coaches": [...] }`），不返回裸数组。
- 错误返回统一错误体。
- 写请求支持 `Idempotency-Key`；预约、签到、报名、挑战加入必须幂等。

错误体：

```json
{
  "code": "SLOT_FULL",
  "message": "这场已经约满，换一天或换一家门店试试。",
  "fields": null,
  "requestId": "req_01J..."
}
```

最终错误码：

| code | HTTP | 场景 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 字段校验失败，可带 `fields` |
| `DATE_OUT_OF_RANGE` | 400 | 超出可预约窗口 |
| `UNAUTHORIZED` | 401 | token 缺失或过期 |
| `NOT_FOUND` | 404 | 门店/课程/场次/活动不存在 |
| `DUPLICATE_BOOKING` | 409 | 同一会员/手机号同一场已有有效预约 |
| `TRIAL_ALREADY_BOOKED` | 409 | 该手机号已预约过首次体验课 |
| `SLOT_FULL` | 409 | 场次满员 |
| `LOCK_EXPIRED` | 409 | 锁位过期 |
| `PAST_SESSION` | 409 | 场次已开始 |
| `NO_CREDITS` | 409 | 次数不足 |
| `PASS_EXPIRED` | 409 | 次卡过期 |
| `HAS_SEAT` | 409 | 仍有空位，无需候补 |
| `CANCEL_WINDOW_CLOSED` | 409 | 距开课不足 2 小时 |
| `DUPLICATE_SIGNUP` | 409 | 已报名该活动 |
| `EVENT_FULL` | 409 | 活动满员 |
| `BAD_STATE` | 409 | 当前状态不允许该操作 |
| `RATE_LIMITED` | 429 | 频率限制 |
| `NETWORK_ERROR` | - | 客户端网络失败（本地错误） |
| `SERVER_ERROR` | 500 | 未预期异常 |

迁移别名：旧小程序码 `SESSION_FULL` 映射为 `SLOT_FULL`；后端稳定后可移除。

## 3. 最终领域模型

### 3.1 Member

```json
{
  "id": "MBR-2049",
  "nickname": "阿电",
  "avatarUrl": "",
  "avatarText": "电",
  "phone": "139****9000",
  "phoneBound": true,
  "goal": "增肌 · 体能",
  "heightCm": 178,
  "weightKg": 74.2,
  "bmi": 23.4,
  "credits": 8,
  "creditsTotal": 10,
  "creditPercent": 80,
  "planCode": "PLAN-03",
  "planName": "10 次充电卡",
  "expiresAt": "2027-03-09",
  "tier": "VOLT MEMBER",
  "referralCode": "VLT8K3M",
  "joinedAt": "2026-03-18"
}
```

### 3.2 Venue（含今日余位）

```json
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
```

`todaySlots` 为当日尚未开始且 `left > 0` 的场次数。

### 3.3 Program / Plan

```json
{
  "no": "P02",
  "name": "高压燃脂",
  "en": "HIGH VOLTAGE HIIT",
  "desc": "短时高强度间歇，心率拉满。",
  "meta": { "intensity": "HIGH", "duration": "45 MIN", "kcal": "550 KCAL" },
  "image": "/assets/images/program-conditioning.webp",
  "imageUrl": "/assets/images/program-conditioning.webp"
}
```

`image` 保留兼容，`imageUrl` 为统一字段。

```json
{
  "code": "PLAN-03",
  "name": "10 次充电卡",
  "en": "10 SESSION PASS",
  "price": 899,
  "unit": "10 节 / 180 天",
  "featured": false,
  "points": ["10 节团课", "180 天内有效", "可与朋友共用"],
  "cta": "查看详情"
}
```

### 3.4 Coach（C01–C06）

```json
{
  "id": "C01",
  "name": "阿K",
  "title": "搏击主教练",
  "avatarUrl": "/assets/images/coach-01.webp",
  "bio": "拳击与体能打底，强调站位和呼吸。",
  "programNos": ["P01"],
  "venueCodes": ["V-01", "V-02", "V-03"],
  "programs": ["光影搏击"],
  "venues": ["天河工场", "海珠工场", "番禺工场"],
  "nextAvailableAt": "2026-11-02T19:30:00+08:00",
  "slotsLeft": 5
}
```

固定对应：C01 阿K/P01，C02 赵彻/P02，C03 老魏/P03，C04 Mia/P04，C05 林岚/P05，C06 苏晓/P06。

### 3.5 Session（库存唯一单位）

```json
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
```

`myStatus`：`"" | locked | confirmed | waitlisted | checked-in`。

兼容说明：`startTime`/`time`、`programNo`/`programCode`、`coachId + coachName`/`coach` 同时保留；新代码用前者，旧页面可继续用后者。

### 3.6 Booking

```json
{
  "id": "BK-202611010001",
  "orderNo": "V2026110165505",
  "entryCode": "V2026110165505",
  "memberId": "MBR-2049",
  "sessionId": "S003-20261102",
  "status": "confirmed",
  "seatNo": 5,
  "lockExpiresAt": null,
  "reminderOptIn": true,
  "source": "wx",
  "planCode": "PLAN-03",
  "planName": "10 次充电卡",
  "isTrial": false,
  "amount": 0,
  "name": "阿电",
  "phone": "139****9000",
  "date": "2026-11-02",
  "dateShort": "11/02",
  "dayCn": "周一",
  "startTime": "19:30",
  "endTime": "20:30",
  "time": "19:30",
  "programNo": "P01",
  "programName": "光影搏击",
  "programEn": "SHADOW BOXING",
  "programImage": "/assets/images/program-boxing.webp",
  "intensity": "HIGH",
  "durationMin": 60,
  "kcal": 620,
  "coachId": "C01",
  "coachName": "阿K",
  "coach": "阿K",
  "venueCode": "V-01",
  "venueName": "天河工场",
  "venueImage": "/assets/images/station-tianhe.webp",
  "canCancel": true,
  "cancelHint": "开课前 2 小时可取消",
  "canCheckin": false,
  "createdAt": "2026-11-01T16:14:00+08:00"
}
```

状态：`locked → confirmed → checked-in`；异常 `cancelled / expired`。`entryCode` 前端阶段等于 `orderNo`；正式后端可返回签名进场码，扫码/前台核销均以服务端校验为准。

### 3.7 Waitlist

```json
{
  "id": "WL-202611010003",
  "memberId": "MBR-2049",
  "sessionId": "S012-20261105",
  "position": 1,
  "status": "waiting",
  "createdAt": 1762000000000
}
```

`waiting → converted / cancelled / expired`。取消/过期释放座位时按 `createdAt` FIFO 生成下一位 `locked` 预约。

### 3.8 Checkin / Record

```json
{
  "id": "R-...",
  "sessionId": "S003-20261102",
  "mode": "class",
  "source": "scan",
  "date": "2026-11-02",
  "time": "19:25",
  "startTime": "19:30",
  "checkedAt": 1762080300000,
  "programNo": "P01",
  "programName": "光影搏击",
  "programEn": "SHADOW BOXING",
  "intensity": "HIGH",
  "durationMin": 60,
  "kcal": 620,
  "coachId": "C01",
  "coachName": "阿K",
  "coach": "阿K",
  "venueCode": "V-01",
  "venueName": "天河工场",
  "venueImage": "/assets/images/station-tianhe.webp",
  "distanceMeters": 86
}
```

`mode=free` 时 `sessionId=null`、`programNo=FREE`、不扣次；普通课时核销必须扣 1 次并写 `credit_ledger`；`PLAN-01` 首次体验课核销不扣次、不写扣次流水。

### 3.9 Community

```json
{
  "stats": [{ "value": "3,204", "label": "在册社员", "en": "MEMBERS" }],
  "ranking": [{ "no": "01", "code": "VOLT-0457", "sessions": 23, "minutes": 1720, "weeks": 6, "me": false }],
  "stories": [{ "name": "阿 K", "role": "搏击教练 · 天河工场", "quote": "...", "img": "/images/coach-03-960.webp" }],
  "events": [{
    "id": "EV-1104", "title": "燃脂电流营", "day": "04", "month": "NOV · WED",
    "tag": "WEEKDAY / 周三", "time": "周三 19:30", "venueCode": "V-01", "venueName": "天河工场",
    "desc": "HIIT 循环 + 心率带冲刺，一节课烧掉一周奶茶热量。",
    "cap": 24, "signed": 18, "remaining": 6, "soldOut": false, "mySigned": false
  }],
  "challenge": {
    "id": "CH-3X-WEEK", "title": "本周练满 3 次", "desc": "...",
    "sessions": 3, "days": 7, "joined": 128, "percent": 68,
    "joinedByMe": false, "myProgress": 2
  },
  "membership": { "eventIds": [], "challengeJoined": false }
}
```

故事卡兼容字段：`tag`、`sessions`、`streak`、`favorite` 可空，官网按需展示；小程序只依赖 `name / role / quote / img`。

### 3.10 其他实体

- **BodyLog**：`{ date, heightCm, weightKg }`，同一天覆盖；BMI 服务端返回。
- **Message**：`{ id, type: reminder|change|waitlist|checkin|system, title, body, action, createdAt, read }`。
- **Subscription**：`{ classReminder, classChange, waitlist }` 布尔偏好。
- **Invitation**：`{ code, invitedCount, trialCount, records }`。

### 3.11 CardOrder（官网购卡登记）

```json
{
  "orderNo": "C202611030001",
  "name": "林一",
  "phone": "138****8000",
  "planCode": "PLAN-03",
  "planName": "10 次充电卡",
  "amount": 899,
  "credits": 10,
  "validDays": 180,
  "venueCode": "V-01",
  "venueName": "天河工场",
  "status": "pending",
  "applied": false,
  "createdAt": "2026-11-03T22:33:45",
  "activatedAt": null
}
```

`status`：`pending`（待开卡）→ `activated`（店员已确认）。`applied=true` 表示次数已并入会员；未登录/未绑定手机号时先保持 `false`，登录或刷新会员信息时自动领取。

## 4. 最终接口总表

| Method | Path | 鉴权 | 端 | 用途 |
| --- | --- | --- | --- | --- |
| GET | `/catalog/venues` | - | 双端 | 门店 + todaySlots / 坐标 / 图片 |
| GET | `/catalog/programs` | - | 双端 | 课程 |
| GET | `/catalog/plans` | - | 双端 | 次卡方案 |
| GET | `/catalog/coaches?venueCode` | - | 小程序 | 教练 |
| GET | `/catalog/sessions?venueCode&date&coachId&programNo` | - | 双端 | 场次（小程序后续分段拉取） |
| GET | `/catalog/schedule` | - | 小程序 | 7 天聚合；小程序当前预约页使用 |
| GET | `/sessions/:id` | - | 双端 | 场次详情（分享预设） |
| GET | `/availability?venue&program&date` | - | Web | 日期汇总余位 |
| GET | `/availability/trial?venue&date` | - | Web | 体验课跨课程时段（不要求先选课程） |
| POST | `/auth/wx/login` | - | 小程序 | `wx.login` code 换 token |
| POST | `/auth/otp/send` | - | Web | 验证码发送 |
| POST | `/auth/phone/send` | 是 | 小程序 | 绑定手机号验证码（`purpose=bind`） |
| POST | `/auth/otp/verify` | - | Web | 验证码登录 |
| POST | `/auth/link-phone` | 是 | 小程序 | 校验绑定验证码并绑定手机号 |
| GET | `/auth/members` | 是 | 小程序 | 当前微信名下的会员档案列表 |
| POST | `/auth/members/add` | 是 | 小程序 | 手机号 OTP 添加会员档案并切换 |
| POST | `/auth/switch-member` | 是 | 小程序 | 切换当前会员档案并换发 token |
| GET | `/member/home` | 是 | 小程序 | 首页 |
| GET | `/member/account` | 是 | 小程序 | 我的 |
| GET | `/member/bookings` | 是 | 双端 | 预约台账 |
| GET | `/member/credits/ledger` | 是 | 小程序 | 次卡扣减/返还流水（`{entries, credits}`） |
| GET | `/member/archive?month` | 是 | 小程序 | 记录页 |
| GET | `/member/checkin` | 是 | 小程序 | 进场页 |
| PATCH | `/member/me` | 是 | 双端 | 资料 |
| POST | `/member/avatar` | 是 | 小程序 | 本地头像上传（multipart `file`，≤5MB，JPG/PNG/WebP），返回最新 Member |
| POST | `/member/body-logs` | 是 | 小程序 | 身体数据 |
| GET | `/member/messages` | 是 | 小程序 | 消息 |
| POST | `/member/messages/:id/read` | 是 | 小程序 | 已读（`:id=all`） |
| PATCH | `/member/subscriptions` | 是 | 小程序 | 提醒偏好 |
| POST | `/bookings` | 是（体验课） | Web | 官网体验课预约；OTP 会员 + `PLAN-01`，0 次卡允许，`autoConfirm` 可传 |
| POST | `/sessions/:id/bookings` | 是 | 小程序 | 锁位 |
| POST | `/bookings/:id/confirm` | 是 | 双端 | 确认锁位 |
| POST | `/bookings/:id/cancel` | 是 | 双端 | 取消 |
| POST | `/sessions/:id/waitlist` | 是 | 小程序 | 候补 |
| DELETE | `/waitlist/:id` | 是 | 小程序 | 退出候补 |
| POST | `/checkins/scan` | 是 | 小程序 | 扫码核销 |
| POST | `/checkins/free` | 是 | 小程序 | 自由训练 |
| GET | `/community` | 可选 | 双端 | 社区聚合 |
| POST | `/events/:id/signups` | 可选 | 双端 | 活动报名 |
| POST | `/challenges/current/join` | 可选 | 双端 | 加入挑战 |
| GET | `/invites/:ref` | - | 双端 | 邀请落地 |
| GET | `/invites/me` | 是 | 小程序 | 我的邀请 |
| POST | `/invites/:ref/accept` | 是 | 双端 | 绑定邀请 |
| POST | `/card-orders` | 可选 | Web | 购卡登记（pending，不收款、不占座） |
| GET | `/member/card-orders` | 是 | 双端 | 会员购卡登记列表 |
| GET | `/staff/card-orders` | 店员密钥 | Web | 按手机号/状态查待开卡 |
| POST | `/staff/card-orders/:orderNo/activate` | 店员密钥 | Web | 店员确认收款后开卡 |
| POST | `/demo/card-orders/:orderNo/pay` | 是 | 小程序 | 演示支付并开卡；仅 `VOLT_DEMO_PAY=true`，不调用微信支付 |
| POST | `/payments/orders` | 是 | 预留 | 在线支付购卡（本期不做） |
| POST | `/payments/miniprogram/notify` | 微信签名 | 预留 | 支付回调 |

## 5. 页面与接口映射

### 5.1 小程序（最终实现）

| 页面/动作 | 当前调用 |
| --- | --- |
| 首页 | `GET /member/home` + `GET /community` |
| 预约选门店 | `GET /catalog/venues`（来自 `/catalog/schedule` 的 venues） |
| 按教练 / 按时间 | `GET /catalog/schedule` 的 sessions + coaches，按门店/日期/教练本地筛选 |
| 锁位 / 确认 / 取消 | `/sessions/:id/bookings` → `/bookings/:id/confirm|cancel` |
| 候补 | `/sessions/:id/waitlist` → `/waitlist/:id` |
| 进场页 | `GET /member/checkin`；扫码 `POST /checkins/scan`；自由训练 `POST /checkins/free` |
| 记录页 | `GET /member/archive`；身体数据 `POST /member/body-logs` |
| 我的 | `GET /member/account`；手机号 `POST /auth/link-phone` |
| 消息 | `GET /member/messages`；已读 / 订阅写入 |
| 社区 | `GET /community`；`POST /events/:id/signups`；`POST /challenges/current/join` |
| 门店 | `GET /catalog/venues` |
| 邀请 | `GET /invites/:ref`、`GET /invites/me`、`POST /invites/:ref/accept` |

### 5.2 官网

| 页面/动作 | 接口 |
| --- | --- |
| 首屏 ¥49 / 课程卡 / 页脚预约 | 本地选择 → `#offer`，后续带 `sessionId` |
| 04 屏余位 | `GET /availability?venue=&program=&date=` |
| 04 屏提交 | `POST /bookings`（可 autoConfirm，或后端折算最早场次） |
| 社区列表 | `GET /community` |
| 活动报名 / 挑战 | `POST /events/:id/signups` / `POST /challenges/current/join` |
| 会员中心（后续） | `/member/*` 与小程序共用 |

## 6. 关键接口细节

### 6.1 小程序预约页

小程序当前为了前端阶段简单，预约页读取 `/catalog/schedule` 聚合（7 天，已包含 venues/coaches/sessions），按“门店 → 教练/时间”本地筛选；锁位、确认、取消、候补全部走服务端。正式接入时：
- 后端优先实现 `/catalog/schedule` 的兼容聚合，或把适配层改为按门店调用 `/catalog/sessions`；页面字段不变。
- 下单必须以 `sessionId` 为准，禁止按“日期 + 课程”自行选场次。

### 6.2 锁位

`POST /sessions/:id/bookings`：

```json
{ "reminderOptIn": true }
```

```json
{
  "booking": {
    "id": "BK-...",
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

- 已有有效预约：返回当前预约并标记 `duplicate: true`，小程序按服务端 `status` 进入管理/锁位态。
- 满员：409 `SLOT_FULL`；次数不足/次卡过期：409 `NO_CREDITS` / `PASS_EXPIRED`。
- 会员预约要求已绑定手机号；场次限制在今天起 60 天内，否则返回 `VALIDATION_ERROR` / `DATE_OUT_OF_RANGE`。
- 锁位占库存，确认只改状态；锁位过期回到可售并按 FIFO 递补。

### 6.3 确认/取消

- `POST /bookings/:id/confirm`：仅 `locked`；过期 409 `LOCK_EXPIRED`；重复确认幂等返回当前预约。
- `POST /bookings/:id/cancel`：`locked` 或 `confirmed` 可取消；`confirmed` 距开课不足 2 小时返回 409 `CANCEL_WINDOW_CLOSED`。释放库存、写消息、按 FIFO 递补。
- 锁位超时：`VoltScheduleService.expireStaleLocks()` 每 60 秒扫描过期锁位，条件更新为 `expired`、释放座位并按 FIFO 递补；递补只生成新的 10 分钟锁位，不扣次。
- 响应包含完整 booking、session 与可选 `promotedWaitlistId`。

### 6.4 签到核销

`POST /checkins/scan` 请求：`{ venueCode, distanceMeters, signature? }`。

| mode | 含义 |
| --- | --- |
| `class` | 核销今日该馆 confirmed 普通课时，扣 1 次 |
| `trial` | 核销今日该馆 confirmed `PLAN-01` 体验课，不扣次 |
| `free-ready` | 今日无课，可自由训练 |
| `already` | 同一预约今天已核销，或该馆今天已有同预约的课次记录，幂等 |
| `wrong-venue` | 预约在另一家店 |

响应需同时返回 `mode`、`trial`（布尔，体验课为 true）、`member`、`record`、`booking`（若有）、`stats`（本周统计）、`locationLabel`。事务顺序：校验场馆/预约 → `markCheckedInIfConfirmed` 条件改为 `checked-in` → 普通课时按 `credits > 0` 条件扣 1 次（体验课跳过）→ 写训练记录 → 普通课时写 `volt_credit_ledger`（`CHECKIN`，-1）。唯一键 `(booking_id, reason)` 保证同一预约最多扣一次；普通课时余额不足抛 `NO_CREDITS` 并回滚核销。

`PLAN-01` 首次体验课按手机号限一次：创建预约时已有非取消/非过期的体验课单，返回 409 `TRIAL_ALREADY_BOOKED`。

预约锁位和确认都不扣次，取消/锁位超时也不返还；只有门店撤销核销时才追加 `REVOKE`（+1）流水，当前无自助撤销入口。

### 6.5 社区

- `GET /community` 带 token 时返回 `mySigned`、`joinedByMe`、`membership`；匿名时均为空/false。
- 报名使用当前 Member，不重复传姓名手机号；匿名 Web 才传 `name/phone`。
- 挑战重复加入返回 `already: true`，不新增记录；首页与社区页共用同一个 challenge 状态。

### 6.6 手机号合并与购卡开卡

- `POST /auth/link-phone` 必须携带 `{phone, code}`；`code` 来自 `/auth/phone/send` 的 `bind` 验证码，验证通过后才允许绑定/合并。
- 微信新会员默认 `phoneBound=false`；小程序启动登录后进入手机号验证页，未绑定前限制约课、购卡与进场。
- 绑定后，官网同一手机号登录返回同一 Member。
- 手机号已属于另一个会员时自动合并：微信会员有次卡时保留微信会员，并把手机号会员的次数累加过去；微信会员没有次卡时以手机号会员为主账号。
- 合并后可能换发新 token，响应为 `{member, merged, token?, members, activeMemberId}`；小程序需要把新 token 和会员档案列表写回本地会话。
- 一个微信 openid 可以通过 `volt_wechat_member` 关联多个手机号会员档案；`GET /auth/members` 返回 `{members, activeMemberId}`。
- 切换档案调用 `POST /auth/switch-member {memberId}`，返回新 token 和更新后的列表；添加档案调用 `POST /auth/members/add {phone, code}`，验证码使用 `login` 用途。
- 手机号仍是会员唯一标识：同一手机号永远对应同一条 `volt_member`；不同手机号是不同档案，次数、预约和流水互相隔离。同一手机号的多张卡订单继续汇总到该会员余额。
- 官网 `/card-orders` 只生成 `pending` 登记单，不生成预约、不占座、不收款。店员 `/staff/card-orders/:orderNo/activate` 确认后状态变 `activated`。
- 本地演示可开 `VOLT_DEMO_PAY=true`，小程序调用 `/demo/card-orders/:orderNo/pay` 把待开卡单标记为已开卡；该接口只改业务状态，不调用微信支付，生产必须关闭。
- 已开卡权益在会员登录、绑定手机号、刷新 `/member/home` 或 `/member/account` 时，按手机号领取并写入 `credits / planCode / expiresAt`，同时写一条 `CARD` 次卡流水。
- 官网游客预约按手机号认领，认领幂等，不重复创建预约。
- openid/unionid 不直接作为会员唯一键：`volt_member.openid` 保留主身份，关联表 `volt_wechat_member(openid, member_id, active)` 支持一个微信多档案；手机号唯一，AppSecret、session_key 只存在服务端。

### 6.7 邀请绑定

- `POST /invites/:ref/accept`：`ref` 对应 `volt_member.referral_code`；不能绑定自己。
- 写入 `volt_invite`，`invitee_member_id` 唯一；重复领取返回 `{bound:true, already:true}`。
- 小程序邀请页登录后领取体验课时调用；成功后「我的 → 邀请」显示邀请记录。

## 7. Mock（已移除）

官网与小程序均已删除 Mock，只连接真实后端。演示数据重置执行 `backend/src/main/resources/db/demo-reset.sql` 并重启后端。

## 8. 数据库与并发（落地要点）

实际库表以 `backend/src/main/resources/db/schema.sql` 为准；核心约束必须满足：

```sql
class_sessions (
  id VARCHAR(24) PRIMARY KEY,
  date DATE, start_time TIME, end_time TIME,
  venue_code VARCHAR(8), program_no VARCHAR(8), coach_id VARCHAR(8),
  capacity SMALLINT, booked SMALLINT DEFAULT 0,
  status ENUM('open','closed','cancelled') DEFAULT 'open',
  UNIQUE KEY uk_slot (venue_code, date, start_time, coach_id, program_no)
);

bookings (
  id VARCHAR(24) PRIMARY KEY, order_no VARCHAR(24) UNIQUE,
  member_id CHAR(12), session_id VARCHAR(24),
  status ENUM('locked','confirmed','checked-in','cancelled','expired'),
  lock_expires_at DATETIME(3), seat_no SMALLINT, source ENUM('web','wx'),
  active_flag TINYINT GENERATED ALWAYS AS
    (IF(status IN ('locked','confirmed','checked-in'), 1, NULL)) STORED,
  UNIQUE KEY uk_active_member_session (member_id, session_id, active_flag)
);

credit_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id CHAR(12),
  delta INT, balance_after INT, reason VARCHAR(24),
  ref_type VARCHAR(24), ref_id VARCHAR(32), created_at DATETIME(3)
);

checkins (
  id VARCHAR(24) PRIMARY KEY, member_id CHAR(12), session_id VARCHAR(24) NULL,
  mode ENUM('class','free'), venue_code VARCHAR(8), checkin_date DATE,
  UNIQUE KEY uk_class_checkin (member_id, session_id),
  UNIQUE KEY uk_free_checkin (member_id, venue_code, checkin_date, mode)
);
```

锁位事务：

```sql
START TRANSACTION;
SELECT capacity, booked FROM class_sessions WHERE id = ? FOR UPDATE;
UPDATE class_sessions SET booked = booked + 1 WHERE id = ? AND booked < capacity;
-- 0 rows → ROLLBACK, SLOT_FULL
INSERT INTO bookings (..., status, lock_expires_at) VALUES (..., 'locked', NOW(3) + INTERVAL 10 MINUTE);
COMMIT;
```

必须有：
- 每场每会员最多一个有效预约的数据库唯一约束；
- 每分钟过期任务释放锁位并 FIFO 递补；
- 签到事务扣次 + 写 ledger + 写 checkin 幂等；
- 活动报名事务 `UPDATE ... SET signed = signed + 1 WHERE signed < capacity`；
- 挑战唯一键 `(challenge_id, member_id/phone)`。

## 9. 两端验收

1. 官网用手机号预约 → 小程序绑定同一手机号 → 「我的预约」看到同一 `orderNo`。
2. 小程序选门店 → 按教练/时间 → 锁位 → 确认 → 首页/预约台账/进场码三处状态一致。
3. 10 分钟未确认 → 场次恢复可售，候补第一位收到递补消息。
4. 满员候补 → 取消后 FIFO 递补 → 候补者进入锁位并收到通知。
5. 扫码核销 → 次数 -1、ledger 有记录、记录页新增、重复扫码不重复扣。
6. 次数 0 / 次卡过期 → 不可锁位、不可核销，错误码明确。
7. 社区活动报名/挑战 → 小程序与官网同会员状态一致；报名满员/重复报名返回 409。
8. 日期超出窗口、未带 token 调会员接口、并发超卖均返回约定错误，不产生超卖或重复扣次。

## 10. 本期不做

微信支付/退款、改期、转赠、多人同行、优惠券、管理后台、短信通道、跨城门店、课程评价。已预留接口位，不在本次前端改造范围。
