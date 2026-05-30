# 11 待进一步审查方向（执行计划 / 交接单）

> **用途**：本文件是"重开对话继续审查"的交接单。新会话从这里开始即可，无需重新推导上下文。
> 已完成轮次见 [README](README.md) 与 01–10 分区文档；本文件只列**尚未覆盖**的方向 + 每块要查什么 + 产出去向。

## 如何用本文件执行

1. 读 [README](README.md)（已确认的 11 类系统级模式）+ 本文件。
2. 按下方优先级逐块审查，**只聚焦"可能藏新类 bug"的区域，不逐文件通读**纯 CRUD/DTO/枚举。
3. 每块发现记录到指定产出文档（沿用 blocker/major/minor 分级 + 文件:行 + 修复建议 + 对称性勾选）。
4. 对照"复用检查清单"逐条核对，避免重复劳动。

## 复用检查清单（每块都过一遍）

- 鉴权：非公开写接口是否有 `@PreAuthorize/@AccessControl`（B9/B10）；"当前用户"是否取自 `OperatorContext` 而非参数（M1/M18）。
- 验签：webhook/回调/触发器是否验签、是否 fail-open（B3/M5/M24）。
- 资金/配额：扣减是否加锁/幂等、是否接积分权益门控（M3/M4/M23）。
- 越权：按 id/key 操作是否校验归属（M18/M20/B11）。
- 注入：路径拼接（B8/B11）、JSON 手工拼接（m13）、SQL/向量查询、LLM 提示词注入。
- 实体外泄/Mass Assignment：实体作 `@RequestBody`/响应、敏感字段缺 `@JsonIgnore`（M15/M16/B7）。
- 占位/重复：TODO 假实现、同名重复类（B6/重复2/重复3）。
- 资源对称性：注册/注销、申请/释放、缓存写/失效、线程池关闭。

## 优先级 1 — 资金与文件（承接 B2/B3/B7/B11，最高）

| 范围 | 重点核查 | 产出 |
|------|---------|------|
| `framework/engine/settlement/`（DefaultSettlementEngine、MockPayChannelAdapter、channel/、Charge/Refund/Withdraw Request、PayResult） | 真实渠道验签、金额来源可信性、退款上限/重复退款幂等、MockPayChannelAdapter 生产隔离 | `11a-framework-settlement-storage.md` |
| `framework/storage/`（S3StorageService、LocalStorageService、FileService、ImageProcessor、StorageProperties） | 上传类型/大小校验、key 命名空间（防 B11 任意 key 覆盖）、本地存储路径穿越、presigned URL 归属、敏感凭证 | 同上 |

## 优先级 2 — 认证旁路 / 授权绕过

| 范围 | 重点核查 | 产出 |
|------|---------|------|
| `framework/security/oauth/`（Wechat/Wecom/Dingtalk OAuthClient、OAuthProperties、OAuthAutoConfiguration） | OAuth state/nonce 校验防 CSRF、code 兑换、token 存储、回调绑定到当前用户 | `11b-framework-auth-oauth-license.md` |
| `framework/security/license/`（LicenseLoader、LicenseAspect、PluginRegistry、PremiumRequired） | 许可证校验能否绕过、签名/有效期校验、`@PremiumRequired` 是否真生效（当前 0 使用） | 同上 |
| `framework/intelligent/core`（AssistantPermissionEvaluator、HumanApprovalService、token/、confidence/） | **B10 关键**：REST `/tools/invoke` 直调是否绕过权限门控；HITL 审批是否可绕过；置信度门控真实性 | `11c-framework-intelligent-core.md` |

## 优先级 3 — framework 级 REST 控制器（暴露面）

| 范围 | 重点核查 | 产出 |
|------|---------|------|
| `framework/engine/workflow/trigger/WebhookTriggerController` | 触发器鉴权 + 验签（同 B3/M24） | `11d-framework-controllers.md` |
| `framework/engine/dataprocess/table/DataTableController`、`DataIngestController` | 鉴权、ApiKey scope/allowedTables 是否在此真正强制（M9）、批量写入限额 | 同上 |
| `framework/engine/workflow/`（FlowableWorkflowEngine、WorkflowEngine、runtime/config/node/condition） | 流程定义注入、脚本节点是否走 ScriptSandbox（B5）、表达式求值安全 | 同上 |

## 优先级 4 — 数据/AI 注入面

| 范围 | 重点核查 | 产出 |
|------|---------|------|
| `framework/engine/dataprocess/`（AiEnricher、DataCleaner、DataRouter、DataPipeline、FieldMapper） | LLM 提示词注入、字段映射越权、外部数据清洗 | `11e-framework-data-ai.md` |
| `framework/intelligent/ai/`（chat ResilientChatService、image ImageServiceFactory/MidjourneyImageService、embedding、rerank、speech、omni、music、video、model3d） | 回调验签（M24）、配额/积分门控（M23）、密钥处理、SSE/WS 鉴权 | 同上 |
| `framework/engine/knowledge/`（KnowledgeVectorService、rag/graph/pipeline/chunker/importer/search/embedding） | 向量/图查询注入、导入路径/大小限制、租户隔离 | 同上 |

## 优先级 5 — 基础设施（正确性 > 安全）

| 范围 | 重点核查 | 产出 |
|------|---------|------|
| `framework/task/`（ScheduledTaskExecutor、TaskConsumer、RedisStreamTaskQueue、DistributedLockAspect、retry/） | 分布式锁正确性、消费幂等、DLQ、重试退避 | `11f-framework-infra.md` |
| `framework/messaging/`（MessageServiceImpl、sms/email/internal senders、模板引擎） | 模板注入、发送限流/成本（同 M21）、渠道路由 | 同上 |
| `framework/engine/cache/`（TwoLevelCache、ConfigCacheManager、CacheInvalidation*） | 缓存写/失效对称、序列化、穿透/雪崩 | 同上 |
| `framework/engine/{memory,checkpoint,budget,metadata,monitor,meta}`、`intelligent/{agent,assistant,team,cognition}` | 编排正确性、检查点一致性、占位/重复（CognitiveCycleExecutor、TaskBoard、TeamOrchestrator、AgentScheduler、agentscope/） | 同上 |
| `framework/sequence/`、`framework/logging/`、`flyway/`、`spring/`、`protection/` | 序列号并发、审计/操作日志敏感数据、Flyway clean 生产隔离 | 同上 |

## API 层剩余未逐文件读（预期复现已知模式，低优先）

> 这些大概率只是 B9/M15/实体外泄的新实例，按矩阵批量修即可，不必单独深审。

- `module/ai/{model,output,team,a2a,context,memory,role,skill}`、`aigc/{video,media,voice,model3d,omni,history,batch}`
- `module/system/{notify,dict,config,mail,dashboard,org,task,menu,entity,workflow/*,log/*}`
- `module/{knowledge/segment,knowledge/problem,document}`、`autodev/{doc,agent KiroAgentController,PullRequestService}`
- `module/examples/*`（示例，建议移出生产而非审查）

## 收尾项（审查完成后）

- 汇总各 `11x-*.md` 到 README 严重级别表，更新覆盖进度为"全覆盖"。
- 评估是否将优先级 1–3 的 blocker 并入正式修复任务（🔴 需人类审核）。
- 关联现有未运行项：`ControllerAuthorizationTest` 待 `pnpm nx test service` 首跑生成基线（见 10 + README）。
