# 权限注解审计清单

> 生成口径：排除 `SecurityConfig.PUBLIC_PATHS`，排除继承 `BaseCrudController` 的通用 CRUD，列出 Controller 文件内没有 `@PreAuthorize` / `@Secured` / `@RolesAllowed` 的非公开 Controller。
>
> 当前全局安全配置有 `anyRequest().authenticated()` 兜底，因此本清单表示缺少细粒度权限，不表示匿名开放。

## P0 管理、配置、高风险写操作

优先补 `ADMIN` 或明确权限码。

| Controller | 路径 | 建议 |
|------------|------|------|
| `DataIngestController` | `/api/v1/ingest` | 数据接入管理权限 |
| `DataTableController` | `/api/v1/data-tables` | 数据表管理权限 |
| `WebhookTriggerController` | `/api/webhook/trigger` | Webhook 签名或触发权限 |
| `ChannelController` | `/api/channel` | 渠道管理权限；回调入口单独处理 |
| `SystemConfigController` | `/api/system/configs` | 系统配置管理权限 |
| `EntityDefController` | `/api/entity-defs` | 实体定义管理权限 |
| `CustomFieldController` | `/api/entity-defs/{slug}/fields` | 实体字段管理权限 |
| `GenericEntityController` | `/api/data/{slug}` | 按实体动态权限 |
| `RecordTemplateController` | `/api/system/record-templates` | 模板管理权限 |
| `MailAccountController` | `/api/system/mail/accounts` | 邮件账号管理权限 |
| `MailTemplateController` | `/api/system/mail/templates` | 邮件模板管理权限 |
| `SmsController` | `/api/system/sms` | 短信管理权限；回调入口单独处理 |
| `ScheduledTaskController` | `/api/admin/scheduled-tasks` | 管理员权限 |
| `TaskManagementController` | `/api/tasks` | 任务管理权限 |
| `AuditLogController` | `/api/admin/audit-log` | 审计日志查看权限 |
| `LoginLogController` | `/api/system/login-logs` | 登录日志查看权限 |
| `OperationLogController` | `/api/operation-logs` | 操作日志查看权限 |
| `OfficialConsoleController` | `/api/official/console` | 官方控制台管理员权限 |
| `LicenseController` | `/api/license` | 许可证管理权限 |
| `PermissionController` | `/api/permissions` | 权限管理权限 |

## P1 业务数据、用户资源

至少补 `isAuthenticated()`，写操作再细分 owner/admin。

| 模块 | Controller |
|------|------------|
| 知识库 | `KnowledgeBaseController`, `KnowledgeSegmentController`, `ProblemController` |
| 文档 | `DocumentController` |
| 企业运营 | `CompanyController` |
| 客服 | `LivechatController`, `TicketController`, `WecomKfBindingController` |
| 计费 | `CreditController`, `BillingController`, `LevelController` |
| 通知 | `NotificationController`, `NotificationPreferenceController`, `MessageTemplateController`, `NoticeController`, `SubscriptionController` |
| 日志协作 | `ActivityController`, `CommentController`, `RecordVersionController` |
| 仪表盘 | `DashboardController`, `PageDefController` |
| 任务 | `TaskController`, `TodoController` |
| 搜索 | `SearchController` |

## P1 AI、AIGC、消耗型能力

涉及模型调用、资产、历史、额度，建议按登录用户和额度权限细化。

| 模块 | Controller |
|------|------------|
| 智能体与协议 | `A2AController`, `AafAguiConfirmController`, `AssistantController`, `TeamController` |
| AI 配置与上下文 | `AiModelController`, `MemoryController`, `ContextController`, `AiOutputController` |
| Chat | `ChatController`, `ChatRunController`, `ChatTaskController`, `TaskEventController` |
| 图像生成 | `ImageController`, `BatchGenerationController`, `GenerationTemplateController`, `GenerationHistoryController` |
| 媒体资产 | `MediaAssetController`, `MediaCategoryController`, `MediaTagController` |
| 视频、语音、3D | `Model3dController`, `VideoGenerationController`, `VideoTemplateController`, `VoiceController` |

## P2 开发者、示例、调试类

按是否生产启用决定；生产建议禁用或加管理员权限。

| 模块 | Controller |
|------|------------|
| 开发者 | `DeveloperAccountController`, `DeveloperApiKeyController`, `DeveloperProxyController`, `DeveloperSubscriptionController`, `DeveloperTokenController` |
| 示例 | `AgentScopeExampleController`, `ImageExampleController`, `MovieRestController`, `MovieGraphQlController` |
| UI/统计 | `AiuiController`, `TrackingController`, `StatsController` |

## 特殊入口

不要批量加统一权限，需要按协议单独设计。

| Controller | 说明 |
|------------|------|
| `WecomKfCallbackController` | 回调入口，可能不能要求登录，应做签名校验。 |
| `CaptchaController` | 验证码通常应公开；当前路径不在 `PUBLIC_PATHS`，需确认设计。 |
| `HelloController` | `/api/hello` 已公开，但类路径是 `/api`，需人工看具体方法。 |
| `ArchiveController` | 审批流/工作流相关，按本人任务、管理员、流程管理员拆权限。 |
| `AutomationController` | 审批流/工作流相关，按本人任务、管理员、流程管理员拆权限。 |
| `DelegationController` | 审批流/工作流相关，按本人任务、管理员、流程管理员拆权限。 |
| `TrashController` | 审批流/工作流相关，按本人任务、管理员、流程管理员拆权限。 |
| `WorkflowVisualizationController` | 审批流/工作流相关，按本人任务、管理员、流程管理员拆权限。 |
| `WorkflowAgUiController` | 审批流/工作流相关，按本人任务、管理员、流程管理员拆权限。 |
| `ApprovalController` | 审批流/工作流相关，按本人任务、管理员、流程管理员拆权限。 |

## 建议处理顺序

- 先补 P0 管理、配置、高风险写操作。
- 再补 AI/AIGC 消耗型接口，避免登录用户越权消耗额度或访问他人资产。
- 再补业务数据接口，细分本人、组织管理员、系统管理员。
- 回调、验证码、Webhook 单独审计，不跟普通业务接口一起批量加 `@PreAuthorize`。
