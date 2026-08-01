# 10 全 Controller 鉴权矩阵（B9 收口工单）

> 将 B9 从历史扫描结论更新为当前剩余授权风险矩阵。
> 2026-05-30 的全量扫描结果仅作为冻结基线，不再代表当前代码现状。

## 当前判断

方法级鉴权已大面积补齐，历史“几乎全部无方法级授权”的结论不再成立；但冻结基线**尚未清零**，不能把“存在授权注解”等同于授权正确，也不能声称授权风险已完全消除。

当前核心集中在三个维度：

| 维度 | 当前风险 | 收口标准 |
|------|---------|---------|
| 角色范围 | `ADMIN`/`ORG_ADMIN` 等角色使用过宽，平台与组织管理边界混淆 | 区分平台管理员、组织管理员和业务角色，组织角色不得跨 org |
| SELF 归属 | 仅校验登录或角色，未验证 session/tool/image 等具体资源归属 | service 层按当前 Actor 校验 owner/member/share scope，拒绝仅凭 id 访问 |
| org 过滤 | 管理与统计查询缺少强制组织条件 | 查询入口统一注入当前 org，跨组织访问显式授权并审计 |

> 下列矩阵是**剩余风险工单**，不是无鉴权控制器清单；未列出不代表已自动证明安全。

## 修复优先级分级

`SELF` = 仅本人或本人有权访问的资源；服务端从 `OperatorContext` 获取身份并执行资源归属校验，禁止信任客户端传入的 userId/orgId。

### P0 — 资金 / 账号 / 运维

| 控制器 | 当前最小授权要求 | 关联 |
|--------|------------------|------|
| `system/user/UserController`（reset/delete/status/create/import/export） | 平台 ADMIN；组织内操作限定当前 org，禁止 `ORG_ADMIN` 全局化 | B9 |
| `system/user/UserProfileController` | SELF | M1 |
| `system/role/RoleController`、`role/PermissionController`、`permission/PermissionController`、`role/DataAccessRuleController`、`role/policy/AccessPolicyController`、`role/relation/ResourceRelationController` | 平台权限与组织权限分层；合并重复权限服务 | 重复2 |
| `system/org/OrganizationController` | 平台 ADMIN；组织管理员仅管理当前 org | B1 |
| `pay/PayOrderController`、`CreditController`、`CreditTokenRuleController`、`RefundController`、`ReconcileController`、`BizOrderController` | SELF 查询 / 平台 ADMIN 配置 / 回调验签 | M1 |
| `billing/SubscriptionController`、`EntitlementController`、`LevelController`、`BillingController` | SELF / 平台 ADMIN 配置；组织套餐按 org 过滤 | — |
| `autodev/git/GitController`（commit/push/PR/CI/deploy）、`KiroAgentController`、`codegen/CodegenController`、`doc/AutodevDocController` | 平台 ADMIN + 目标仓库/工作区资源授权 + webhook 验签 | B4/B8 |
| `tool/ToolController#viewSource`、工具列表端点 | 按 owner/org/share scope 做资源级授权与过滤 | B10 |
| `framework/.../apikey/ApiKeyController` | 平台 ADMIN，并校验 key 所属组织/工作区 | — |

### P1 — 跨用户数据 / 计费型 / 配置

