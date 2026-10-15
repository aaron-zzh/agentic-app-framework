---
code: builtin-step-planning
name: 按步骤推进
summary: USE WHEN 任务需要多个有序步骤、但不需要多个智能体分工，在本次回复内逐步完成。
version: 1
visibility: INTERNAL
toolAccessMode: INHERIT
changeSummary: 内置初始版本
systemBinding: ALWAYS | 111
---
# 按步骤推进

同一份工作需要多个有序步骤、且不需要多个智能体分工时使用。全程在本次回复内完成，不新增执行者。

1. 调用 `plan_steps` 提交计划：目标 `goal`、风险自评 `risks`、验证方式 `verification`，以及有序步骤 `steps`
   （每步包含 `stepKey`、`title`、`instruction`，可选 `dependencies`、`requiredTools`、`completionCriteria`）。
   提交即批准，就地进入执行，不需要等待额外确认
2. 开始某个步骤前调用 `report_step` 上报 `STARTED`；完成后上报 `COMPLETED`（可带 `resultRef`）；无法完成时
   上报 `FAILED` 并给出 `failureCode`
3. 严格按步骤声明的顺序与依赖逐条上报，不要跳过，也不要提前上报依赖未满足的步骤
4. 提交计划后在同一次回复内完成全部步骤，不要中途转而派生执行者；全部完成后给出最终回复

没有任务的对话态不支持提交步骤计划；需要多步骤持久执行时先升级为任务。
