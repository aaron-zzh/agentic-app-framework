# 08 AI 对话 · 任务 · 企业运营 · 统计

> 覆盖：ai/chat 会话与流式、持久任务执行、company 编排、stats 行为分析、prompt 引擎。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| M18 | 🟠 | `ai/chat/controller/ChatController`（listMessages/getMessagesPaged/deleteSession/renameSession/archiveSession/streamChat/messageFeedback） | 这些操作以 `sessionId`/`messageId` 取自路径但**未校验归属当前用户**→对象级 IDOR：可读/删/改他人会话与消息 | 在 ChatService 内校验 session.userId == currentUserId，否则 404/403 |
| M19 | 🟠 | `stats/StatsController`、`ui/tracking/TrackingController` | 趋势/漏斗/留存/用户画像/报表导出、热力图/模式 均无鉴权，且查询为组织全域无租户隔离→越权读全员分析数据（PII/运营），叠加 B1 | 加鉴权 + 租户/数据权限过滤 |
| M15 | 🟠 | `company/controller/CompanyController` | 见 07 M15：plan/objective/task/metric 以实体作 `@RequestBody`（Mass Assignment）+ 返回实体 + 无鉴权 | 改 DTO/VO；加鉴权 |
| 占位 | 🟡 | `DefaultPromptEngine#renderWithExamples`（TODO 返回普通 render）、`stats/ReportService#exportPdf`（"骨架"）、`KnowledgeBaseService` 导入 | 占位/假实现散布多处（见 04/07） | 未实现即拦截并明确报错，不静默降级 |
| m13 | 🟡 | `ChatController.streamChat` 等 | 大量手工拼 JSON（`"{\"token\":\"%s\"}".formatted(escapeJson(...))`），SSE/事件/CI 多处重复且脆弱 | 统一用 ObjectMapper/DTO 序列化 |
| m14 | 🟡 | `ChatController.streamChat` AI 回复 `saveMessage(0L, "AI", ...)` | 用魔法值 0L 表示 AI 发送者，与 Actor(type+id) 约定不一致 | 用 Actor 抽象记录 AI 发送者 |
| m15 | 🟡 | `company/workflow/WorkflowExecutor` | 注释称"fork 并行"，实际 for 循环内同步 dispatch，非并行；"workflow" 概念在 company/framework.engine.workflow/system.workflow 三处并存 | 修正注释；厘清三套 workflow 抽象边界 |

## 良好实践

- `ChatController` 的 createSession/listSessions/sendMessage/streamChat **入口用 `operatorContext.currentUserId()`** 取身份，写入侧不信任客户端 userId（值得作为全局范式推广到 02/07 区的越权接口）。
- `DurableTaskExecutor` 设计扎实：执行实例 + 检查点 + 事件日志三层持久化，CAS 抢占启动（`casStart`）、孤儿恢复（`recoverOrphans` + 超时阈值）、子任务 fork 与 TaskBoard 依赖管理，具备可恢复/可观测性。
- `BehaviorService` 全部用 `JdbcTemplate` 参数化查询（`?` 占位），无 SQL 注入；漏斗/留存/画像聚合 SQL 正确。
- `DefaultPromptEngine` 版本化（失活旧版 + 递增）、`${var}` 安全插值（`Matcher.quoteReplacement`）实现正确。

## 对称性 / 一致性提示

- 认证 vs 鉴权（清单#8）：会话级 IDOR（M18）、stats 越权（M19）。
- 已有模式 vs 新建（清单#13）：三套 workflow 抽象、手工 JSON 重复（m13）。
- 成功 vs 错误路径（清单#9）：`recordOutput` 失败仅 warn 不影响主流程，合理；但多处 `catch(Exception)` 仅记日志需确认不掩盖关键失败。

## 待确认（仍未读）

- `ai/{model,output,aigc,a2a,assistant,role,skill,memory,context,team}` 控制器/服务的鉴权与输入校验（预计同样存在 B9 类缺口）。
- `framework/engine/{workflow(Flowable),memory,dataprocess,skill,metadata,checkpoint,cache}`、`framework/intelligent/cognition` 的实现完整性与一致性。
- `system/{notify,sms,dict,config,file,mail,dashboard,org,task,menu,workflow/approval}` CRUD 与鉴权。
- `aaf-auto-dev/doc`（AutodevDocService/Import）路径与大小限制；`knowledge/{segment,problem}` 服务。
