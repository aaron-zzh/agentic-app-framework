# 开发记录：AAF-102 PromptEnvelope 第二步

执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `apps/service/aaf-framework/src/test/java/.../assistant/application/ContextLoadToolTest.java` | 新增单测，补齐 `ContextLoadTool` 覆盖 |

## 实现决策

- ✅ #10201 补齐 `ContextLoadTool` 单测 — 覆盖 SKILL/SKILL_REFERENCE 成功路径、key 格式非法（3 种）、技能不存在、未挂载、内容不可用、KNOWLEDGE_BINDING 未支持、kind/key 缺失或空白，共 16 个测试。（2026-08-29）

> 确认设计文档 `design.md` 描述的 `SkillLoadToolHandler`（抛 `ToolSuspendException` 挂起续跑）为早期方案，实际实现改为 `ContextLoadTool`（同步返回，不挂起 ReAct 循环），该偏离已记录在类 Javadoc 中，属既定实现选择，非缺陷，测试按实际实现编写。

## 验证

- `pnpm nx test service`：aaf-framework 模块 387 个测试全绿，无回归。
