# VOLT 官网（Web）后端接入文档

> 适用代码：`src/`（Vue 3 + Vite 官网，5 屏 + 社区页）。
> 契约来源：[shared-backend-api-final.md](shared-backend-api-final.md)（v2-final）。本文只展开官网侧的接入方式、适配层改造点与验收项，不重复领域模型、数据库和并发设计；如有冲突，以最终契约为准。
> 版本：2026-11-02。
> 小程序侧见 [wx-integration.md](wx-integration.md)。
>
> **2026-11 更新**：官网已移除 Mock，API 适配层只保留 `catalog` / `bookings` / `community` / `member` / `http`，只连接真实后端。本文 Mock 章节仅作历史参考。
>
> **接入进度（2026-11-02）**：API 适配层已按领域拆分到 `src/services/api/`；官网 04 屏已消费 `availability.sessions[]` 并提交 `sessionId`，社区报名已支持会员身份。真实后端已落地为本仓库的 `backend/`（Spring Boot 3.5 + MyBatis + MySQL），契约验收脚本为 `tools/api-check.mjs`。

## 1. 接入现状

| 项目 | 当前实现 |
| --- | --- |
| 框架 | Vue 3 + Vite，入口 `src/main.js`，根组件 `src/App.vue` |
| 页面 | 首屏 / 充电站 / 课程 / 价格 / 预约 / 页脚 + 独立社区页 |
| 数据来源 | 静态数据 + 本地 Mock；真实模式走后端 `/api/v1` |
| API 适配层 | `src/services/api/`，公开入口 `index.js` |
| 后端实现 | `backend/`（同仓库），Base URL `http://localhost:8080/api/v1` |
| Mock 存储 | `localStorage`：`volt.bookings.v1`、`volt.event-signups.v1`、`volt.challenge.v1` |
| 选择态 | `src/services/selection.js`，`localStorage` 键 `volt.selection.v1` |
| 登录 | 体验课预约必须先做手机号 OTP 验证，token + member 摘要存 `volt.web.session.v1`；后端仍兼容旧游客预约，但官网表单不再匿名提交 |
| 后端地址 | `VITE_API_BASE`（留空用同源 `/api/v1`） |

真实模式与 Mock 模式的差异只有三处：

- 真实模式不再写 `volt.bookings.v1` / `volt.event-signups.v1` / `volt.challenge.v1`，预约与报名只落服务端；
- 选择态仍存本地（`volt.selection.v1`），它只是 UI 选择态；
- 适配层会带 `Idempotency-Key` 与可选 `Authorization: Bearer <token>`。

真正调用 API 的位置：

| 文件 | 用途 |
| --- | --- |
| `src/components/volt/steps/StepOffer.vue` + `src/components/volt/offer/BookingForm.vue` | 预约布局、表单、日期 → 场次选择 |
| `src/components/volt/member/WebMemberPanel.vue` | OTP 发送/校验、会员首页、次卡与下一节预约、退出登录 |
| `src/components/community/CommunityPage.vue` + `src/components/community/dialogs/CommunitySignupDialog.vue` | 社区聚合、活动报名、加入挑战 |

实际请求集中在 `src/composables/useBooking.js` 与 `src/services/api/`。另外 `src/services/api/catalog.js` 导出了 `listVenues`、`listPrograms`、`listPlans`，但当前没有任何页面调用：

- 首屏、课程、价格、门店内容全部直接 import `src/data/*.js`；
- 因此只打开真实后端开关，不会让这些静态内容变动态；
- 需要动态化时必须先引入 catalog 状态，见第 3.6 节。

## 2. 切换 Mock / 真实后端

### 2.1 配置

复制 `.env.example` 为 `.env.local`：

```bash
VITE_API_BASE=https://api.volt.fit/api/v1
VITE_USE_MOCK=false
```

规则（`src/services/api/config.js`）：

```js
const API_BASE = (import.meta.env.VITE_API_BASE || '').replace(/\/+$/, '')
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true' || !API_BASE
```

- `VITE_API_BASE` 为空时强制 Mock，即使 `VITE_USE_MOCK=false`；
- `VITE_API_BASE` 必须包含 `/api/v1`，适配层不再补路径前缀；
- 只支持 HTTPS；
- Vite 环境变量在构建时写入产物，改完要重启 dev server / 重新 build；
- 不要把 AppSecret、短信密钥等写进任何 `VITE_*` 变量，它们会被打进前端包。

### 2.2 模式对照

| 模式 | 条件 | 行为 |
| --- | --- | --- |
| Mock | `VITE_USE_MOCK=true` 或 `VITE_API_BASE` 为空 | 本地生成余位、写 localStorage、模拟 420ms 延迟 |
| 真实后端 | `VITE_USE_MOCK=false` 且 `VITE_API_BASE` 非空 | 所有导出函数走 `fetch`，不再读写预约类 localStorage |
| 混合（不推荐） | 在 Mock 模式下手改 localStorage | 只适合演示，不能作为联调依据 |

