---
level: Practice
layer: Product
status: in-progress
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# Assistant 助理层（AAF-051）

## 技术任务

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| #5101 | 会话管理 + AssistantExecutor | 多会话并行、会话状态机、Actor/Role/MemoryStrategy 拆分 | 🟡 部分完成 |
| #5102 | 意图理解 | 多轮意图跟踪、意图消歧、槽位填充 | 🟡 部分完成 |
| #5103 | Agent 调度 | 按意图路由到 Agent、负载均衡、优先级队列 | ✅ 已完成 |
| #5104 | 结果聚合 | 多 Agent 结果合并、冲突解决、置信度加权 | ✅ 已完成 |
| #5105 | 情感感知 | 情感分析、语气适配、个性化回复风格 | 🟡 部分完成 |

## 已完成

- `SessionManager`：Redis 持久化会话状态机（ACTIVE/WAITING/PROCESSING/SUSPENDED/CLOSED）、TTL 24h
- `IntentUnderstandingService`：规则匹配意图分类（question/command/conversation）
- `AgentDispatcher`：按能力路由 Agent + AgentPool 池化 + AgentSandbox 执行（**今日重构**）
- `ResultAggregator`：置信度排序 + 冲突检测
- `EmotionPerceptionService`：关键词规则情感分类 + 回复风格建议
- `LearningFeedbackService`：执行记录 + 学习反馈
- `AssistantDefinition`：重构为 Actor + Role + MemoryStrategy 组合（**今日新增**）
- `actor/Actor`：人格载体 @Entity（name/persona/systemPrompt/avatar）（**今日新增**）
- `role/Role`：能力配置 @Entity（Skill 集 + Tool 白名单）（**今日新增**）
- `DefaultAssistantExecutor`：实现 `AssistantExecutor` 接口，按 MemoryStrategy 路由记忆管道（**今日新增**）

## 待实现

### #5101 会话管理（补充）

- [ ] `AssistantDefinitionRepository`：按 assistantId/userId 查询
- [ ] `ActorRepository` / `RoleRepository`：Actor 和 Role 的 CRUD
- [ ] Assistant 配置管理 API（创建/编辑 Assistant 及其 Actor/Role 绑定）
- [ ] 旧的 `AssistantService` 与新的 `DefaultAssistantExecutor` 共存，待统一后删除旧版

### #5102 意图理解（核心补充）

> ⚠️ **跨版本依赖**：LLM 驱动实现依赖 v0.3 AAF-043（对话引擎），在此之前只能保持关键词规则实现。

- [ ] LLM 驱动意图分类（替换当前关键词规则，依赖 AAF-043）
- [ ] 多轮意图跟踪：跨消息维护意图上下文，识别意图切换
- [ ] 意图消歧：置信度低时生成澄清问题
- [ ] 槽位填充：从对话上下文提取缺失参数（当前 `fillSlots` 返回空 Map）

### #5105 情感感知（补充）

> ⚠️ **跨版本依赖**：LLM 驱动实现同样依赖 AAF-043。

- [ ] LLM 驱动情感分析（替换关键词规则，依赖 AAF-043）
- [ ] 情感历史追踪：跨对话的情感趋势
- [ ] 回复风格实际应用到 Prompt 构建中（当前只返回建议，未集成）

### 内置技能设计（4 个，结构化存储到 ai_skill_definition 表）

> 内置技能在 `engine/skill/BuiltinSkills` 枚举中定义，`BuiltinSkillInitializer` 启动时 upsert 到数据库。

| 技能名 | 触发条件 | 说明 |
|--------|---------|------|
| `builtin-self-awareness` | 用户问"你是谁/你能做什么" | 介绍 Actor 人格、Role 技能集、可用工具 |
| `builtin-user-understanding` | 用户描述自己的背景/偏好 | 主动收集用户画像，写入 Memory（assistantId 隔离） |
| `builtin-self-learning` | 用户反馈差/任务失败 | 触发 LearningPipeline |
| `builtin-skill-creation` | 用户说"创建技能/教你做X" | 引导用户定义新技能，生成 SkillDefinition 持久化 |

- [x] `engine/skill/BuiltinSkills` 枚举（4 个内置技能，代码权威源）
- [x] `engine/skill/BuiltinSkillInitializer`（启动时 upsert，版本升级自动更新）
- [ ] `SkillDefinition` 实体扩展字段已在 engine/skill/ 完成，旧的 `intelligent/assistant/SkillDefinition` 待删除

### 记忆通道配置（依赖 AAF-049 MemoryPipeline 抽象）

- [x] `MemoryStrategy` 枚举（core/memory/）
- [x] `AssistantDefinition` 加 `memoryStrategy` 字段
- [x] `DefaultAssistantExecutor` 通过 `MemoryPipelineFactory` 按策略路由
- [ ] `AssistantDefinition` 加 `knowledgeBaseId` 字段（已加，待迁移脚本）

### 通用补充

- [ ] 负载均衡：多个同能力 Agent 间的负载分配策略
- [ ] 优先级队列：高优先级请求插队机制

### learn 包（已合并到顶层 learning/ 包）

> `assistant/learn/` 与系统级 Learning 是同一套流程，合并为 `intelligent/learning/`。
> Assistant 通过内置 `LearningSkill` 触发，产出按数据归属分流（Memory/Value → assistantId 隔离，Knowledge → 全局）。

- [ ] `LearningFeedbackService` 迁移到 `learning/` 包（当前在 `assistant/` 根目录）
