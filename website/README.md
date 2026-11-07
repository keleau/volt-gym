# VOLT 官网前端

Vue 3 + Vite 的整屏单页站点，主题为「电流 / 充电」。官网与微信小程序共用同一个 Spring Boot 后端和 MySQL 库。

## 运行

```bash
npm install
npm run dev      # http://localhost:5173
npm run build
npm run preview
```

环境变量（`.env.local`）：

```dotenv
VITE_API_BASE=http://127.0.0.1:8080/api/v1
```

留空 `VITE_API_BASE` 时默认使用同源 `/api/v1`（适合 Nginx 反向代理）。官网只连真实后端

## 页面

站内用 hash 切换，可直接访问：

| hash | 内容 |
| --- | --- |
| `#top` | 01 首页：品牌短片、会员入口、课程概览 |
| `#stations` | 02 三处充电站 |
| `#programs` | 03 五门电流课 |
| `#offer` | 04 电价 + 预约表单（日期 → 场次 → 锁位） |
| `#footer` | 05 会员小程序宣传与门店信息 |
| `#community` | 社区页：战绩榜、本周挑战、教练故事、活动报名、门店画廊 |

支持从门店 / 课程 / 页脚 / 社区带入门店、课程或价格预设到预约表单；选择状态存 `localStorage`。

## 结构

| 路径 | 作用 |
| --- | --- |
| `src/components/volt/steps/` | 5 屏骨架与各屏入口 |
| `src/components/volt/offer/` | 价格列表、预约表单、场次选择、成功弹窗 |
| `src/components/volt/member/` | OTP 会员面板与会员摘要 |
| `src/components/community/` | 社区页外壳；`sections/` 六个区块，`dialogs/` 报名弹窗 |
| `src/components/ui/BaseModal.vue` | 统一弹窗：焦点陷阱、Escape、滚动锁、焦点恢复 |
| `src/composables/useBooking.js` | 预约状态、校验、余位、幂等提交 |
| `src/composables/useCooldown.js` | 验证码/限流倒计时 |
| `src/services/api/` | 按 catalog / bookings / community / member / http 拆分的 API 门面 |
| `src/services/selection.js` | 跨屏选择（门店/课程/方案）持久化 |
| `src/utils/phone.js` / `member.js` | 手机号与次卡状态共享校验 |

## 数据与接口

- API 门面：[`src/services/api/index.js`](src/services/api/index.js)
- 跨屏选择：[`src/services/selection.js`](src/services/selection.js)
- 共用后端契约：[`../docs/shared-backend-api-final.md`](../docs/shared-backend-api-final.md)
- 接口索引：[`../docs/api-index.md`](../docs/api-index.md)
- 官网接入：[`../docs/web-integration.md`](../docs/web-integration.md)
- 后端实现：[`../backend/`](../backend/)
- 联调自检：`node ../tools/api-check.mjs`（对着真实后端跑 56 项契约验收）

## 当前范围

已实现：手机号 OTP 登录（token 存 `volt.web.session.v1`）、会员认领、官网预约绑定会员、次卡余额与下一节预约展示；按门店/课程/日期查余位并选择具体场次、表单校验、重复预约与满员处理、预约成功弹窗；社区活动报名与挑战加入；小程序名称复制、门店/课程/价格跨屏预设、hash 直达。

未实现：在线支付、真实短信通道（本地显示联调验证码）、官网取消/改期与完整会员中心、官网周课表/按教练筛选、管理后台。