| 控制器 | 当前最小授权要求 | 关联 |
|--------|------------------|------|
| `ai/chat/*`（ChatController、ChatRunController、ChatTaskController、AgUiChatController、TaskEventController） | SELF，校验 session/message 归属 | M18 已修复（ChatService 统一归属校验）；其余 chat 控制器待复核 |
| `ai/aigc/image/*`（Image/BatchGeneration/GenerationTemplate）、`video/*`、`model3d`、`voice`、`media/*`、`history`、`omni` | SELF + 统一任务主链权益门控；查询校验资源归属 | M23/m16 |
| `ai/agent/*`、`ai/assistant/*`、`ai/role/*`、`ai/skill`、`ai/model`、`ai/memory`、`ai/output`、`ai/context`、`ai/team`、`ai/a2a` | SELF；注册与全局配置限平台 ADMIN；组织资源按 org 过滤 | — |
| `stats/StatsController`、`ui/tracking/TrackingController`、`ui/aiui/AiuiController` | `ORG_ADMIN` 仅当前 org；平台跨组织查询需显式权限 | M19 已修复（stats/tracking 强制 org 过滤）；`AiuiController` 待复核 / B1 |
| `channel/ChannelController`（密钥配置） | 平台 ADMIN 或当前组织管理员，并限定当前 org | B7 |
| `system/sms/SmsController`（test-send/模板） | 测试发送做生产环境隔离与号码白名单；模板经 service 返回 VO | M21/M22 |
| `system/mail/*`（MailTemplate/MailLog/MailAccount） | 平台 ADMIN 或当前组织管理员；账号与日志按 org 过滤 | — |
| `system/config/SystemConfigController` | 平台 ADMIN；组织配置使用独立权限与 org 过滤 | — |
| `system/entity/*`（EntityDef/GenericEntity/CustomField/RecordTemplate）、`system/dashboard/*`（PageDef/Dashboard） | ADMIN / SELF，并校验元数据与页面所属 org | 元数据驱动需谨慎 |
| `system/task/*`（Task/TaskManagement/ScheduledTask/Todo） | SELF；调度配置限管理员且按 org 隔离 | — |
| `system/workflow/*`（Workflow/Trash/Delegation/Automation/Archive/Approval/Visualization/AgUi）、`framework/.../workflow/trigger/WebhookTriggerController` | SELF / 组织管理员；流程实例、委托与触发器按 org/owner 校验 | — |
| `system/notify/*`、`livechat/*`（Ticket/Livechat）、`customerservice/WecomKfBinding` | SELF / 坐席角色，并校验会话或工单归属 | — |
| `knowledge/*`、`document/DocumentController` | SELF + org 过滤 | — |
| `company/CompanyController` | SELF / 组织角色 + org 过滤；请求改 DTO | 请求体已为 DTO；SELF/org 过滤待补 |

### P2 — 公开 / 低风险

| 控制器 | 处理 |
|--------|------|
| `system/auth/AuthController`、`CaptchaController` | 仅明确的认证入口公开，其余方法逐项确认 |
| `customerservice/WecomKfCallbackController`、`sms` 回调、各 webhook/notify 回调 | 加入必要白名单，同时按各自协议验签、抗重放并记录审计 |
| `system/HelloController`、`module/examples/*`（Movie/Image/AgentScope 示例） | 移出生产构建或仅在 dev profile 启用 |
| `system/log/*` 查询、`ai/aigc/history` 查询 | SELF / 对应管理角色，并按 org/资源归属过滤 |

## 落地建议（最小改动路径）

1. **冻结并持续对比基线**：保留历史扫描作为回归基线，CI 检查新增 Controller 的授权声明，但不再用注解数量代表完成度。
2. **先收紧角色范围**：区分平台 ADMIN、`ORG_ADMIN` 与业务角色，逐项移除组织角色的全局数据访问能力。
3. **补齐 SELF 资源授权**：当前用户语义统一取 `OperatorContext`，在 service 层校验 owner/member/share scope。
4. **强制 org 过滤**：管理、统计、知识、文档、工作流等查询统一追加当前 org 条件；跨组织访问需显式权限与审计。
5. **统一回调安全**：webhook/notify/pay/sms 回调按协议验签、抗重放并与白名单配置配套。
6. **保留未闭合去重项**：合并 `PermissionController/PermissionService` 的重复实现，避免两套授权策略继续分叉（重复2）。

> 本矩阵是 [README](README.md) 中 B9/B10 的剩余授权收口清单。它不再沿用过时数量统计，也不表示当前问题已全部清零；每一项需以角色边界、SELF 归属和 org 过滤三类证据验收。
