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
| #5101 | 会话管理 | 多会话并行、会话状态机、会话恢复 | ✅ 已完成 |
| #5102 | 意图理解 | 多轮意图跟踪、意图消歧、槽位填充 | 🟡 部分完成 |
| #5103 | Agent 调度 | 按意图路由到 Agent、负载均衡、优先级队列 | ✅ 已完成 |
| #5104 | 结果聚合 | 多 Agent 结果合并、冲突解决、置信度加权 | ✅ 已完成 |
| #5105 | 情感感知 | 情感分析、语气适配、个性化回复风格 | 🟡 部分完成 |

## 已完成

- `SessionManager`：Redis 持久化会话状态机（ACTIVE/WAITING/PROCESSING/SUSPENDED/CLOSED）、TTL 24h、跨实例恢复
- `IntentUnderstandingService`：规则匹配意图分类（question/command/conversation）
- `AgentDispatcher`：按能力路由 Agent + 沙箱执行 + 批量并行调度
- `ResultAggregator`：置信度排序 + 冲突检测（差距<0.1 标记不同观点）
- `EmotionPerceptionService`：关键词规则情感分类（URGENT/POSITIVE/FRUSTRATED/CONFUSED/NEUTRAL）+ 回复风格建议
- `AssistantService`：完整链路（会话→短期记忆→意图→Skill匹配→Agent认知循环→记忆更新→响应）
- `SkillMatchService`：按触发意图匹配 Skill + 优先级排序
- `SkillDefinition`：JPA 实体（name/assistantId/agentId/triggerIntent/systemPrompt/priority/status）
- `LearningFeedbackService`：执行记录 + 学习反馈

## 待实现

### #5101 会话管理（补充）

- [ ] `AssistantExecutor` 接口：AAF Assistant 执行契约（chat/dispatch/getSession），上层只依赖此接口
- [ ] `DefaultAssistantExecutor`：当前 `AssistantService` 重构实现此接口
- [ ] **[架构修正]** `AssistantDefinition` 拆分为三个概念：
  - `Actor`：人格载体（name/persona/systemPrompt/avatar），可复用跨 Role
  - `Role`：能力配置（Skill 集 + Tool 白名单），可复用跨 Actor
  - `AssistantDefinition`：Actor + Role + MemoryStrategy 的组合，是运行时实体
  - 对应数据库表：`ai_actor` / `ai_role` / `ai_assistant`（关联 actor_id + role_id）

### #5102 意图理解（核心补充）

> ⚠️ **跨版本依赖**：LLM 驱动实现依赖 v0.3 AAF-043（对话引擎）提供的 Spring AI 集成，在 AAF-043 完成前只能保持关键词规则实现。

- [ ] LLM 驱动意图分类（替换当前关键词规则，依赖 AAF-043）
- [ ] 多轮意图跟踪：跨消息维护意图上下文，识别意图切换
- [ ] 意图消歧：置信度低时生成澄清问题
- [ ] 槽位填充：从对话上下文提取缺失参数（当前 `fillSlots` 返回空 Map）
- [ ] 意图分类器训练/配置接口

### #5105 情感感知（补充）

> ⚠️ **跨版本依赖**：LLM 驱动实现同样依赖 AAF-043，在此之前维持关键词规则。

- [ ] LLM 驱动情感分析（替换关键词规则，支持复杂情感，依赖 AAF-043）
- [ ] 情感历史追踪：跨对话的情感趋势
- [ ] 回复风格实际应用到 Prompt 构建中（当前只返回建议，未集成）

### 通用补充

### 内置技能设计（4 个，结构化存储到 ai_skill_definition 表）

> 参考 `.kiro/skills/` 的 SKILL.md 设计思路，但数据结构化存储（不是文件），前端可配置。
> 内置技能 `builtIn=true`，不可删除，可被覆盖（用户可创建同名技能覆盖默认行为）。

**技能数据模型扩展**：
```
SkillDefinition
  + builtIn: boolean       是否内置（内置不可删除）
  + category: String       分类（BUILTIN / USER / IMPORTED）
  + instructions: Text     技能指令（对应 SKILL.md 正文，Markdown）
  + triggerKeywords: List  触发关键词（辅助意图匹配）
  + allowedTools: List     允许使用的工具白名单
```

**4 个内置技能**：

| 技能名 | 触发条件 | 绑定 Agent | 核心行为 |
|--------|---------|-----------|---------|
| `self-awareness` | 用户问"你是谁/你能做什么/你有哪些技能" | 无（Assistant 直接回答） | 自我介绍：描述 Actor 人格、Role 技能集、可用工具、记忆策略 |
| `user-understanding` | 用户首次交互 / 用户描述自己的背景/偏好 | 无（Assistant 直接处理） | 主动收集用户画像：职业/偏好/沟通风格，写入 Memory（assistantId 隔离） |
| `self-learning` | 用户反馈差（👎）/ 任务失败 / 用户说"你做错了" | LearningAgent | 触发 LearningPipeline：收集轨迹→评估→提取改进→更新 Skill/Prompt/Memory |
| `skill-creation` | 用户说"创建技能/新建技能/教你做X" | SkillBuilderAgent | 引导用户定义技能（名称/触发条件/指令/工具），生成 SkillDefinition 并持久化 |

- [ ] `SkillDefinition` 实体扩展 `builtIn` / `category` / `instructions` / `triggerKeywords` / `allowedTools` / `version` 字段
- [ ] 数据库迁移：`ai_skill_definition` 表补充上述字段
- [ ] `BuiltinSkills` 枚举/常量类：代码定义 4 个内置技能（权威来源，版本控制）
- [ ] `BuiltinSkillInitializer`（`ApplicationRunner`）：启动时 upsert 内置技能到数据库（存在且版本相同则跳过，版本升级则更新）
- [ ] `SkillMatchService` 优先级：用户自定义同名技能 > 内置技能
- [ ] 前端技能管理页面（AAF-050 #5006）：内置技能只读展示 + 用户技能可 CRUD
- [ ] 负载均衡：多个同能力 Agent 间的负载分配策略
- [ ] 优先级队列：高优先级请求插队机制
- [ ] Assistant 配置管理 API（创建/编辑 Assistant 及其 Skill 绑定）

### learn 包（已合并到顶层 learning/ 包）

> `assistant/learn/` 与系统级 Learning 是同一套流程，已合并为顶层 `learning/` 包。
> Assistant 通过内置 `LearningSkill` 触发学习，产出按数据归属分流：
> - Memory / Value → 关联 assistantId（助理私有）
> - Knowledge → 全局共享，不关联具体助理
>
> 详见 `learning/` 包任务（待建 AAF-049 补充或单独用户故事）。

- [ ] `LearningFeedbackService` 迁移到 `learning/` 包

### 记忆通道配置（依赖 AAF-049 MemoryPipeline 抽象）

> **结论**：需要，现在就做。`AssistantDefinition` 声明 `MemoryStrategy`，`AssistantService` 通过 `MemoryPipelineFactory` 选择对应 Pipeline 执行，不硬编码。
>
> **依赖**：AAF-049 先完成 `MemoryPipeline` 接口 + Factory，本任务再接入。

- [ ] `AssistantDefinition` 加 `memoryStrategy` 字段（默认 `HYBRID`）
- [ ] `AssistantService` 改为通过 `MemoryPipelineFactory` 获取 Pipeline，移除硬编码检索调用
