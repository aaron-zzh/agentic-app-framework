# 开发记录

- ✅ #02006-#02008 AAF-020 前端实现：文档页面重构（ResizablePanel+分组树+Markdown渲染+SSE通知）+ Kiro Agent 聊天抽屉 — 2026-05-22

- ✅ #02001-#02005 AAF-020 后端实现：Flyway V4 迁移 + 文档新建接口 + SSE 变更通知 + KiroAgentController（ProcessBuilder 调用 kiro-cli）+ 辅助接口 — 2026-05-22

- ✅ 开发者商业化管理：新增开发者账户、订阅、Token 池、兑换码、API Key、子代理管理；模型网关执行层因合规要求仅预留设计 — 2026-05-31
- ✅ 开源授权订阅入口 — 增加 license 状态接口，并在前端侧边栏接入 Free/Pro 标记与订阅弹层（2026-05-31）
- ✅ 开源授权入口收口 — `/api/license/current` 返回官方 `upgradeUrl`，前端订阅弹层改为打开官方入口而非调用本地 developer 订阅（2026-05-31）
- ✅ 官方服务 owner 入口 — license 增加 `owner` 标记，前端仅 owner 可见客户门户/运营管理，后端 owner-only 接口接入拦截（2026-05-31）
- ✅ 官方 license 签发 — owner 控制台支持设置 `owner` 并生成 `license.jwt`，私钥由官方服务配置注入（2026-05-31）
- ✅ License 逻辑耦合 — 官方 user_id 增加格式校验与 coupling seed，非法 subject 会导致高级能力降级（2026-05-31）
- ✅ License feature 门控 — 签发/读取 `features`，新增 `@FeatureRequired`，developer 模块与源码下载接入高级能力码（2026-05-31）
- ✅ License 高级模块白名单 — `features` 仅允许登记的商业模块码，并开放 `/api/license/source-code` 支持客户实例按授权下载源码包（2026-05-31）
- ✅ 商业权限演示模块 — `@PremiumRequired` 支持类级门控，developer 高级接口接入 Premium 授权拦截（2026-05-31）
- ✅ Developer CRUD 示例 — 开发者订阅套餐后台继承 `BaseCrudController`，接入 `_query/_meta/_options` 与权限码（2026-05-31）
- ✅ 开发者订阅调整 — 增加管理员为指定用户开通/调整开发者订阅接口（2026-05-31）
