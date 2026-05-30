# 04 AI 引擎与工具

> 覆盖：工具权限守卫、脚本执行沙箱、价值规则引擎、占位引擎、知识库服务。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B5 | 🔴 | `framework/engine/tool ScriptSandbox` | 注释称"文件系统隔离/资源限制"，`executePython` 实为裸 `ProcessBuilder("python3")` 无隔离；`executeShell` 用关键词黑名单（易绕过）；与真正受限的 `GraalVmScriptExecutor` 并存 | 统一走 GraalVM 受限上下文，或子进程配 OS 级隔离（容器/seccomp/低权用户）；修正误导注释；shell 改白名单或禁用 |
| M12 | 🟠 | `framework/intelligent/agent ToolPermissionGuard#wrapWithPermission` | `meta==null` 的工具"默认放行"不包装→未注册工具绕过权限检查（fail-open） | 无元数据工具默认拒绝或强制走最严策略 |
| M13 | 🟠 | `module/knowledge KnowledgeBaseService#batchImportDocuments/getImportProgress` | TODO 占位：未真正解析/分块/向量化；`getImportProgress` 直接假返回 `COMPLETED` | 接入 framework KnowledgePipeline 或明确标注未实现并拦截调用，禁止假成功 |
| M2-vr | 🟡 | `framework/engine/valuerule DefaultValueRuleEngine` | 硬编码关键词黑名单做内容安全，易绕过 | v0.1 可接受，但黑名单应外置可配，并规划语义级升级 |
| 占位 | 🟡 | `engine/space`、`evolution`、`semanticcalc`、`dsl`、`metadata`、`monitor` 等 | 大量"v0.2+/v0.3+ 实现"的空接口，无实现 | 违反"简洁优先/禁占位"，按需到实现阶段再声明 |

## 良好实践

- `GraalVmScriptExecutor` 用 `HostAccess.NONE/IOAccess.NONE/allowCreateThread(false)/allowNativeAccess(false)`，是正确的受限执行范式——应作为脚本执行的唯一基线。
- `ToolPermissionGuard` 组合模式装饰 `ToolCallback`，在 `call()` 前做委托判定 + 会话级权限 + HITL 审批，整体设计合理（除 fail-open 缺口）。
- 工具调用结果对 PENDING/DENIED 返回结构化提示而非抛异常，利于 Agent 续跑。

## 对称性 / 一致性提示

- 已有模式 vs 新建（清单#13）：`ScriptExecutor`（GraalVM/Process）与 `ScriptSandbox`（子进程）两套脚本执行并行抽象，应收敛为一。
- 成功路径 vs 错误路径（清单#9）：`ScriptSandbox.executePython` 异常分支未 `deleteIfExists` 临时文件（轻微泄漏）。
- `ToolPermissionGuard` 多个 "兼容旧接口" 重载——轻微违反"禁兼容层"，建议统一调用方后删除。

## 待确认

- `module/ai/chat`（WebSocket/AGUI/SSE 流式）、`module/ai/agent` 编排、`CognitiveCycleExecutor` 未深读，建议补审（涉及工具调用与流式鉴权）。
- `framework/intelligent/cognition`（记忆/检索）与 `engine/memory` 的一致性未审。
