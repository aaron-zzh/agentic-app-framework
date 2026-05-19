---
level: Practice
layer: Product
purpose: AAF-063 协调者引擎的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 协调者引擎（AAF-063）

> 设计文档：[用户感知与语义界面（AI 决策记录章节）](../../../design/apps/webui/user-awareness-semantic-ui.md) | [Auto Dev 设计](../../../design/framework/engine/auto-dev.md)
> 负责人：architect + developer-service | 创建：05-19

## 任务列表

### 协调者核心

1. [ ] #6301 协调者 Agent 实现
   - 协调者角色定义（系统 Prompt、能力边界、权限范围）
   - 流程规范知识库加载（docs/reference/ 规范文档向量化）
   - 多 Agent 调度能力（product/architect/developer/tester/qa 子 Agent 派发）
   - 风险分级评估（🟢低/🟡中/🔴高→决定派发链路）
   - verify: 协调者正确评估任务风险等级并派发对应 Agent

2. [ ] #6302 流程规范执行
   - 需求→设计→编码→审查→测试流水线自动驱动
   - 阶段门控校验（上游产出物完整性检查后才派发下游）
   - 产出物契约验证（requirement.md 有 AC、design.md 有接口定义等）
   - 阶段超时与异常处理（超时提醒、失败回退上游）
   - verify: 完整流水线自动执行，门控拦截不合格产出

### 文档与决策

3. [ ] #6303 文档自动同步
   - 代码变更→反向检测关联设计文档是否需更新
   - 规范一致性检测（代码实现 vs 设计文档 diff 分析）
   - 变更通知（文档过期提醒、关联任务标记）
   - 文档自动生成（dev-log、review.md、test-report.md 模板填充）
   - verify: 代码变更后正确识别需更新的文档并提醒

4. [ ] #6304 置信度与人工介入
   - 协调者置信度评估模型（任务复杂度 × 历史成功率 × 规范覆盖度）
   - 低置信度暂停转人工（< 0.7 暂停并展示状态快照 + 建议选项）
   - 决策记录与审计（每次派发/门控/回退记录日志）
   - 人工介入后恢复执行（人类确认→继续流水线）
   - verify: 低置信度任务正确暂停，人工确认后恢复

### 开发者交互

5. [ ] #6305 开发者对话接口
   - 开发者通过 LiveChatter 与协调者对话（任务咨询、进度汇报）
   - 任务领取（协调者分配→开发者确认→状态流转）
   - 问题反馈（开发中遇到阻塞→协调者评估→升级或调整）
   - 上下文自动注入（当前任务规范、设计文档、相关代码自动加载）
   - verify: 开发者对话中可领取任务、汇报进度、反馈问题