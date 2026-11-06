# VOLT 后端接口索引

> 权威字段形状仍以 [shared-backend-api-final.md](shared-backend-api-final.md) 为准；本文补的是**入口索引、服务职责、幂等键和扣次规则**，方便面试时快速定位。
>
> Base URL：`http://localhost:8080/api/v1`  
> 代码入口：`backend/src/main/java/com/gym/gymmanagementsystem/controller/`

## 1. 通用约定

| 项目 | 约定 |
| --- | --- |
| 成功响应 | 直接返回各接口自己的 JSON 对象，不额外包 `{data: ...}`，保持官网与小程序现有契约不变 |
| 错误响应 | 统一 `{ code, message, fields, requestId }`，由 `GlobalExceptionHandler` + `dto/ApiErrorResponse` 输出 |
| 登录 | `Authorization: Bearer <token>`；无 token 可匿名查余位/社区，会员接口必须登录 |
| 幂等 | 写请求可带 `Idempotency-Key`；同一 key + endpoint 重试时返回首次结果 |
| 时间 | 日期 `yyyy-MM-dd`，时间 `HH:mm`，毫秒时间戳为 number |

常见错误码：`VALIDATION_ERROR`、`UNAUTHORIZED`、`NOT_FOUND`、`NO_CREDITS`、`PASS_EXPIRED`、`SLOT_FULL`、`LOCK_EXPIRED`、`BAD_STATE`、`CANCEL_WINDOW_CLOSED`、`HAS_SEAT`、`RATE_LIMITED`、`DUPLICATE_CARD_ORDER`、`CARD_ORDER_CLOSED`、`STAFF_DISABLED`、`DEMO_PAY_DISABLED`、`PHONE_ALREADY_BOUND`、`SERVER_ERROR`。

## 2. 服务职责

`VoltMiniServiceImpl` 已删除，按职责拆成四个服务；`VoltMiniSupport` 只放共享查询和格式化，不对外提供接口。

| 服务 | 职责 | 主要入口 |
| --- | --- | --- |
| `service/mini/VoltMemberCenterService` | 首页、我的、预约台账、消息、订阅、邀请、次卡流水 | `/member/home`、`/member/account`、`/member/bookings`、`/member/messages`、`/member/credits/ledger` |
| `service/mini/VoltArchiveService` | 训练档案、趋势数据、体重/身高记录 | `/member/archive`、`/member/body-logs` |
| `service/mini/VoltCheckinService` | 进场信息、扫码核销、自由训练；唯一扣次入口 | `/member/checkin`、`/checkins/scan`、`/checkins/free` |
| `service/mini/VoltScheduleService` | 课表、场次、锁位/确认/取消、候补、锁位超时任务 | `/catalog/schedule`、`/sessions/{id}`、`/sessions/{id}/bookings`、`/sessions/{id}/waitlist` |
| `service/mini/VoltMiniSupport` | 共享查询 / 视图 / ID / 格式化，无 HTTP 入口 | 被以上四个服务注入 |
| `service/VoltMemberServiceImpl` | 只保留会员资料修改 | `PATCH /member/me` |
| `service/VoltCardOrderServiceImpl` | 官网购卡登记、店员开卡、登录时领取已开卡权益 | `/card-orders`、`/staff/card-orders` |

## 3. 登录与目录

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/otp/send` | 否 | body `{phone}`；本地 `VOLT_EXPOSE_OTP=true` 时额外返回 `devCode` |
| POST | `/auth/phone/send` | 是 | 微信换绑/首次绑定手机号专用验证码，`purpose=bind`；已绑定账号返回 `PHONE_ALREADY_BOUND` |
| POST | `/auth/otp/verify` | 否 | body `{phone, code}`；返回 `{token, member}`，登录后认领同手机号的游客预约/报名/挑战 |
| POST | `/auth/wx/login` | 否 | body `{code, nickname?}`；小程序 wx.login 换会员 |
| POST | `/auth/link-phone` | 是 | body `{phone, code}`；先校验 `bind` 验证码，再绑定/合并手机号记录；返回 `{member, merged, token?, members, activeMemberId}` |
| GET | `/auth/members` | 是 | 当前微信名下会员档案列表 `{members, activeMemberId}` |
| POST | `/auth/members/add` | 是 | body `{phone, code}`；用 `login` 验证码添加另一手机号会员并切换 |
| POST | `/auth/switch-member` | 是 | body `{memberId}`；切换当前档案并换发 token |
| GET | `/catalog/venues` | 否 | 门店 |
| GET | `/catalog/programs` | 否 | 课程 |
| GET | `/catalog/plans` | 否 | 次卡价格 |
| GET | `/catalog/coaches` | 否 | 教练 |
| GET | `/availability?venue=&program=&date=` | 否 | 指定课程的余位查询 |
| GET | `/availability/trial?venue=&date=` | 否 | 体验课跨课程时段，不要求先选课程 |

## 4. 官网预约与社区

| 方法 | 路径 | 登录 | 幂等 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/bookings` | 是（体验课） | 是 | 官网体验课预约；OTP 会员 + `PLAN-01`，0 次卡允许，金额 ¥49；不可重复体验 |
| GET | `/community` | 否/可选 | — | 社区聚合 |
| POST | `/events/{eventId}/signups` | 否/可选 | 是 | 活动报名 |
| POST | `/challenges/current/join` | 否/可选 | 是 | 加入本周挑战 |
| POST | `/card-orders` | 否/可选 | 否 | 官网购卡登记；`PLAN-03` = 10 次 / 180 天，`PLAN-02` = 1 次 / 30 天；不收款，`status=pending` |
| GET | `/member/card-orders` | 是 | — | 当前会员的购卡登记列表 |
| GET | `/staff/card-orders?phone=&status=` | 店员密钥 | — | 店员查询待开卡登记；请求头 `X-Staff-Key` |
| POST | `/staff/card-orders/{orderNo}/activate` | 店员密钥 | 天然幂等 | 店员确认开卡；`activated`，会员下次登录/刷新时领取次数 |
| POST | `/demo/card-orders/{orderNo}/pay` | 是 | 天然幂等 | 小程序「演示支付并开卡」；仅 `VOLT_DEMO_PAY=true` 时可用，不调用微信支付 |

