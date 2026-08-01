# 04 AI 引擎与工具

> 覆盖：工具权限守卫、脚本执行沙箱、价值规则引擎、占位引擎、知识库服务。

## 问题清单（2026-08-01 复核）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| B5 | 🔴 | PARTIAL | `framework/engine/tool ScriptSandbox` | 由另一对话处理中，本轮跳过 |
| m36 | 🟡 | PARTIAL | `framework/engine/valuerule DefaultValueRuleEngine` | 核实：审计原文"仅硬编码黑名单"已不准确——现在**优先从数据库加载可配置规则**（`ValueRuleRepository.findEnabledForbiddenRules()`），硬编码 `FALLBACK_KEYWORDS` 只是数据库查询失败时的降级兜底。"黑名单应外置可配"已实现。剩余问题：命中逻辑仍是 `content.contains(...)` 子串匹配，同音字/空格插入等经典绕过手法仍有效，语义级升级（LLM/专门内容安全 API）是更大的独立工程，v0.1 阶段维持现状可接受，本轮未做 |
| 占位 | 🟡 | OPEN | `engine/space`、`evolution`、`semanticcalc`、`dsl`、`metadata`、`monitor` 等 | 大量"v0.2+/v0.3+ 实现"的空接口，无实现，未在本轮处理 |

## 良好实践

- `GraalVmScriptExecutor` 用 `HostAccess.NONE/IOAccess.NONE/allowCreateThread(false)/allowNativeAccess(false)`，是正确的受限执行范式——应作为脚本执行的唯一基线。
- 工具权限链在调用前执行委托判定、会话级权限与 HITL 审批，工具调用结果对 PENDING/DENIED 返回结构化提示，利于 Agent 续跑。
- 知识库批量导入已接入上传、队列与处理流水线，进度按真实文档状态汇总。

## 对称性 / 一致性提示

- 已有模式 vs 新建（清单#13）：`ScriptExecutor`（GraalVM/Process）与 `ScriptSandbox`（子进程）两套脚本执行并行抽象，应收敛为一。
- 成功路径 vs 错误路径（清单#9）：`ScriptSandbox.executePython` 异常分支未 `deleteIfExists` 临时文件（轻微泄漏）。
- 工具注册中心多个 "兼容旧接口" 重载——轻微违反"禁兼容层"，建议统一调用方后删除。

## 待确认

- `module/ai/chat`（WebSocket/AGUI/SSE 流式）、`module/ai/agent` 编排、`CognitiveCycleExecutor` 未深读，建议补审（涉及工具调用与流式鉴权）。
- `framework/intelligent/cognition`（记忆/检索）与 `engine/memory` 的一致性未审。
