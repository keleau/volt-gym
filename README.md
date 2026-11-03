# VOLT 电压健身 · 全栈 Demo

VOLT 是一个「官网预约 + 微信小程序会员端 + 共用后端」的健身品牌 Demo。核心链路是：官网/小程序查余位 → 锁位 → 确认预约 → 到店扫码核销 → 扣次并写入训练档案。

## 技术栈

| 部分 | 目录 | 技术 |
| --- | --- | --- |
| 后端 | [`backend/`](backend/) | Spring Boot 3.5 · Java 17 · MyBatis · MySQL 8 |
| 官网 | [`website/`](website/) | Vue 3 · Vite |
| 会员小程序 | [`miniprogram/`](miniprogram/) | 原生微信小程序 · Canvas 2D |
| 共享文档 | [`docs/`](docs/) | 接口契约、接入说明、历史归档 |
| 联调工具 | [`tools/api-check.mjs`](tools/api-check.mjs) | 56 项真实接口自检 |

## 快速启动

### 1. 后端（端口 8080）

```bash
cd backend
cp .env.example .env      # 第一次运行：填写本地 MySQL 账号密码
mvn spring-boot:run
```

首次启动会自动建库、建表并生成 60 天场次。`backend/.env` 已被 gitignore，不要提交真实密码。

本地没有配置微信 AppSecret 时，在 `.env` 设置 `WX_DEMO_FALLBACK=true`，小程序 `wx.login` 会落到演示会员 `MBR-DEMO`；生产必须保持 `false`。

### 2. 官网（端口 5173）

```bash
cd website
npm install
npm run dev
```

`website/.env.local` 指向 `http://127.0.0.1:8080/api/v1`。生产构建时把 `VITE_API_BASE` 改成正式域名，或留空使用同源 `/api/v1`。

### 3. 微信小程序

微信开发者工具导入 [`miniprogram/`](miniprogram/) 目录（不是仓库根目录），关闭「校验合法域名」，然后编译。真机联调把 [`miniprogram/config/index.js`](miniprogram/config/index.js) 的 `API_BASE` 改成电脑局域网 IP。

## 验证

```bash
# 后端接口契约（需先启动后端；会写入预约/报名数据，建议用临时库）
node tools/api-check.mjs http://localhost:8080/api/v1

# 官网生产构建
cd website && npm run build

# 小程序目录与 JS 语法
cd miniprogram && node --check services/api.js
```

当前验证结果：后端契约 **56/56 通过**；官网 `vite build` 通过；小程序 JS/JSON 静态检查通过。

## 目录约定

- `backend/src/main/java/.../controller`：只做鉴权与参数透传。
- `backend/src/main/java/.../service`：官网通用业务；`service/mini`：小程序页面域聚合；`service/mini/VoltMiniSupport`：共享查询/视图工具。
- `backend/src/main/resources/db`：幂等建表、种子数据、演示重置脚本。
- `website/src/services/api`：按 catalog/bookings/community/member 拆分的 API 门面；组件不直接写 `fetch`。
- `miniprogram/services/api.js`：页面唯一 API 门面；`miniprogram/utils/view.js`：共享展示映射。
- `docs/shared-backend-api-final.md` 是当前唯一权威契约；旧版文档和设计稿已删除。

## 已知边界

- 支付、退款、购卡下单未接入；`amount` 只作展示，会员次卡在核销时扣减。
- 短信通道未接入，本地通过 `VOLT_EXPOSE_OTP=true` 显示 `devCode`；生产应接真实短信。
- 微信登录未配置 AppSecret 时使用演示会员映射，不能用于生产。
- 官网没有完整会员中心（历史档案、消息、资料编辑在小程序端）。
- 小程序只连真实后端，不再提供离线 Mock 数据。
