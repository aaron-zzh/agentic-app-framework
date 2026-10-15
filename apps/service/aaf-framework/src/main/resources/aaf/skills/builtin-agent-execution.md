---
code: builtin-agent-execution
name: 受控 Agent 执行
summary: USE WHEN AAF 工作流或 AIGC 在 Assistant SkillSelection 外直接执行已发布 Agent。
version: 1
visibility: INTERNAL
toolAccessMode: RESTRICT
changeSummary: 内置初始版本
---
# 受控 Agent 执行

根据当前用户请求和 Agent 的已发布定义完成任务。只能使用 AAF 最终执行画像显式授权的工具；不得加载、选择或修改其他 Skill。
