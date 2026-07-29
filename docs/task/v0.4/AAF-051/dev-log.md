---
level: Practice
layer: Product
purpose: 记录 Role / Skill 只读目录阶段一改造及验证结果
status: active
version: 1.0.0
date: 2026-07-29
author: Kiro
---

# 开发记录：Role / Skill 只读目录接线

执行者：AI/developer-service

## AAF-051 阶段一只读目录接线

✅ 07-29 — developer-service

- ai_role 增 code 与防御性回填
- 新增 Role、Skill 只读端口及适配器
- Skill 目录复用既有 SkillStore
- 仅注册 Bean，未接入执行链
- 定向 12 项测试及 Spotless 全绿

> **问题**：`pnpm nx test service` 已执行，但被并行工作区既有 CRUD/授权测试失败阻断；本阶段新增 framework 10 项测试均通过，另行定向执行的 api 2 项测试通过。

> **注意**：迁移尚未在实际 PostgreSQL 环境执行；并行修改的 v12 内容角色未固定 ID 且位于客服 `id=1` 前，空库可能发生种子主键冲突，本阶段未越界修改。


## AAF-051 有效能力接入验证

✅ 07-29 — developer-service

- 验证内置 Role 技能与工具键
- 编译器新增 Role 感知重载
- 工具交集后再构建 Toolkit
- 有效工具纳入编译缓存键
- 保留无 Role 编译原行为


## AAF-051 阶段三 SkillRoute 子智能体规格化

✅ 07-29 — developer-service

- 新增 SubagentSpec 两类规格
- SkillRoute 改持有 subagentSpec
- 任务溯源统一使用 identifier
- 内置路由改动态子智能体
- 删除 v201 内置 Agent 种子

> **问题**：Dynamic 尚未接入执行器 → 本阶段不可独立发布 → 须与执行适配改造原子合并

> **验证**：按本阶段约束未执行测试或构建命令，仅完成静态审查


## #5103 Dynamic 子智能体执行接线

✅ 07-29 — developer-service

- 命令改持有 SubagentSpec
- Predefined 查询缓存行为不变
- Dynamic 无库无缓存直编译
- 计量事件统一执行上下文
- 移除 Dynamic 执行拒绝

> **决策**：Assistant 暂无独立模型字段，复用 CapabilityRouter 解析系统当前 CHAT 模型，并以数据库主键构造 ModelSpec。

> **注意**：Dynamic.tools 视为上游已物化的最终工具集；当前两个内置助理为空工具集，可执行文本生成但不继承父 Toolkit。

> **验证**：按本轮约束未执行测试或构建命令，仅完成代码修改与静态审查


## #5103 Dynamic 子智能体 major 复核修复

✅ 07-29 — developer-service

- Predefined 移除父模型依赖
- Dynamic Agent 终止即释放
- 未实现工具继承显式拒绝
- 内置助理各显式声明三工具
- 静态复核调用面与所有权路径

> **验证**：按本轮约束未执行测试、pnpm 或 mvn 命令，仅完成代码修改与静态审查。
