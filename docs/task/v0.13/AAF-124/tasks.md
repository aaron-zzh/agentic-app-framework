---
level: Practice
layer: Model
purpose: AAF-124 助理执行过程可观测与智能体评测的技术任务
status: active
version: 1.0.0
date: 2026-11-08
author: AaronZZH & Kiro
---

# AAF-124 技术任务

调研与推荐方案：[research.md](research.md)

一期（本文件当前范围）为 🟡 中风险：只改日志与学习失败语义，不新增依赖、不新增 `ExecutionEventType`、不改接口签名。二期 OTel、三期评测为 🔴，需 architect 设计并经人类审核后再拆任务。

## 一期

- [x] **#12401** 执行轨迹摘要日志、失败收口打印堆栈、长期记忆学习尽力而为 — developer-service
  - 每次助理执行（DIRECT、Task、TaskNode）结束时，在 INFO 级别打印一段按执行分组的轨迹摘要，覆盖：执行模式与结果、耗时、角色、技能（命中或未命中）、工具名单、L1 上下文（记忆与知识库命中数）、Prompt 规模、每次模型调用（模型、首字耗时、总耗时、token）、工具调用（名称、耗时、成败）、任务提升与拆分、子节点与 Worker 角色、完成验证结果、执行后学习（短期会话、长期记忆候选与写入数、失败原因）。
  - 摘要只记录名称、数量、耗时、长度，不记录 Prompt、参数、工具结果、记忆正文；正文类信息保留在 DEBUG。
  - 摘要输出由配置开关控制，默认开启；一次执行只打印一个日志块，避免与其他请求日志交错。
  - 失败收口改为 WARN，打印完整堆栈。
  - `memoryGovernance.learn` 改为尽力而为：失败打 WARN 并记入摘要，不改变本轮执行结果。
  - 不新增 `ExecutionEventType`，不改 AG-UI 事件与前端。
  - 单测覆盖摘要聚合（正常、工具调用、失败、学习失败）与学习失败不影响结果。

- [ ] **#12402** 定位并修复长期记忆学习的 `PSQLException: 查询没有传回任何结果` — developer-service
  - 依赖 #12401 拿到堆栈后定位具体 SQL，再修复。
