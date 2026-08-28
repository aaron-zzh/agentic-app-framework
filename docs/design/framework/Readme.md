---
level: Practice
layer: Model
purpose: 框架设计目录索引
status: published
version: 1.3.0
date: 2026-08-23
author: AaronZZH
changelog:
  - 2026-08-23 | 智能架构重构：智能层统一由 intelligent/Readme.md 入口；补充复杂性封装、人类计算、开发时能力
  - 2026-07-31 | 智能架构收敛为唯一 architecture.md
  - 2026-07-23 | 更新五层智能架构 v2 与开发计划入口
  - 2026-05-06 | 补充 Front Matter
---

# 框架设计（Framework）

> 框架层设计文档，涵盖 AAF 五层架构中的 Layer 2（引擎层）和 Layer 3（智能层）。

## 目录结构

```
framework/
├── component-overview.md          # 架构级核心组件总览
├── execution-flow.md              # 端到端执行流程
├── operator.md                    # Operator 操作者模型
├── complexity-encapsulation.md    # 复杂性封装策略
├── human-computation.md           # 人类计算支撑
├── intelligent/                   # 五层智能架构与横向机制
├── engine/                        # 专项引擎
├── auto-dev/                      # AI 自动开发
├── dsl/                           # 领域语言
├── data/                          # 数据与查询
├── security/                      # 安全与权限
└── api/                           # 协议与网关
```

## 文档索引

### 核心架构（根目录）

| 文档 | 说明 |
|------|------|
| [智能架构设计索引](intelligent/Readme.md) | 五层智能架构与横向机制导航——智能层唯一入口 |
| [component-overview.md](component-overview.md) | 架构级核心组件总览 |
| [execution-flow.md](execution-flow.md) | 端到端执行流程 |
| [operator.md](operator.md) | Operator 操作者模型设计 |
| [complexity-encapsulation.md](complexity-encapsulation.md) | 复杂性默认隐藏、按需展开、查看与操作权限分离 |
| [human-computation.md](human-computation.md) | 群体智慧贡献的横向执行底座 |
| [meta-engine.md](engine/meta/meta-engine.md) | 元引擎核心设计（调度、状态、上下文、置信度门控） |

### 专项引擎 `engine/`

| 文档                                                                | 说明 |
|-------------------------------------------------------------------|------|
| [atom-memory.md](engine/data-knowledge/atom-memory.md)             | AtomMemory 原子记忆引擎（支撑 Cognition.Memory） |
| [nexus-knowledge.md](engine/data-knowledge/nexus-knowledge.md)                   | NexusKB 连接式知识引擎（支撑 Cognition.Knowledge） |
| [semantic-compute.md](engine/data-knowledge/semantic-compute.md)         | SemanticCalc 语义计算引擎（横切支撑 Cognition/Learning/Agent） |
| [data-process.md](engine/data-knowledge/data-process-engine.md)           | DataProcess 数据处理分析引擎（v2.0 迁移到 actormesh） |
| [physics-spacetime.md](engine/ecosystem/physics-spacetime.md) | PhysicsSpaceTime 物理时空引擎（世界模型 + 语义引力 + 虚拟空间基础） |
| [auto-dev.md](auto-dev/auto-dev.md)                                 | AI 自动开发引擎 |
| [dev-capability.md](auto-dev/dev-capability.md)                     | 开发时能力：自开发设计、两类开发对象、业务运行时四层 |
| [document-engine.md](engine/content/document-engine.md)                   | 文档引擎设计 |
| [magic-dsl.md](./dsl/magic-dsl.md)                                | DSL 设计（占位） |

### 安全与权限 `security/`

| 文档 | 说明 | 状态 |
|------|------|------|
| [access-control.md](security/access-control.md) | 访问控制（认证 + 授权） | draft |
| [security.md](security/security.md) | 安全架构（加密、脱敏、审计、AI 安全） | draft |
| [license-control.md](security/license-control.md) | 商业授权控制（Premium vs Free）与开发者商业化托管额度 | draft |

## 与其他目录的关系

- 整体架构概览 → [docs/design/architecture.md](../architecture.md)
- 后端实现细节 → [docs/design/apps/service/](../apps/service/)
- 前端实现细节 → [docs/design/ui/](../ui/)
