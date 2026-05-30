# 10 全 Controller 鉴权矩阵（B9 修复工单）

> 把 B9「系统性缺失方法级鉴权」从抽样结论变成逐条可修复工单。
> 数据来源：对 `apps/service` 全量扫描 `@RestController` / `@PreAuthorize` / `@AccessControl` / `@Entitlement` / `@PremiumRequired` / `@Secured`（2026-05-30）。

## 扫描结果（事实）

| 指标 | 数量 |
|------|------|
| `@RestController` 总数 | 117（含约 6 个同名重复类） |
| 有任意 `@PreAuthorize` 的控制器 | **5** |
| `@AccessControl` 使用 | **0**（注解已定义但全项目未用） |
| `@Entitlement` 使用 | **0** |
| `@PremiumRequired` 使用 | **0** |
| `@Secured` / `@RolesAllowed` 使用 | **0** |

**结论**：除全局 `SecurityConfig` 的"非白名单一律 authenticated"基线外，几乎所有接口**没有方法级授权**。任何登录用户（或持 API Key / Mock Token 者）可调用绝大多数管理与跨用户接口。

### 已有方法级鉴权的 5 个控制器

| 控制器 | 注解 | 评价 |
|--------|------|------|
| `framework/security/apikey/ApiKeyController` | `hasRole('ADMIN')` ×3 | ✅ 真实角色门控 |
| `system/log/LogLevelController` | `hasRole('ADMIN')` ×2 | ✅ 真实角色门控 |
| `system/dict/DictTypeController` | `isAuthenticated()` ×3 | ⚠️ 与全局基线等价，写操作仍无角色门控 |
| `system/dict/DictDataController` | `isAuthenticated()` ×3 | ⚠️ 同上 |
| `system/menu/MenuController` | `isAuthenticated()` ×5 | ⚠️ 同上 |

> 即真正做了角色限制的只有 2 个控制器；其余 112+ 全靠"登录即放行"。

## 修复优先级分级

按"接口能造成的损害"分级，给出每类建议的最小鉴权要求。`SELF` = 仅本人数据（服务端按 `OperatorContext` 过滤，禁止以参数传 userId）。

### P0 — 资金 / 账号 / 运维（必须 ADMIN 或严格 SELF + 验签）

| 控制器 | 建议 | 关联 |
|--------|------|------|
| `system/user/UserController`（reset/delete/status/create/import/export） | ADMIN | B9 |
| `system/user/UserProfileController` | SELF | M1 |
| `system/role/RoleController`、`role/PermissionController`、`permission/PermissionController`、`role/DataAccessRuleController`、`role/policy/AccessPolicyController`、`role/relation/ResourceRelationController` | ADMIN | 重复2 |
| `system/org/OrganizationController` | ADMIN | B1 |
| `pay/PayOrderController`、`CreditController`、`CreditTokenRuleController`、`RefundController`、`ReconcileController`、`BizOrderController` | SELF 查询 / ADMIN 配置 / 回调验签 | B2/B3/M1/M2 |
| `billing/SubscriptionController`、`EntitlementController`、`LevelController`、`BillingController` | SELF / ADMIN 配置 | M2 |
| `autodev/git/GitController`（commit/push/PR/CI/deploy）、`KiroAgentController`、`codegen/CodegenController`、`doc/AutodevDocController` | ADMIN + webhook 验签 | B4/B8 |
| `system/file/FileController`、`FileConfigController` | SELF（归属校验）+ ADMIN 配置 | B11/M20 |
| `tool/ToolController`（invoke/generate/confirm/share/source/delete） | ADMIN + 复用工具权限门控 | B10 |
| `framework/.../apikey/ApiKeyController` | 已 ADMIN ✅ | — |

### P1 — 跨用户数据 / 计费型 / 配置（SELF 或 ADMIN + 配额）