### 2.3 本地跑真实后端

后端在本仓库的 `backend/`，与官网共用同一个 MySQL 库：

```bash
# 1. 后端：首次启动自动建库建表 + 灌种子数据（场次覆盖今天起 60 天）
cd backend
mvn spring-boot:run          # http://localhost:8080

# 2. 官网切到真实后端
cd ..
VITE_API_BASE=http://localhost:8080/api/v1 VITE_USE_MOCK=false npm run dev -- --port 5176

# 3. 契约自检（56 项，覆盖本文件 §11 的验收清单）
node tools/api-check.mjs
```

后端默认允许 `http://localhost:*` / `http://127.0.0.1:*` 跨域；生产用 `VOLT_CORS_ORIGINS` 指定正式域名。数据库账号密码放 `backend/.env`（已在 `.gitignore` 中），不要写进 `application.yml`。

### 2.4 后端已实现的端点

| Method | Path | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/catalog/venues` | - | 门店 + `todaySlots` |
| GET | `/api/v1/catalog/programs` | - | 课程（含 `meta`） |
| GET | `/api/v1/catalog/plans` | - | 次卡方案（含 `points`） |
| GET | `/api/v1/catalog/coaches` | - | 教练（小程序预约页在用） |
| GET | `/api/v1/availability` | - | 指定课程的日期汇总余位 + `sessions[]` |
| GET | `/api/v1/availability/trial` | - | 体验课按门店 + 日期返回跨课程可约时段 |
| POST | `/api/v1/bookings` | 是（体验课） | 官网体验课预约；会员 token + `PLAN-01`，允许 0 次卡 |
| GET | `/api/v1/community` | 可选 | 社区聚合 + `membership` |
| POST | `/api/v1/events/{id}/signups` | 可选 | 活动报名 |
| POST | `/api/v1/challenges/current/join` | 可选 | 加入本周挑战 |
| POST | `/api/v1/auth/otp/send` | - | 验证码登录（短信通道未接，本地可开 `VOLT_EXPOSE_OTP=true` 取 devCode） |
| POST | `/api/v1/auth/otp/verify` | - | 换 token |
| GET | `/api/v1/member/bookings` | 是 | 会员预约台账 |
| PATCH | `/api/v1/member/me` | 是 | 会员资料 |
| POST | `/api/v1/card-orders` | 可选 | 官网购卡登记，生成 `pending` 单，不收款 |
| GET | `/api/v1/member/card-orders` | 是 | 会员购卡登记列表 |
| GET | `/api/v1/staff/card-orders` | 店员密钥 | 店员按手机号/状态查待开卡 |
| POST | `/api/v1/staff/card-orders/{orderNo}/activate` | 店员密钥 | 店员确认收款后开卡 |

### 2.5 官网体验课与购卡登记是两条路径

04 屏不再把“买卡”做成一次预约：

- `PLAN-01` 首次体验课：先做手机号 OTP，再由会员 token 提交 `POST /bookings`；不选课程，按门店 + 日期展示 `/availability/trial` 返回的全部可约时段；体验课只允许一次，非会员金额 ¥49 到店支付，已开卡会员随卡赠课 ¥0。
- `PLAN-03` 10 次充电卡：走 `POST /card-orders`，生成购卡登记单，**不生成预约、不占座**。
- 到店付款后，店员在官网 `/staff` 页面确认开卡；会员下次登录官网或刷新会员面板时，权益按手机号领取。
- 本地演示可设 `VOLT_DEMO_PAY=true`，小程序会员卡页会出现「演示支付并开卡」；它只改业务状态，不调用微信支付。
- 会员账号与次卡权益分开：OTP 登录只代表有账号，开卡确认后才写入 `credits / planCode / expiresAt`。
- 小程序绑定同一手机号时会合并账号；如果官网先登录过，绑定不会丢卡。

后端与契约的一致性由 `tools/api-check.mjs` 自动核对，改后端后跑一遍即可。

## 3. 适配层改造清单

以下改动都在 `src/services/api/` 内完成，页面组件不感知 Mock。

### 3.1 路径补齐 `/catalog`

当前真实的 fetch 路径与最终契约不一致：

| 当前 | 最终契约 | 说明 |
| --- | --- | --- |
| `GET /venues` | `GET /catalog/venues` | 门店 |
| `GET /programs` | `GET /catalog/programs` | 课程 |
| `GET /plans` | `GET /catalog/plans` | 次卡方案 |
| `GET /availability` | `GET /availability` | 不变 |
| `POST /bookings` | `POST /bookings` | 不变 |
| `GET /community` | `GET /community` | 不变 |
| `POST /events/:id/signups` | 不变 | — |
| `POST /challenges/current/join` | 不变 | — |

适配层修改后：

```js
export const listVenues = () =>
  USE_MOCK ? mockApi.listVenues() : request('/catalog/venues').then((res) => res.venues)