店员接口的密钥来自后端环境变量 `VOLT_STAFF_KEY`；未配置时返回 503 `STAFF_DISABLED`。官网 `/staff` 页面把密钥保存在 sessionStorage，不写入前端构建产物。

## 5. 小程序会员与档案

| 方法 | 路径 | 登录 | 幂等 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/catalog/schedule` | 可选 | — | 未来 7 天课表 + 我的预约/候补状态 |
| GET | `/sessions/{sessionId}` | 可选 | — | 单个场次详情 |
| GET | `/member/home` | 是 | — | 首页聚合：会员、次卡、本周、下一节、未读数、`cardOrders[]` |
| GET | `/member/account` | 是 | — | 我的页聚合；含 `cardOrders[]` |
| GET | `/member/bookings` | 是 | — | 预约台账：upcoming / waitlists / history / counts |
| GET | `/member/archive?month=YYYY-MM` | 是 | — | 档案与趋势图数据；`month` 可选，不传返回全部记录 |
| POST | `/member/body-logs` | 是 | — | 记录身高/体重，`weightKg` 保留一位小数 |
| GET | `/member/checkin` | 是 | — | 进场页：今日预约、今日记录、最近记录 |
| GET | `/member/messages` | 是 | — | 消息列表 + 未读数 |
| POST | `/member/messages/{messageId}/read` | 是 | — | `messageId=all` 时全部已读 |
| PATCH | `/member/subscriptions` | 是 | — | body `{key, value}`；key 为 `classReminder` / `classChange` / `waitlist` |
| GET | `/member/credits/ledger` | 是 | — | **次卡流水**，见 §7 |
| PATCH | `/member/me` | 是 | — | 修改昵称/目标/身高/体重 |
| POST | `/member/avatar` | 是 | — | 上传本地头像，返回最新 Member；文件存 `uploads/avatars` |
| GET | `/invites/{ref}?program=` | 可选 | — | 邀请页 |
| GET | `/invites/me` | 是 | — | 我的邀请码与邀请记录 |
| POST | `/invites/{ref}/accept` | 是 | 天然幂等 | 绑定邀请人；不能绑自己；重复领取返回 `already:true` |

邀请关系写在 `volt_invite`，`invitee_member_id` 唯一。小程序邀请页在登录后点击「领取体验课」时调用；成功后邀请记录会出现在「我的 → 邀请」。

## 6. 课表动作、候补与进场

| 方法 | 路径 | 登录 | 幂等 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/sessions/{sessionId}/bookings` | 是 | **是** | 需已绑定手机号；仅 60 天内场次；锁位 10 分钟，重复请求返回同一预约；不扣次 |
| POST | `/bookings/{bookingId}/confirm` | 是 | **是** | 条件更新 `locked → confirmed`；重复确认返回已确认状态；不扣次 |
| POST | `/bookings/{bookingId}/cancel` | 是 | 是 | 条件更新后释放座位并递补；开课前 2 小时内禁止取消；重复取消返回已取消状态 |
| POST | `/sessions/{sessionId}/waitlist` | 是 | 是 | 满员时加入候补；有座位会提示直接预约；行锁保证顺位 |
| DELETE | `/waitlist/{waitlistId}` | 是 | — | 取消候补 |
| POST | `/checkins/scan` | 是 | **是** | 场馆码核销；按 `booking_id` 判重；普通课时扣 1 次并写流水，`PLAN-01` 体验课不扣次 |
| POST | `/checkins/free` | 是 | 是 | 自由训练记录，不扣次 |

### 小程序幂等键

`miniprogram/services/api.js` 在真实模式下为以下请求生成 `Idempotency-Key`：

| 操作 | scope | 缓存身份 | 成功后 |
| --- | --- | --- | --- |
| 锁位 | `lock` | `sessionId` | 删除缓存键 |
| 确认 | `confirm` | `bookingId` | 删除缓存键 |
| 取消 | `cancel` | `bookingId` | 删除缓存键 |
| 候补 | `waitlist` | `sessionId` | 删除缓存键 |
| 扫码核销 | `scan` | `venueCode + 日期` | 删除缓存键 |
| 自由训练 | `free` | `venueCode + 日期` | 删除缓存键 |

