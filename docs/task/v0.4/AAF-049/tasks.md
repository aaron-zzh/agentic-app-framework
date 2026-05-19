---
level: Practice
layer: Product
status: in-progress
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# Cognition 认知层（AAF-049）

## 技术任务

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| #4901 | 短期记忆 | Redis 实现、对话级上下文缓存、TTL 自动过期 | ✅ 已完成 |
| #4902 | 长期记忆 | PostgreSQL 持久化、记忆提取与压缩、重要性评分 | 🟡 部分完成 |
| #4903 | 图谱记忆 | Neo4j 实体关系存储、时序图谱、参考 graphiti 双时态模型 | 🟡 部分完成 |
| #4904 | 程序化记忆 | 参考 ReMe、经验蒸馏、SOP 记忆、技能记忆 | 🟡 部分完成 |
| #4905 | 记忆检索 | 多源融合检索、相关性排序、记忆衰减策略 | ✅ 已完成 |

## 已完成

- `ShortTermMemoryService`：Redis List 实现、滑动窗口（50条）、TTL 2h 自动过期
- `LongTermMemoryService`：PG 持久化、重要性评分、访问频率提升、遗忘机制
- `LongTermMemory` 实体 + Repository
- `GraphMemoryService`：实体节点存储、关系添加、多跳查询、关键词搜索
- `GraphMemoryNode` / `GraphMemoryRelation` 实体 + Repository
- `ProceduralMemoryService`：经验蒸馏、按任务类型检索、使用反馈（成功率）
- `ProceduralMemory` 实体 + Repository
- `MemoryRetrievalService`：多源融合检索（短期+长期+图谱+程序化+原子引擎）、预算分配、重排
- `MemoryExtractionService`：LLM 驱动记忆抽取（对话→原子事实+关系）、去重决策
- `MemoryDeduplicationService`：语义去重
- `MemoryRerankerService`：LLM 重排
- `UnifiedRetrievalService`：跨 Memory/Knowledge 统一路由与 RRF 融合
- `AtomMemoryEngine`（引擎层）：原子记忆 CRUD + 向量检索 + 时间衰减
- `MemoryAtom` / `MemoryRelation` 实体 + Repository
- V24 迁移脚本：`memory_atom` + `memory_relation` 表（含 pgvector 索引）
- **今日新增**：`cognition/pipeline/DefaultMemoryPipeline`（实现 `MemoryPipeline` 接口）、`MemoryPipelineFactory`（按 `MemoryStrategy` 选择实现）

## 架构修正（优先级高）

> **问题一：分层违规**
> Cognition 层当前直接持有 JPA Repository（`LongTermMemoryRepository`、`GraphMemoryRepository`、`ProceduralMemoryRepository`），与引擎层 `AtomMemoryEngine` 形成并行存储。
>
> **问题二：MemoryPipeline 已抽象**（今日完成）
> `MemoryPipeline` 接口已定义在 `core/memory/`，`DefaultMemoryPipeline` + `MemoryPipelineFactory` 已在 `cognition/pipeline/` 实现。`MemoryRetrievalService` 和 `UnifiedRetrievalService` 作为 `DefaultMemoryPipeline` 的底层实现保留。

### MemoryPipeline 抽象（已完成）

- [x] `MemoryPipeline` 接口（`core/memory/`）
- [x] `MemoryStrategy` 枚举（MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL）
- [x] `MemoryContext` Record（各 Stage 格式化块，可直接注入 Prompt）
- [x] `PipelineInput` Record
- [x] `DefaultMemoryPipeline`（全源：Memory + Knowledge + RRF + 重排）
- [x] `MemoryPipelineFactory`（按策略选择实现，内嵌 MemoryOnly/KnowledgeOnly/ProceduralFirst）

### 分层违规修正（待实现）

- [ ] `LongTermMemoryService` 重构：移除 Repository，改调 `AtomMemoryEngine`
- [ ] `ProceduralMemoryService` 重构：程序化记忆以 `type=PROCEDURAL` 存入 `MemoryAtom`
- [ ] **[BUG]** `GraphMemoryNode` / `GraphMemoryRelation` 使用 JPA `@Entity`，应改为 Spring Data Neo4j 的 `@Node` / `@Relationship`
- [ ] `GraphMemoryService` 重构：等 AAF-054 图谱引擎落地后对接，当前保持占位

## 待实现

### #4902 长期记忆（补充）

- [ ] `long_term_memory` 表迁移脚本（当前实体存在但无建表 SQL）
- [ ] 记忆压缩：多条相似记忆合并为摘要
- [ ] 记忆衰减定时任务（定期执行 forget）

### #4903 图谱记忆（补充）

- [ ] Neo4j 实际连接配置（Spring Data Neo4j 配置）
- [ ] 双时态模型：valid_from / valid_to 时间窗口（参考 graphiti）
- [ ] 图谱可视化数据导出接口

### #4904 程序化记忆（补充）

- [ ] `procedural_memory` 表迁移脚本
- [ ] SOP 记忆：将多步骤流程记录为可复用 SOP
- [ ] 技能记忆：与 SkillDefinition 关联，Agent 执行后自动蒸馏

### 通用

- [ ] Neo4j 连接池配置 + 健康检查
- [ ] 记忆系统配置类（TTL、窗口大小、衰减参数可配置化）

### 数据库迁移脚本（阻塞项）

- [ ] `long_term_memory` 表迁移脚本（实体已存在，无建表 SQL）
- [ ] `procedural_memory` 表迁移脚本（实体已存在，无建表 SQL）
