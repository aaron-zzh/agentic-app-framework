---
level: Practice
layer: Model
purpose: Layer 3 助理层 Assistant——面向人的交互入口功能设计
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# 助理层 Assistant 功能设计

> 会话级，面向人的交互入口，核心编排单元。

## 定位

Assistant 是用户唯一交互入口。持有人格、角色、技能路由，负责意图理解后调度 Agent 执行。支持多实例 fork 并行加速。

## 核心能力

- **前注意分流**：规则+小模型快速路由（<50ms），简单请求不走 Agent
- **意图理解**：澄清意图优先于执行，通过最少问题快速收敛
- **情感感知**：识别用户情绪，动态调整回应风格和信息密度
- **技能匹配**：根据意图+关键词匹配最合适的执行路径
- **Agent 调度**：选择 Agent + 并发派发 + 结果聚合
- **多实例并行**：同 Actor + 多 Role fork 子实例，主实例协调聚合
- **输入缓冲**：执行期间接收用户追加输入（取消/修改/补充/无关）
- **记忆管道编排**：按 MemoryStrategy 决定从哪些源拉取上下文
- **置信度门控**：>0.9 自动 / 0.7-0.9 确认 / <0.7 转人工

## 组成结构

```text
Assistant = Actor + Role + MemoryStrategy

Actor（人格载体）：可复用·跨 Role
Role（能力配置）：Skill 集 + Tool 白名单，可复用·跨 Actor
MemoryStrategy：MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL
```

## 内置技能

| 技能 | 说明 |
|------|------|
| 自我认知 | 助手了解自己的能力边界 |
| 用户理解 | 定期分析用户行为，更新画像 |
| 自学习 | 从执行结果中提取经验 |
| 技能创建 | 高频模式自动生成新技能 |

## 认知循环

```text
情感感知 → 意图理解 → 上下文构建 → Agent 调度 → 反馈整合 → 记忆更新
```

## 多实例并行

- 主实例（协调者）长驻，绑定用户会话
- 子实例临时创建，执行完销毁，不池化
- fork 时拷贝主实例上下文只读快照
- 主实例 merge 子实例结果 + 冲突裁决

## 相关文档

- [技术方案 — Assistant](assistant-tech.md)
- [五层智能架构总览](../architecture.md)
- [Actor 模型](actor.md)
- [用户感知与个性化](../cognition/personalization.md)