规则：同一操作在 10 分钟内网络重试复用同一个 key；后端 `volt_idempotency` 命中后直接回放首次响应，避免重复锁位、重复扣次。业务成功或明确失败后清缓存，下一次操作换新 key。

## 7. 次卡流水与“为什么只扣一次”

### 扣次规则

| 阶段 | 是否扣次 | 原因 |
| --- | --- | --- |
| 锁位（`locked`） | 否 | 锁位只占座 10 分钟，超时自动释放；提前扣次会让用户白掉次数 |
| 确认（`confirmed`） | 否 | 确认只是把锁位转正；用户仍可在开课前 2 小时外取消 |
| 扫码核销（`checked-in`） | **普通课时扣 1 次；`PLAN-01` 体验课不扣** | 体验课是单独付费的单次权益；普通课时到店才消耗次卡 |
| 自由训练 | 否 | 自由训练不占用课程席位，也不消耗次卡 |
| 候补递补 | 否 | 递补只生成新的 10 分钟锁位，用户仍需确认和到场核销 |

### 只扣一次的实现

1. `VoltBookingMapper.markCheckedInIfConfirmed` 用条件更新 `WHERE status='confirmed'` 把预约改成 `checked-in`；并发扫码只有一条能成功。
2. 普通课时执行 `UPDATE volt_member SET credits = credits - 1 WHERE member_id=? AND credits > 0` 条件扣次；余额不足时抛 `NO_CREDITS`，整个事务回滚，核销状态也撤销。`PLAN-01` 体验课跳过扣次。
3. 普通课时写 `volt_credit_ledger`：`change_amount = -1`、`reason = 'CHECKIN'`、`balance_after` 为扣后余额；体验课不写扣次流水。唯一键 `(booking_id, reason)` 再兜底一次，同一预约不可能出现两条扣次流水。
4. `PLAN-01` 首次体验课按手机号限一次：创建预约时已有非取消的体验课单则返回 409 `TRIAL_ALREADY_BOOKED`。

### 什么时候恢复

- **取消预约、锁位超时都不会恢复**：因为这些阶段从未扣次，没有可退的次数。
- 只有核销被撤销（门店后台判定误核销）才需要返还：按同一 `booking_id` 追加一条 `change_amount = +1`、`reason = 'REVOKE'` 的流水。
- 当前产品没有自助撤销核销的入口；`REVOKE` 流水的字段和唯一键已预留，管理端接入时直接复用。

### `GET /member/credits/ledger`

响应 DTO：`dto/CreditLedgerView`

```json
{
  "entries": [
    {
      "id": "LED-202611020711",
      "bookingId": "BK-202611021239",
      "recordId": "REC-202611029712",
      "changeAmount": -1,
      "balanceAfter": 6,
      "reason": "CHECKIN",
      "note": "进场核销 · 燃脂单车",
      "createdAt": "2026-11-02T20:37:59"
    }
  ],
  "credits": 6
}
```

## 8. 候补超时任务

`VoltScheduleService.expireStaleLocks()`：

- `@EnableScheduling` 开启；启动后 30 秒首次执行，之后每 60 秒一次。
- 每批最多处理 100 条 `status='locked' AND lock_expires_at < now` 的预约。
- 条件更新为 `expired`，释放场次座位，然后 `promoteWaitlist` 按 `created_at` 先到先得取第一位候补。
- 递补成功后：候补状态改 `converted`，为候补会员生成新的 10 分钟锁位，推送站内消息；**不扣次**。
- 日志：`锁位超时释放 booking=... session=... promoted=...`。
- 读接口里的 `VoltMiniSupport.expireLocks(memberId)` 仍保留，作为用户访问时的即时兜底：释放该会员的过期锁位后同样调用 `promoteWaitlist`，保证用户打开页面就能看到最新顺位。

## 9. 本次重构验证

- `mvn -DskipTests compile` 通过（Java 17）。
- 仓库契约自检 `node tools/api-check.mjs`：56/56 通过。
- 锁位同 key 重放：返回同一 `bookingId`，场次余位只减 1。
- 确认同 key 重放：返回同一预约状态。
- 扫码同 key 重放 + 换 key 再扫：第二次返回 `already`，余额只扣 1，`volt_credit_ledger` 只有一条 `CHECKIN`。
- 造一条过期锁位 + 一位候补：60 秒内锁位变 `expired`、候补变 `converted`、生成新锁位，场次座位净额不变。
- 邀请绑定：首次返回 `{bound:true, already:false}`，重复返回 `already:true`，绑定自己返回 400；小程序邀请页真实调用并跳转预约页。
- 档案月份：`?month=2026-11` 返回过滤后的记录，`?month=bad` 返回 400 `VALIDATION_ERROR`。
- 小程序真实 E2E：锁位/确认/取消全部走新后端；邀请页在旧 token 失效时会清会话并重试公开接口，不再停留在错误页。
