---
level: Practice
layer: Principle
purpose: 架构决策记录（ADR）索引
status: active
version: 1.0.0
date: 2026-05-05
author: AaronZZH
scope:
  includes:
    - 架构与技术选型决策的权威记录
    - 决策上下文、驱动因素、候选方案对比、反向选择触发条件
gains:
  - 查找某项技术选型/架构决策的历史背景和理由
  - 添加新决策时有可对齐的 MADR 格式模板
  - 硬约束规则回链到决策起因（multica P1.5 规则溯源）
changelog:
  - 2026-05-05 初始化 ADR 目录 + ADR-001/002/003（迁移自 AAF-023 dev-log）
---

# 架构决策记录（ADR）

> 本目录是 AAF 项目架构与技术选型决策的**权威真理源**，采用 [MADR](https://adr.github.io/madr/) 格式。

## 为什么需要 ADR

- **规则溯源**：规范硬约束可以回链到具体决策起因（例如 `起因：ADR-001`），让规则"为什么是这样"可追溯
- **决策考古**：新成员加入、六个月后回看、AI 上下文加载时，可以一眼读到"当初为什么选 A 不选 B"
- **反向选择触发条件**：每条 ADR 记录"什么时候可能回切"的触发条件，避免陷入历史包袱
- **避免真理源漂移**：决策散落在 dev-log 时易被忘却，提取为独立 ADR 后引用路径稳定

## 格式与流程

1. 新建 ADR 复制 [`_template.md`](_template.md)，编号 `ADR-{三位数}`
2. 文件命名 `ADR-NNN-{slug}.md`，slug 使用 kebab-case
3. status 字段变化：`proposed` → `accepted` → 可能的 `deprecated` / `superseded-by ADR-XXX`
4. **一旦 accepted 不得修改内容**（除了 status），需要改动新建一条 superseded ADR
5. 影响的规范硬约束回链到本 ADR，格式：`起因：ADR-NNN`（渐进补充，无明确起因的条目暂不补）

## 索引

| 编号 | 标题 | 状态 | 影响范围 |
|------|------|------|---------|
| [ADR-001](ADR-001-vitest-vs-jest.md) | 前端测试框架选型：Vitest vs Jest | accepted | 前端单测工具链、AAF-023 #6 |
| [ADR-002](ADR-002-local-env-vs-testcontainers.md) | 后端测试环境：本地真实 DB vs Testcontainers | accepted | 后端测试环境、CI 配置、AAF-023 #8 |
| [ADR-004](ADR-003-virtual-threads-over-webflux.md) | 全量 Virtual Threads + JDBC，放弃 WebFlux + R2DBC | accepted | 后端并发模型、依赖清理、编码规范、AAF-023 #17 |

## 与其他文档的关系

| 文档位置 | 定位 |
|---------|------|
| `docs/design/adr/`（本目录） | **权威决策源**，一次记录永久保留 |
| `docs/task/v0.*/AAF-XXX/dev-log.md` | **历史执行记录**，决策讨论过程可留存但指针指向 ADR |
| `docs/reference/**/*-standard.md` | **规范结论**，相关条目回链 ADR（渐进补） |
| `docs/prd/improvements.md` | **改进意见池**，部分采纳项最终转为 ADR |

## 添加新 ADR 的触发条件

出现以下任一情况应立即写 ADR：

- 技术选型决策（选框架 / 选库 / 选数据库 / 选协议）
- 架构层面的取舍（单体 vs 微服务、同步 vs 异步、SSE vs WebSocket 等）
- 重要的"不做什么"决定（如本次 ADR-003 移除 Cucumber）
- 跨模块的规则或约束首次确立
- 规范硬约束的起因性决策（反过来让规范条目可回链本 ADR）

不需要写 ADR 的情况：

- 纯执行类的任务（写入 dev-log 即可）
- 规范条款的文字润色或重构（写入 commit message）
- 单模块内的实现细节选择（写入 design.md）
