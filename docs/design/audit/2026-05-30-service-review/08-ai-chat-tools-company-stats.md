# 08 AI 对话 · 任务 · 企业运营 · 统计

> 覆盖：ai/chat 会话与流式、持久任务执行、company 编排、stats 行为分析、prompt 引擎。

## 问题清单（本轮已全部修复，见下方修复记录）

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| M18 | 🟠 | `ai/chat/controller/ChatController`（listMessages/getMessagesPaged/deleteSession/renameSession/archiveSession/streamChat/messageFeedback） | 这些操作以 `sessionId`/`messageId` 取自路径但**未校验归属当前用户**→对象级 IDOR：可读/删/改他人会话与消息 | 在 ChatService 内校验 session.userId == currentUserId，否则 404/403 |
| M19 | 🟠 | `stats/StatsController`、`ui/tracking/TrackingController` | 统计与追踪端点以 `ORG_ADMIN` 作为全局权限使用，查询未按当前 org 过滤，组织管理员可读取跨组织分析数据（PII/运营数据） | 将 `ORG_ADMIN` 约束到当前组织，并在查询层强制追加 org 条件与数据权限过滤 |
| 占位 | 🟡 | `DefaultPromptEngine#renderWithExamples`（TODO 返回普通 render）、`stats/ReportService#exportPdf`（“骨架”） | 占位/降级实现仍会表现为可用能力 | 未实现即拦截并明确报错，不静默降级 |
| m13 | 🟡 | `ChatController.streamChat` 等 | 大量手工拼 JSON（`"{\"token\":\"%s\"}".formatted(escapeJson(...))`），SSE/事件/CI 多处重复且脆弱 | 统一用 ObjectMapper/DTO 序列化 |
| m14 | 🟡 | `ChatController.streamChat` AI 回复 `saveMessage(0L, "AI", ...)` | 用魔法值 0L 表示 AI 发送者，与 Actor(type+id) 约定不一致 | 用 Actor 抽象记录 AI 发送者 |
| m15 | 🟡 | `company/workflow/WorkflowExecutor` | 注释称“fork 并行”，实际 for 循环内同步 dispatch，非并行；“workflow”概念在 company/framework.engine.workflow/system.workflow 三处并存 | 修正注释；厘清三套 workflow 抽象边界 |

## 修复记录（2026-08-01）

上表 7 项已全部修复，代码内以 `M18:`/`M19:`/`m13:`/`m14:`/`m15:`/`占位修复:` 注释标注：

| 编号 | 修复实现 |
|------|---------|
| M18 | `ChatService` 新增 `requireOwnedConversation`（比对 `conversation.creatorId` 与当前身份，不匹配抛"会话不存在"而非 403，避免枚举他人会话），应用于 listMessages / getMessagesPaged / archiveSession / deleteSession / renameSession / messageFeedback（messageId 经所属会话反查）；REST 发消息改走新增的 `saveUserMessage`（校验归属），内部/AI 写入路径 `saveMessage` 重载保持不变（事件监听器与 AG-UI 链路无 SecurityContext） |
| M19 | `StatsController`/`TrackingController` 的 `isPlatformAdmin()` 剔除 ORG_ADMIN；新增 `filterOrgId()`——平台管理员 null（全局），其余强制当前 org，缺组织上下文直接 403；`sys_user_event` 增 `org_id`（迁移 `v203__user_event_org_scope.sql`），采集时写入，漏斗/留存/画像查询强制附加 org 条件；埋点事件记录 orgId，热力图/模式聚合按 org 过滤；funnel/retention/profile/heatmap/patterns 放开给 ORG_ADMIN 但只能看本组织 |
| M15 | 复核已收敛：CompanyController 入参全部为 `*CreateDTO`、出参为 `*VO`，无实体出入参残留 |
| 占位 | `DefaultPromptEngine#renderWithExamples` 抛 `UnsupportedOperationException`（原静默 `return render(...)`，maxExamples 被忽略）；`ReportService#exportPdf` 抛业务异常（原向 `application/pdf` 流写纯文本，产出"下载成功但打不开"的假 PDF）；接口与类注释同步去掉"骨架"表述 |
| m13 | `ChatService#messageFeedback` 改用 Jackson `ObjectNode` 构造 JSON；ChatController 的 SSE 手工拼串随 streamChat 迁至 AG-UI 链路已不存在 |
| m14 | 非人类发送者 senderId 收敛为具名常量 `ChatService.NON_HUMAN_SENDER_ID`，注明行动者语义由 `senderType` 承载 |
| m15 | 删除"fork 并行 + 置信度门控"误导描述（v1 已归档为显式抛异常）；`WorkflowExecutor` 类注释厘清三套 workflow 边界：Flowable 审批流 / 审批流业务封装 / 企业运营编排 |

附带修正（改动同一段 SQL 时发现）：`BehaviorService` 的画像与留存查询原写 `FROM user_event`，实际表名为 `sys_user_event`，运行时必然报表不存在，已一并更正。

遗留：本轮按人类指示未运行 `pnpm nx test service` / `check:affected`，需在下次门控时补跑。

## 良好实践

- `ChatController` 的 createSession/listSessions/sendMessage/streamChat **入口用 `operatorContext.currentUserId()`** 取身份，写入侧不信任客户端 userId（值得作为全局范式推广到 02/07 区的越权接口）。
- `CompanyController` 的 plan/objective/key-result/task/metric 创建请求均使用受校验 DTO，所有 JPA 实体响应（含任务执行）均转换为显式 VO。
- `DurableTaskExecutor` 设计扎实：执行实例 + 检查点 + 事件日志三层持久化，CAS 抢占启动（`casStart`）、孤儿恢复（`recoverOrphans` + 超时阈值）、子任务 fork 与 TaskBoard 依赖管理，具备可恢复/可观测性。
- `BehaviorService` 全部用 `JdbcTemplate` 参数化查询（`?` 占位），无 SQL 注入；漏斗/留存/画像聚合 SQL 正确。
- `DefaultPromptEngine` 版本化（失活旧版 + 递增）、`${var}` 安全插值（`Matcher.quoteReplacement`）实现正确。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：会话级 IDOR（M18）与 `ORG_ADMIN` 跨组织读取风险（M19）均已闭环。
- 已有模式 vs 新建（清单#13）：三套 workflow 抽象、手工 JSON 重复（m13）。
- 成功 vs 错误路径（清单#9）：`recordOutput` 失败仅 warn 不影响主流程，合理；但多处 `catch(Exception)` 仅记日志需确认不掩盖关键失败。

## 待确认（仍未读）

- `ai/{model,output,aigc,a2a,assistant,role,skill,memory,context,team}` 控制器/服务的角色范围、资源归属与输入校验。
- `framework/engine/{workflow(Flowable),memory,dataprocess,skill,metadata,checkpoint,cache}`、`framework/intelligent/cognition` 的实现完整性与一致性。
- `system/{notify,sms,dict,config,file,mail,dashboard,org,task,menu,workflow/approval}` CRUD 的角色范围与资源边界。
- `aaf-auto-dev/doc`（AutodevDocService/Import）路径与大小限制；`knowledge/{segment,problem}` 服务。
