---
level: Overview
layer: Model
purpose: 定义智能架构设计目录边界，并索引五层架构、实施计划和专项设计
status: published
version: 1.0.0
date: 2026-07-23
author: AaronZZH
---

# 智能架构设计

本目录描述 AAF 智能层的领域模型、运行时边界和专项技术设计。当前目标架构以 Assistant、Team、Agent、Cognition、Core 五层领域模型为核心，AgentScope 仅作为基础设施适配器。

## 阅读顺序与边界

优先阅读 v2 架构及其开发计划。其他根目录文档是专项设计或历史迁移材料；与 v2 冲突时，以 `architecture-v2.md` 中“已确认的实现决策”为准。

本目录负责“智能层如何设计”，不负责正式迭代 Story、代码实现和业务产品需求。正式开发任务放入 `docs/task/`，通用工程规范放入 `docs/reference/`。

## 当前架构

| 文档 | 内容 | 状态 |
|---|---|---|
| [五层智能架构 v2](architecture-v2.md) | 五层领域模型、内置助理、默认长期记忆、AgentScope v2 边界、轨迹与多副本一致性 | 当前目标架构 |
| [五层智能架构 v2 开发计划](architecture-v2-development-plan.md) | P0-P6 任务、依赖、退出标准、测试矩阵和旧路径删除清单 | 配套实施计划 |

## 专项与历史设计

| 文档 | 内容 | 使用说明 |
|---|---|---|
| [智能架构（历史版）](architecture.md) | 早期智能架构总览 | 历史参考，已由 v2 取代 |
| [AgentScope 整合](agentscope-integration.md) | 早期 AgentScope 整合分析 | 迁移素材；API 与实例模型以 v2 为准 |
| [Assistant/Agent Runtime 重构](assistant-agent-runtime-refactor.md) | 旧运行时重构设计 | 迁移素材；不作为目标包或兼容方案 |
| [任务持久化](task-durability.md) | 长任务、恢复和持久化专项 | 结合 v2 状态所有权使用 |
| [审查机制](review-mechanism.md) | 结果审查与确认机制 | 专项设计 |
| [AI 业务动作网关](ai-business-action-gateway-tech.md) | AI 调用业务动作的网关与安全边界 | 专项技术设计 |
| [开发者商业化技术设计](developer-commercialization-tech-design.md) | 开发者能力商业化相关设计 | 专项技术设计 |

## 五层与既有专题目录

| 目录 | 内容 |
|---|---|
| [Assistant](assistant/) | 助理领域与技术设计 |
| [Team](team/) | 群体协作领域与技术设计 |
| [Agent](agent/) | Agent、运行事件与执行轨迹设计 |
| [Cognition](cognition/) | 记忆、学习、检索和个性化设计 |
| [Core](core/) | 模型、推理、置信度与核心能力设计 |
| [AI（历史专题）](ai/) | 早期 AI Service 设计；后续责任应收敛到五层或基础设施 |

## 资源文件

| 文件 | 用途 |
|---|---|
| [agent.jpg](agent.jpg) | Agent 架构示意资源 |
| [agentic-rag.jpg](agentic-rag.jpg) | Agentic RAG 示意资源 |