| 控制器 | 建议 | 关联 |
|--------|------|------|
| `ai/chat/*`（ChatController、ChatRunController、ChatTaskController、AgUiChatController、TaskEventController） | SELF（校验 session/message 归属） | M18 |
| `ai/aigc/image/*`（Image/BatchGeneration/GenerationTemplate）、`video/*`、`model3d`、`voice`、`media/*`、`history`、`omni` | SELF + 积分/权益门控 + 回调验签 | M23/M24/m16 |
| `ai/agent/*`、`ai/assistant/*`（含重复类）、`ai/role/*`、`ai/skill`、`ai/model`、`ai/memory`、`ai/output`、`ai/context`、`ai/team`、`ai/a2a` | SELF / ADMIN（注册类） | 重复3 |
| `stats/StatsController`、`ui/tracking/TrackingController`、`ui/aiui/AiuiController` | ADMIN + 租户过滤 | M19/B1 |
| `channel/ChannelController`（密钥配置） | ADMIN | B7 |
| `system/sms/SmsController`（test-send/模板） | ADMIN + 限流 | M21/M22 |
| `system/mail/*`（MailTemplate/MailLog/MailAccount） | ADMIN | — |
| `system/config/SystemConfigController` | ADMIN | — |
| `system/entity/*`（EntityDef/GenericEntity/CustomField/RecordTemplate）、`system/dashboard/*`（PageDef/Dashboard） | ADMIN / SELF | 元数据驱动需谨慎 |
| `system/task/*`（Task/TaskManagement/ScheduledTask/Todo） | SELF / ADMIN（调度） | — |
| `system/workflow/*`（Workflow/Trash/Delegation/Automation/Archive/Approval/Visualization/AgUi）、`framework/.../workflow/trigger/WebhookTriggerController` | SELF / ADMIN + 触发器验签 | — |
| `system/notify/*`、`livechat/*`（Ticket/Livechat）、`customerservice/WecomKfBinding` | SELF / 坐席角色 | M14 |
| `knowledge/*`、`document/DocumentController`、`framework/.../dataprocess/table/DataTable+DataIngest` | SELF + 租户 | M13 |
| `company/CompanyController` | SELF / ADMIN + 改 DTO | M15 |

### P2 — 公开 / 低风险（确认白名单，余下保持 authenticated）

| 控制器 | 处理 |
|--------|------|
| `system/auth/AuthController`、`CaptchaController` | 公开（已在白名单） |
| `customerservice/WecomKfCallbackController`、`sms` 回调、各 webhook/notify 回调 | **加入白名单 + 自身验签**（当前要么打不通要么裸奔，见 M10/B3） |
| `system/HelloController`、`module/examples/*`（Movie/Image/AgentScope 示例） | 示例代码：移出生产构建或限 dev |
| `system/log/*` 查询、`ai/aigc/history` 查询 | SELF / ADMIN |

## 落地建议（最小改动路径）

1. **先建默认拒绝基线**：把 `@AccessControl`（已存在但 0 使用）或 `@PreAuthorize` 作为强制项，CI 加 ArchUnit 规则——非白名单 `@RestController` 的写方法（`@PostMapping/@PutMapping/@DeleteMapping`）必须带授权注解，否则编译期/测试期失败。
2. **批量按上表打注解**：P0 先行（资金/账号/运维），P1 次之。
3. **消除 userId 入参**：所有"当前用户"语义改 `OperatorContext`，配合 SELF 归属校验（见 M1/M18/M20）。
4. **回调统一验签**：webhook/notify/pay/sms 回调加入白名单 + 各自签名校验（B3/M5/M24/M10）。
5. **去重**：删除重复控制器/服务（PermissionController/Service、AssistantController/Service、PermissionCacheService），避免两套接口鉴权不一致。

> 本矩阵是 [README](README.md) 中 B9/B10 的可执行化拆解。建议作为独立修复任务（🔴 高风险，需人类审核鉴权策略后实施）。
