---
level: Practice
layer: Model
purpose: 框架设计目录索引
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 框架设计（Framework）

> 框架层设计文档，涵盖 AAF 五层架构中的 Layer 2（引擎层）和 Layer 3（智能层）。

## 目录结构

```
framework/
├── meta-engine.md       # 元引擎核心设计
├── core/                # 核心智能（Layer 3）
├── engine/              # 专项引擎（Layer 2）
├── security/            # 安全与权限
└── assets/              # 图片资源
```

## 文档索引

### 核心架构（根目录）

| 文档 | 说明 |
|------|------|
| [meta-engine.md](meta-engine.md) | 元引擎核心设计（调度、状态、上下文、置信度门控） |

### 核心智能 `core/`

| 文档 | 说明 |
|------|------|
| [agent.md](core/agent.md) | 智能体系统设计（五层智能架构总览） |
| [cognition/Readme.md](core/cognition/Readme.md) | 认知层设计（Layer 1 Cognition，Memory/Knowledge/Value/Retrieval） |
| [actor-model.md](core/actor-model.md) | Actor 模型设计 |

### 专项引擎 `engine/`

| 文档 | 说明 |
|------|------|
| [atom-memory-engine.md](engine/atom-memory-engine.md) | AtomMemory 原子记忆引擎（支撑 Cognition.Memory） |
| [nexus-kb-engine.md](engine/nexus-kb-engine.md) | NexusKB 连接式知识引擎（支撑 Cognition.Knowledge） |
| [semantic-calc-engine.md](engine/semantic-calc-engine.md) | SemanticCalc 语义计算引擎（横切支撑 Cognition/Learning/Agent） |
| [data-process-engine.md](engine/data-process-engine.md) | DataProcess 数据处理分析引擎（v2.0 迁移到 actormesh） |
| [auto-dev.md](engine/auto-dev.md) | AI 自动开发引擎 |
| [document-engine.md](engine/document-engine.md) | 文档引擎设计 |
| [conversational-interaction.md](engine/conversational-interaction.md) | 对话式交互设计 |
| [magic-dsl.md](engine/magic-dsl.md) | DSL 设计（占位） |

### 安全与权限 `security/`

| 文档 | 说明 | 状态 |
|------|------|------|
| [access-control.md](security/access-control.md) | 访问控制（认证 + 授权） | draft |
| [security.md](security/security.md) | 安全架构（加密、脱敏、审计、AI 安全） | draft |
| [license-control.md](security/license-control.md) | 商业授权控制（Premium vs Free） | draft |

## 与其他目录的关系

- 整体架构概览 → [docs/design/architecture.md](../architecture.md)
- 后端实现细节 → [docs/design/apps/service/](../apps/service/)
- 前端实现细节 → [docs/design/ui/](../ui/)
