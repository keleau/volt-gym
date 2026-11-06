# VOLT 后端

官网和微信小程序共用的 Spring Boot 后端：目录、场次、锁位/确认/取消、候补、扫码核销、次卡流水、训练档案、消息和邀请。

- 技术栈：Spring Boot 3.5 / Java 17 / MyBatis 3.0 / MySQL 8
- 契约：[`../docs/shared-backend-api-final.md`](../docs/shared-backend-api-final.md)（唯一权威）
- 接口索引 / 服务职责 / 幂等键 / 扣次规则：[`../docs/api-index.md`](../docs/api-index.md)
- 官网接入：[`../docs/web-integration.md`](../docs/web-integration.md)
- 小程序接入：[`../docs/wx-integration.md`](../docs/wx-integration.md)

## 运行

```bash
# 第一次运行：复制并填写本地数据库
cp .env.example .env
mvn spring-boot:run          # http://localhost:8080
```

首次启动自动执行 `db/schema.sql`、`db/seed.sql`，并由 `VoltDataSeeder` 生成今天起 60 天的场次和演示会员。

`.env` 支持的键：

```dotenv
GYM_DB_HOST=localhost
GYM_DB_PORT=3306
GYM_DB_NAME=gym_management_system
GYM_DB_USER=root
GYM_DB_PASSWORD=
VOLT_EXPOSE_OTP=true          # 本地联调显示 devCode；生产关闭
WX_DEMO_FALLBACK=true         # 本地没有 AppSecret 时落到演示会员；生产关闭
WX_APP_ID=
WX_APP_SECRET=
VOLT_CORS_ORIGINS=http://localhost:[*],http://127.0.0.1:[*]
VOLT_STAFF_KEY=volt-staff-demo   # 官网 /staff 店员开卡页；生产换成强随机值
VOLT_DEMO_PAY=true               # 小程序演示支付并开卡；只改业务状态，生产必须 false
```

## 目录

| 路径 | 内容 |
| --- | --- |
| `controller/Volt*Controller` | 只做鉴权和参数透传 |
| `service/` | 官网通用业务接口；`impl/` 为对应实现 |
| `service/mini/` | 小程序页面域聚合：会员中心、档案、进场、课表 |
| `service/mini/VoltMiniSupport` | 共享查询、视图、消息、ID 工具 |
| `service/VoltViewService` | 数据库行 → 契约 JSON 视图 |
| `mapper/` + `resources/mapper/*.xml` | MyBatis 接口与 SQL |
| `pojo/` | 领域模型 |
| `seed/VoltDataSeeder` | 建表、灌静态目录、生成场次与演示数据 |
| `resources/db/` | `schema.sql`、`seed.sql`、`demo-reset.sql` |
| `common/` | 契约常量、统一错误体、异常处理 |

## 主要接口

Base URL `/api/v1`：

| Method | Path | 鉴权 |
| --- | --- | --- |
| GET | `/catalog/venues` \| `/catalog/programs` \| `/catalog/plans` \| `/catalog/coaches` | - |
| GET | `/catalog/schedule` \| `/sessions/{id}` | 可选 |
| GET | `/availability?venue=&program=&date=` | - |
| GET | `/catalog/sessions?date=&venueCode=&coachId=&programNo=&from=&to=` | - |
| POST | `/bookings` | 可选 |
| GET | `/community` | 可选 |
| POST | `/events/{id}/signups` \| `/challenges/current/join` | 可选 |
| POST | `/auth/otp/send` \| `/auth/otp/verify` \| `/auth/wx/login` | - |
| POST | `/auth/link-phone` | 是 |
| POST | `/card-orders` | 可选 |
| GET | `/member/card-orders` | 是 |
| POST | `/demo/card-orders/{orderNo}/pay` | 是（需 `VOLT_DEMO_PAY=true`） |
| GET | `/staff/card-orders`；`POST /staff/card-orders/{orderNo}/activate` | `X-Staff-Key` |
| GET | `/member/home` \| `/member/account` \| `/member/archive?month=` \| `/member/checkin` | 是 |
| GET | `/member/bookings` \| `/member/credits/ledger` \| `/member/messages` | 是 |
| PATCH | `/member/me` \| `/member/subscriptions` | 是 |
| POST | `/member/body-logs` \| `/member/messages/{id}/read` | 是 |
| POST | `/sessions/{id}/bookings` \| `/bookings/{id}/confirm` \| `/bookings/{id}/cancel` | 是 |
| POST | `/sessions/{id}/waitlist` \| `DELETE /waitlist/{id}` | 是 |
| POST | `/checkins/scan` \| `/checkins/free` | 是 |
| GET | `/invites/{ref}` \| `/invites/me`；`POST /invites/{ref}/accept` | 可选 / 是 |

写请求支持 `Idempotency-Key`；键按「会员/手机号 + 操作对象 + endpoint」哈希存储，不能跨会员或跨场次重放。错误体统一为 `{ code, message, fields, requestId }`。

## 数据与规则

- 预约窗口：今天起 60 天。
- 预约/确认不扣次；普通课时扫码核销扣 1 次并写 `volt_credit_ledger`；`PLAN-01` 首次体验课核销不扣次，同一手机号限一次。
- 锁位 10 分钟；后台任务每 60 秒释放过期锁位并按 FIFO 递补候补。
- `V-03` + `P06`（深度恢复）固定满员，用于验证 `SLOT_FULL`。
- 社区榜单/故事是运营文案，放在 `common/CommunityContent`。

重置演示数据：

```bash
mysql -u root -p gym_management_system < src/main/resources/db/demo-reset.sql
# 然后重启后端，VoltDataSeeder 会重新生成场次
```

## 自检

```bash
node ../tools/api-check.mjs http://localhost:8080/api/v1
```

自检会真实下单、报名、加入挑战，每跑一次会改变演示数据。

## 已知边界

- 在线支付、退款未接入；官网 `/card-orders` 只生成待开卡登记单，到店付款后由店员确认开卡。
- `VOLT_DEMO_PAY=true` 时小程序可「演示支付并开卡」，只改业务状态，不调用微信支付；生产必须 false。
- 会员账号免费，次卡是付费权益；开卡确认后在登录/刷新时按手机号领取次数。
- 短信通道未接；本地用 `VOLT_EXPOSE_OTP=true` 从响应取 `devCode`。
- 小程序 `/auth/wx/login` 在未配置 AppSecret 时是演示映射，生产必须接真实 `jscode2session`。
- 服务连续运行超过 60 天需要重启补齐场次。
- 门店后台撤销核销暂无入口；`volt_credit_ledger` 已预留 `REVOKE(+1)` 流水格式。
