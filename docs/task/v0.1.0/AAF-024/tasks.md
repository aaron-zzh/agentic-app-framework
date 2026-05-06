---
level: Practice
layer: Product
purpose: AAF-024 协作基础设施优化的技术任务清单
status: active
version: 1.0.0
date: 2026-05-05
author: AaronZZH
---

# 协作基础设施优化（AAF-024）

> 需求：协作基础设施优化（详见 [aaf-v0.1.0.md 业务需求](../../aaf-v0.1.0.md#协作基础设施优化)）
> 依据：[开发流程及协作规范分析 2026-05-05](dev-log.md)
> 负责人：协调者 + architect | 创建：05-05

## 任务列表

> 历史编号连续性：#10 真理源归一 + #11 派发触发条件已在本任务独立 dev-log 记录为第 1、3 节；此清单继续从 #12 开始。

1. [x] ✅ #12 架构师与 QA 审查边界合并 — 协调者
   - architect 负责"做对了吗"（代码/设计/架构约束/安全）
   - qa 只负责"过程对了吗"（流程合规/文档完整性/度量），不查代码内容
   - 改动：`roles/qa.md`、`process-audit-standard.md`、`code-review-standard.md` 边界明确；顺手新建 `_template/process-audit.md` 模板，qa 产出路径从 `review.md` 改为 `process-audit.md` 解除与 architect 的文件冲突
   - verify: 两份规范不再重复"规范合规检查"；审查职责无交叉 ✅（边界矩阵见 [dev-log #12](dev-log.md#12-架构师与-qa-审查边界合并)）

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->
<!-- 完成任务时标注负责人：✅ #N 任务描述 - {agent} -->

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入。