```

### 3.2 列表响应解包

最终契约不套 `data` 外壳。为与小程序现有 Mock 和页面字段保持一致，本文约定列表接口返回命名键对象：

```json
{ "venues": [] }
{ "programs": [] }
{ "plans": [] }
{ "coaches": [] }
```

官网适配层解包为数组后再返回；页面不接触 `venues`/`programs` 键名。

### 3.3 预约响应解包

`POST /bookings` 返回 `{ booking, session }`，而 `StepOffer.vue` 的成功弹窗直接使用扁平对象：

```js
const order = await createBooking({...})
booking.value = order
```

适配层必须解包并补齐页面需要的字段：

```js
export const createBooking = (input) =>
  USE_MOCK
    ? mockApi.createBooking(input)
    : request('/bookings', { method: 'POST', body: input, idempotent: true })
        .then((res) => res.booking)
```

`StepOffer.vue` 当前读取 `order.planCode / planName / orderNo / name / venueName / programName / date / amount`。最终 Booking 模型均包含这些字段；`phone` 是脱敏后的展示值，页面弹窗不展示完整手机号。

### 3.4 登录态

预约面板已接 OTP 登录：`POST /auth/otp/send` → `POST /auth/otp/verify`，结果写入 `volt.web.session.v1`（`{ token, member, phone }`）。之后预约、会员首页请求都会自动带 `Authorization`；后端会把同手机号下的游客预约认领到会员。最终契约的 `/community`、`/bookings`、`/member/*` 支持可选 token。适配层注入点：

```js
function authHeaders() {
  const token = getWebToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}
```

- 官网体验课预约必须带 OTP 会员 token（`createBooking` 传 `auth:true`）；后端允许 0 次卡会员预约 `PLAN-01`，不扣次、不检查卡余额；
- 社区报名等需要会员归属的写请求仍按登录态带 token；
- token 过期返回 401 `UNAUTHORIZED`：清除本地会话，提示重新登录；不要静默降级为游客再提交一次，避免生成重复预约。

后端当前的判定：**带了 token 但无效或过期一律返回 401**（可选鉴权接口也不例外），让前端有机会清掉失效会话。为此适配层做了区分处理：

- 读取类请求（GET）收到 401：清会话后按游客重试一次，保证社区页还能出数据；
- 写类请求（POST）收到 401：不清单、不重试，直接把错误抛给页面，用户再提交一次即可（同一 `Idempotency-Key` 复用，后端返回同一张单，不会重复下单）。

官网会话存储键建议使用 `volt.web.session.v1`（仅 token + member 摘要），放在适配层内部，不属于后端契约；具体键名和存储位置可在会员中心开发时定稿。

### 3.5 写请求幂等

最终契约要求预约、签到、报名、挑战加入支持 `Idempotency-Key`。官网至少要对以下请求加上：

- `POST /bookings`
- `POST /events/:id/signups`
- `POST /challenges/current/join`

实现要求：

- 每次用户点击提交生成一个 UUID，重试时复用同一个键；
- 成功后清空；失败但可重试时保留；
- 后端对同一键返回同一业务结果，不重复扣库存/写报名；
- 页面不要因为网络超时自动重新提交而换新键。

### 3.6 错误体归一

当前 `request()` 已把服务端错误映射为 `ApiError`（`status`、`code`、`fields`），需要补两点：

1. 响应不是 JSON 或没有 `code` 时，归为 `SERVER_ERROR`；
2. 把后端字段名映射成表单字段名，`StepOffer.vue` 才能定位到具体输入框：

| 后端 `fields` 键 | 表单字段 |
| --- | --- |
| `venueCode` | `venue` |
| `programNo` | `program` |
| `planCode` | `plan` |
| `name` / `phone` / `date` | 同名 |

`CommunityPage.vue` 只识别 `name`、`phone`，报名接口返回其他键时可忽略。

### 3.7 社区数据归一

官网组件与最终契约有两处命名差异，适配层或页面需要处理：

| 页面使用 | 契约字段 | 处理 |
| --- | --- | --- |
| `event.venue` | `event.venueName` | 适配层补 `venue: event.venueName`（或页面改用 `venueName`） |
| 本地 `signedEventIds` / `challengeJoined` | `membership.eventIds` / `membership.challengeJoined` | 真实模式下以服务端 membership 初始化；本地只做演示回退 |
| 本地 `mySigned` | 事件级 `mySigned` | 报名后以服务端返回的 `event` 为准 |

当前 `CommunityPage.vue` 在 `onMounted` 里读取 `getLocalMembership()`（约 557 行）并用本地 `localStorage` 判断已报名。接真实后端后：

- 匿名访问：`membership` 为空，保持未报名态；
- 带 token：用 `membership` 覆盖本地状态；
- 不要再把真实报名写回 `localStorage`，否则换浏览器会误判。

### 3.8 动态 catalog（可选）

若希望门店/课程/价格由后端维护，新增一个轻量 catalog store：

1. 启动时并行调用 `/catalog/venues`、`/catalog/programs`、`/catalog/plans`；
2. 成功则替换 `src/data/*.js` 的展示数据；
3. 失败回退静态数据，页面不空白；
4. `StepOffer`、`StepStations`、`StepPrograms` 改为从 store 读取。

本期可以不做：官网首屏、课程、价格、门店仍是静态内容，只有余位/预约/社区走接口。若不做，必须保证静态编码与后端种子一致（`V-01–V-03`、`P01–P06`、`PLAN-01–03`）。

## 4. 官网用到的接口

| Method | Path | 鉴权 | 使用位置 |
| --- | --- | --- | --- |
| GET | `/catalog/venues` | - | 动态 catalog（可选） |
| GET | `/catalog/programs` | - | 动态 catalog（可选） |
| GET | `/catalog/plans` | - | 动态 catalog（可选） |
| GET | `/availability` | - | 04 屏余位 + 场次 |
| POST | `/bookings` | 可选 | 04 屏提交预约（带 `sessionId`） |
| GET | `/community` | 可选 | 社区页 |
| POST | `/events/:id/signups` | 可选 | 社区活动报名 |
| POST | `/challenges/current/join` | 可选 | 加入本周挑战 |
| POST | `/auth/otp/send` | - | 预约面板会员登录 |
| POST | `/auth/otp/verify` | - | 预约面板会员登录 |
| GET | `/member/home` | 是 | 预约面板会员摘要 / 下一节预约 |
| GET | `/member/bookings` | 是 | 会员中心 / 订单查询（后续） |
| PATCH | `/member/me` | 是 | 会员资料（后续） |

官网已接 `/auth/otp/*`、`/member/home` 以及 `/availability` 的 `sessions[]`；`/catalog/sessions`、签到和候补接口仍由小程序承接。官网选场次不新增接口，直接消费 `/availability` 返回的场次数组。

## 5. 接口契约（官网视角）

### 5.1 GET /availability

Query：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `venue` | 是 | `V-01` | 门店 code |
| `program` | 是 | `P02` | 课程 no |
| `date` | 是 | `2026-11-05` | `YYYY-MM-DD` |

```http
GET /api/v1/availability?venue=V-01&program=P02&date=2026-11-05
```

200：

```json
{
  "venueCode": "V-01",
  "venueName": "天河工场",
  "programNo": "P02",
  "programName": "高压燃脂",
  "date": "2026-11-05",
  "capacity": 40,
  "booked": 12,
  "remaining": 28,
  "soldOut": false,
  "sessions": [
    {
      "id": "S012-20261105",
      "startTime": "12:20",
      "endTime": "13:05",
      "coachId": "C02",
      "coachName": "赵彻",
      "capacity": 16,
      "left": 7,
      "soldOut": false
    }
  ]
}
```

约定：

- `capacity / booked / remaining` 是该门店 × 课程 × 日期所有**仍可约**场次的汇总；
- 当天已经开课的场次不计入汇总（也不再出现在 `sessions[]`），否则深夜会把「已结束」显示成「还有位」；这条与「所有场次汇总」的原约定不同，以本条为准；
- `remaining = capacity - booked`；`soldOut = remaining <= 0`。当天课全部开课后 `remaining = 0`、`soldOut = true`，页面按钮显示「该场已满 · 换一天」；
- 预约窗口为今天起 60 天（与 `BOOKING_WINDOW_DAYS` 一致），超出返回 400 `DATE_OUT_OF_RANGE`；
- 门店或课程不存在返回 404 `NOT_FOUND`；
- `sessions[]` 已接入官网 04 屏：前端自动选中第一个可约场次，用户可切换，提交时带 `sessionId`；为空或后端兼容旧表单时仍可只传日期，由后端选当日最早可约场次。

#### GET /availability/trial

体验课不要求先选课程，按门店 + 日期跨课程返回时段：

```http
GET /api/v1/availability/trial?venue=V-01&date=2026-11-06
```

响应字段与 `/availability` 类似，但没有单一 `programNo/programName`，`sessions[]` 每项自带课程、教练、余位；`trial=true`。官网 04 屏用这个接口显示所有可约时段，提交时只需 `sessionId + PLAN-01`，后端从场次反推课程。

### 5.2 POST /bookings

#### 请求 A：兼容请求（无具体场次）

```http
POST /api/v1/bookings
Content-Type: application/json
Idempotency-Key: 8f4c2b1e-...
```

```json
{
  "planCode": "PLAN-01",
  "name": "林一",
  "phone": "13800138000",
  "venueCode": "V-01",
  "programNo": "P02",
  "date": "2026-11-05",
  "autoConfirm": true
}
```

服务端按日期 + 门店 + 课程选当日最早可约场次；没有可约场次返回 409 `SLOT_FULL`。官网当前优先使用下方带 `sessionId` 的请求，本请求仅用于旧表单/兼容场景。

#### 请求 B：当前官网请求（带具体场次）

```json
{
  "sessionId": "S012-20261105",
  "planCode": "PLAN-01",
  "name": "林一",
  "phone": "13800138000",
  "venueCode": "V-01",
  "programNo": "P02",
  "date": "2026-11-05",
  "autoConfirm": true
}
```

官网同时带上门店/课程/日期用于日志和兜底；服务端以 `sessionId` 为准。

规则：

- 官网不带 token 时允许匿名预约，`source` 记为 `web`；
- 带 token 时使用当前 Member，并校验手机号归属（如冲突按最终契约的手机号合并规则处理）；
- `autoConfirm=true`：直接 `confirmed` 并占用库存；官网成功弹窗即可展示；
- `autoConfirm=false`：创建 `locked`，页面需要展示 10 分钟倒计时与确认入口；本期官网没有确认 UI，建议固定传 `true`；
- 同一手机号/会员同一场已有有效预约：409 `DUPLICATE_BOOKING`；
- 字段校验失败：400 `VALIDATION_ERROR`，`fields` 键按 3.6 映射；
- 日期超出窗口：400 `DATE_OUT_OF_RANGE`；
- 当天场次全部已开课：409 `PAST_SESSION`（不是 `SLOT_FULL`，避免误导成“约满”）；
- 写入必须幂等，重复提交不得生成第二张预约；
- `amount` 取所选方案价格（`PLAN-01` 49 / `PLAN-02` 20 / `PLAN-03` 899），与 Mock 一致；支付尚未接入，`amount` 只作展示。官网本期只把 `PLAN-01` 作为体验课预约；`PLAN-03` 走 `/card-orders` 登记，不占用场次。会员次卡在到店扫码核销时扣 1 次并写 `volt_credit_ledger`，预约本身不扣次；`PLAN-01` 体验课核销不扣次，且同一手机号限一次。

后端实现补充：

- 预约、报名、挑战加入都在一个数据库事务里完成，`increaseBooked` 与预约写入要么都成功要么都回滚，不会漏占/漏放座位；
- 座位占用用 `UPDATE ... WHERE booked < capacity` 做原子扣减，并发抢最后一个座位只有一个成功，其余 409 `SLOT_FULL`；
- `booking.id`（`BK-202611020001`）与 `orderNo`（`V2026110165505`）用随机后缀 + 查重生成，不再用「当天计数 + 1」，避免并发撞主键。

201：

```json
{
  "booking": {
    "id": "BK-202611010001",
    "orderNo": "V2026110165505",
    "entryCode": "V2026110165505",
    "memberId": null,
    "sessionId": "S012-20261105",
    "status": "confirmed",
    "seatNo": 5,
    "lockExpiresAt": null,
    "source": "web",
    "planCode": "PLAN-01",
    "planName": "首次体验课",
    "amount": 49,
    "name": "林一",
    "phone": "138****8000",
    "date": "2026-11-05",
    "startTime": "12:20",
    "endTime": "13:05",
    "programNo": "P02",
    "programName": "高压燃脂",
    "coachId": "C02",
    "coachName": "赵彻",
    "venueCode": "V-01",
    "venueName": "天河工场",
    "createdAt": "2026-11-01T16:14:00+08:00"
  },
  "session": { "id": "S012-20261105", "left": 6 }
}
```

适配层解包后把 `booking` 交给 `useBooking`，成功弹窗读取 `date / time / orderNo` 等字段；`session` 摘要保留在 booking 上。

官网现流程会先 OTP 验证手机号，`memberId` 为当前手机号会员；旧游客调用仍可传 `memberId=null`，但同一手机号后续必须能认领到同一张订单。体验课预约响应额外带 `isTrial: true`，核销响应带 `trial: true`。

#### 游客预约与后续认领（兼容旧调用）

- 旧游客预约的 `memberId` 可以为 `null`，但 `orderNo` 必须持久化；
- 小程序绑定同一手机号后，`GET /member/bookings` 必须能返回这张官网订单；
- 官网 OTP 登录后也应看到同一 `orderNo`；
- 认领按手机号幂等，不得复制预约、不得重复占用库存。

### 5.3 GET /community

匿名请求：

```http
GET /api/v1/community
```

带 token 请求额外返回 `membership`、事件级 `mySigned`、挑战级 `joinedByMe`：

```json
{
  "stats": [],
  "ranking": [],
  "stories": [],
  "events": [
    {
      "id": "EV-1104",
      "title": "燃脂电流营",
      "day": "04",
      "month": "NOV · WED",
      "tag": "WEEKDAY / 周三",
      "time": "周三 19:30",
      "venueCode": "V-01",
      "venueName": "天河工场",
      "desc": "HIIT 循环 + 心率带冲刺，一节课烧掉一周奶茶热量。",
      "cap": 24,
      "signed": 18,
      "remaining": 6,
      "soldOut": false,
      "mySigned": false
    }
  ],
  "challenge": {
    "id": "CH-3X-WEEK",
    "title": "本周练满 3 次",
    "sessions": 3,
    "days": 7,
    "joined": 128,
    "percent": 68,
    "joinedByMe": false,
    "myProgress": 0
  },
  "membership": { "eventIds": [], "challengeJoined": false }
}
```

- 匿名时 `membership.eventIds` 为空、`challengeJoined=false`；
- 页面展示前必须保证 `remaining = cap - signed` 且 `soldOut` 与之一致；
- 社区数据可以缓存，但用户报名后要立即用响应里的 `event` 覆盖当前项。

### 5.4 POST /events/:id/signups

匿名（官网当前形态）：

```json
{ "name": "陈舟", "phone": "13900139000" }
```

带 token：忽略 `name/phone`，使用当前 Member。

200：

```json
{
  "signupNo": "E20261101001",
  "joinedAt": "2026-11-01T16:30:00+08:00",
  "event": {
    "id": "EV-1104",
    "signed": 19,
    "cap": 24,
    "remaining": 5,
    "soldOut": false,
    "mySigned": true
  }
}
```

错误：400 `VALIDATION_ERROR`、404 `NOT_FOUND`、409 `DUPLICATE_SIGNUP`、409 `EVENT_FULL`。

`CommunityPage.vue` 用返回的 `result.event` 覆盖列表项，并展示 `result.signupNo`。适配层需要保留 `event` 上的展示字段（`title/time/venueName/day/month/tag`），不能只返回计数。

### 5.5 POST /challenges/current/join

匿名：

```json
{ "name": "陈舟", "phone": "13700137000" }
```

带 token 可省略手机号。

```json
{
  "joined": true,
  "already": false,
  "memberNo": "M2213551251",
  "joinedAt": "2026-11-01T16:35:00+08:00"
}
```

- 重复加入返回 200，`already: true`，不报错、不新增记录；
- 手机号格式错误返回 400 + `fields.phone`。

### 5.6 会员中心（后续）

最终契约已定义官网 OTP 登录，但当前官网没有登录 UI。接入时：

```http
POST /api/v1/auth/otp/send
{ "phone": "13800138000" }
```

```http
POST /api/v1/auth/otp/verify
{ "phone": "13800138000", "code": "123456" }
```

`verify` 响应建议与小程序登录对齐：

```json
{ "token": "...", "member": { "id": "MBR-2049", "nickname": "阿电", "phone": "139****9000" } }
```

token 有效期、验证码冷却/错误次数、`fields` 键名需后端确认。会员中心页面再调用：

- `GET /member/bookings`：官网预约 + 小程序预约统一台账；
- `PATCH /member/me`：昵称、目标、身高体重；
- `GET /community` + token：活动/挑战会员态。

## 6. 页面 → 适配函数 → 接口

| 页面/动作 | 代码位置 | 适配函数 | 接口 |
| --- | --- | --- | --- |
| 04 屏余位查询 | `src/composables/useBooking.js` | `getTrialAvailability` | `GET /availability/trial` |
| 04 屏提交预约 | `src/composables/useBooking.js` | `createBooking` | `POST /bookings` |
| 社区列表 | `src/components/community/CommunityPage.vue` | `listCommunity` | `GET /community` |
| 活动报名 | `src/components/community/dialogs/CommunitySignupDialog.vue` | `joinEvent` | `POST /events/:id/signups` |
| 加入挑战 | `src/components/community/dialogs/CommunitySignupDialog.vue` | `joinChallenge` | `POST /challenges/current/join` |
| 首屏 ¥49 / 课程卡 / 页脚预约 | `StepHero`、`StepPrograms`、`StepFooter` | 无 | 本地选择 → `#offer`，不请求 |
| 门店展示 | `StepStations.vue` | 无 | 当前读静态 `venues` |
| 动态门店/课程/价格 | 待新增 boot loader | `listVenues` / `listPrograms` / `listPlans` | `GET /catalog/*`（可选） |

## 7. 端到端流程

### 7.1 预约主链路

1. 用户在 04 屏选择门店、课程、日期。
2. `StepOffer.vue` 的 watcher 触发 `loadAvailability()`，携带递增 token 防止旧响应覆盖新选择。
3. 服务端返回汇总余位与 `sessions[]`；前端自动选中第一个可约场次，用户可切换；`soldOut=true` 时按钮显示“该场已满 · 换一天”，不禁止用户改日期。
4. 用户填写姓名、手机号，页面本地校验（姓名非空、`/^1[3-9]\d{9}$/`、60 天窗口）。
5. 提交前生成 `Idempotency-Key`；请求体按 5.2 请求 B 发送（带 `sessionId`），固定 `autoConfirm: true`。
6. 成功：适配层解包 `booking`；页面展示 `orderNo`、门店、课程、日期、场次时间、金额，并把本地余位和所选场次余位各减 1。
7. 失败：
   - `SLOT_FULL` / `DUPLICATE_BOOKING`：保持弹窗关闭，刷新余位并展示错误文案；
   - `VALIDATION_ERROR`：按 `fields` 标注输入框；
   - `DATE_OUT_OF_RANGE`：定位到日期字段；
   - 网络错误：保留已填内容，允许用同一幂等键重试。
8. 用户关闭成功弹窗后清空表单（含 `sessionId`）和 `selection`，避免刷新后残留。

### 7.2 官网预约 → 小程序可见

验收路径：

1. 官网匿名提交预约，记录 `orderNo`；
2. 打开小程序，登录并绑定同一手机号；
3. 小程序「我的预约」或 `GET /member/bookings` 返回同一 `orderNo`；
4. 官网用 OTP 登录同一手机号，也应看到该订单；
5. 全程库存只占 1 个，不得出现两张有效预约。

这要求后端在游客预约时保留手机号索引，并在绑定手机号时做一次幂等认领。

### 7.3 社区报名

1. 社区页加载 `GET /community`；失败时保留内置数据，不阻断页面。
2. 弹窗打开时读取 `volt.web.session.v1`：登录会员展示会员身份，提交空对象由 token 归属；匿名用户才填写姓名 + 手机号。
3. 成功后用响应 `event` 覆盖当前卡片，写入 `membership.eventIds`（真实模式只在内存中）；会员 token 失效（401）时清会话并回退匿名表单。
4. 已报名/满员时按钮不可点；服务端仍要兜底返回 `DUPLICATE_SIGNUP` / `EVENT_FULL`。
5. 挑战加入是幂等操作，重复点击返回 `already: true`，页面只提示一次。

## 8. 字段差异速查

| 场景 | 当前前端字段 | 契约字段 | 处理 |
| --- | --- | --- | --- |
| 门店图片 | `venue.image` | `imageUrl`（`image` 仅兼容） | 真实模式优先用 `imageUrl`，缺省回退 `image` |
| 课程图片 | 无 | `image` / `imageUrl` | 动态 catalog 时补 |
| 社区活动场地 | `event.venue` | `venueName` | 适配层补别名 |
| 社区活动描述 | `event.desc` | `desc` | 后端必须返回，否则卡片描述为空 |
| 训练者故事 | `tag/sessions/streak/favorite` | 额外展示字段 | 契约允许为空，缺省时隐藏对应行 |
| 社区报名态 | 本地 `signedEventIds` | `membership.eventIds` + `event.mySigned` | 真实模式以服务端为准 |
| 挑战报名态 | 本地 `challengeJoined` | `membership.challengeJoined` + `challenge.joinedByMe` | 真实模式以服务端为准 |
| 预约单号 | `orderNo` | `orderNo` | 一致 |
| 预约响应 | 扁平对象 | `{ booking, session }` | 适配层解包并附加 `session` 摘要 |
| 余位 | `remaining` / `soldOut` | 同名 | 一致 |
| 预约场次 | `form.sessionId` | `sessionId` | 04 屏从 `sessions[]` 选择后提交；为空时后端自动选场 |
| 社区会员报名 | 有 token 时不传 name/phone | member 由 token 归属 | 弹窗展示会员身份，401 回退匿名表单 |
| 手机号 | 完整 `phone` | 展示用 `phone`（脱敏） | 不要依赖完整手机号做前端判断 |

## 9. 错误处理

官网相关的最终错误码：

| code | HTTP | 页面处理 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 按 `fields` 标注输入框，表单上方显示 `message` |
| `DATE_OUT_OF_RANGE` | 400 | 定位日期，提示 60 天窗口 |
| `NOT_FOUND` | 404 | 提示门店/课程/活动已下线，刷新 catalog 或社区数据 |
| `DUPLICATE_BOOKING` | 409 | 提示已约过，刷新余位，不重复提交 |
| `SLOT_FULL` | 409 | 提示换日期/门店，刷新余位；可选引导到小程序候补 |
| `EVENT_FULL` | 409 | 刷新社区列表，按钮置满 |
| `DUPLICATE_SIGNUP` | 409 | 标记已报名，刷新社区列表 |
| `UNAUTHORIZED` | 401 | 清除官网会话，打开登录；不要静默游客重试 |
| `RATE_LIMITED` | 429 | 提示稍后再试，禁用提交按钮若干秒 |
| `SERVER_ERROR` | 500 | 通用失败文案，保留表单 |
| `NETWORK_ERROR` | - | 本地网络错误，保留表单与幂等键，允许重试 |

适配层必须保证 `ApiError` 至少包含 `status`、`code`、`fields`、`message`；`requestId` 可记录到 console，便于和后端对账。

## 10. Mock 行为与回退

- Mock 只用于演示，不代表真实库存；两端 Mock 互不相通。
- 清空官网数据：

```js
localStorage.removeItem('volt.bookings.v1')
localStorage.removeItem('volt.event-signups.v1')
localStorage.removeItem('volt.challenge.v1')
localStorage.removeItem('volt.selection.v1')
```

- 切回 Mock：清空 `VITE_API_BASE` 或设 `VITE_USE_MOCK=true`，重启 dev server。
- 真实模式下不要再写入上述预约/报名键；`selection` 可以继续本地保存，它只是 UI 选择态。
- 成功弹窗当前文案“凭手机号到店核销”在真实后端下仍然成立，但不要再承诺短信通知（最终契约本期不做短信通道）。

## 11. 联调验收（官网）

`node tools/api-check.mjs` 会自动跑完下面 1–8、11 的接口层校验（56 项断言）；页面层按 9–12 人工或在浏览器里核对。

1. `VITE_USE_MOCK=false` + 正确 `VITE_API_BASE` 后，04 屏余位请求出现在 Network 面板，刷新后不读本地预约。
2. 提交预约返回 201，弹窗展示的 `orderNo`、门店、课程、日期、金额与后端一致。
3. 同一手机号再次提交同一场返回 409 `DUPLICATE_BOOKING`，页面不生成第二张单。
4. 并发或满员场次返回 409 `SLOT_FULL`，余位刷新后与后端一致。
5. 日期早于今天或超过 60 天，前端拦截；绕过前端时后端返回 `DATE_OUT_OF_RANGE`。
6. 官网预约后，小程序绑定同一手机号，`GET /member/bookings` 返回同一 `orderNo`。
7. 社区匿名加载正常；报名成功后 `signed + 1`，重复报名 `DUPLICATE_SIGNUP`，满员 `EVENT_FULL`。
8. 带 token 加载社区时返回 `membership`，已报名活动/挑战状态与小程序一致。
9. 模拟 token 过期，`/community` 或 `/member/*` 返回 401，页面清理会话并提示登录。
10. 模拟 429，页面禁用提交并提示，不产生重复订单。
11. CORS 预检通过，生产域名在允许列表内。
12. `npm run build` 后产物中的 API 地址正确，不包含任何密钥。

### 11.1 已验证记录（2026-11-01）

- 契约自检 56/56 通过（`tools/api-check.mjs`，Base URL `http://localhost:8080/api/v1`）；
- 浏览器实跑：04 屏余位来自后端（`余 10 位`）、提交预约 201 且弹窗显示后端 `orderNo`、余位随之减 1；社区页 `signed + 1`、按钮转「已报名」，挑战加入返回 `memberNo`；
- Network 记录：`availability 200 → bookings 201 → community 401 → community 200 → signups 200 → challenges 200`（401 是旧 token 触发的自愈重试，重载后不再出现）；
- 真实模式未写 `volt.bookings.v1` / `volt.event-signups.v1` / `volt.challenge.v1`；
- `npm run build` 与 `VITE_API_BASE=... npm run build` 均通过，产物含真实 API 地址且不含密钥；
- 种子数据固定满员的组合：`V-03` + `P06`（深度恢复），用于验证 `SLOT_FULL`。

### 11.2 本轮复验（2026-11-02）

- 契约自检 56/56 通过（`tools/api-check.mjs`）。
- 浏览器真实后端：游客选场次下单成功，弹窗显示 `10:00–10:45`；会员 OTP 登录后 3 个价格按钮锁定、按会员次卡下单成功；社区弹窗识别会员、不显示姓名/手机号、报名成功后卡片转「已报名」。
- 1440×900 与 390×844 无横向溢出，控制台 0 报错。

## 12. 本期不做

- 官网完整会员中心（历史档案/消息/资料编辑/取消改期）；
- 官网周课表与按教练筛选（小程序已有）；
- 微信支付、在线收款、退款、改期、转赠、优惠券；
- 短信通知；
- 官网候补。

## 13. 已确认项（2026-11-02）

本节原列的三个待确认项已由当前后端实现与 56 项契约自检确认：

- 列表响应按 `{ venues }` / `{ programs }` / `{ plans }` 返回，适配层解包；
- `POST /bookings` 同时支持带 `sessionId` 的精确锁场和无 `sessionId` 的兼容请求（后端选当日最早可约场次）；
- `fields` 由适配层映射为表单键，后端错误体保持 `{ code, message, fields, requestId }`。
| OTP 响应 | `{ token, member }` | token TTL、刷新机制、错误次数 |
| 游客订单认领 | 按手机号幂等合并 | 认领时机（OTP 登录 / 小程序绑定）与冲突处理 |
| 图片地址 | `imageUrl` 可为相对路径 | 生产 CDN 域名与跨域策略 |
