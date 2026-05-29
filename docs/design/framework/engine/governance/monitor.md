---
level: Practice
layer: Model
purpose: 监控引擎设计——AI 可观测性、指标系统、Token 统计、审计日志
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
changelog:
  - 2026-05-20 v0.1.0 | 初稿
---

# 监控引擎设计

> 监控引擎是纯观测层，只采集、分析、告警，不干预执行。执行控制（Token 预算超限介入）由元引擎负责。

## 职责边界

```text
监控引擎（观测，无副作用）          元引擎（执行控制，有副作用）
────────────────────────────────────────────────────────
采集调用链路数据                    Token 预算预估
统计 Token 消耗趋势                 超限暂停执行
暴露指标 endpoint                  置信度门控
审计日志记录                        任务调度干预
```

## AI 可观测性

### LLM 调用链路追踪

每次 LLM / Agent 调用自动采集：

| 采集项 | 说明 |
|--------|------|
| 输入 Prompt | 完整输入内容（含系统提示、上下文） |
| 输出结果 | 模型返回内容 |
| 工具调用 | 工具名、入参、出参、耗时 |
| Token 消耗 | input tokens / output tokens / total |
| 耗时 | 首 token 延迟、总耗时 |
| 模型信息 | 模型名、版本、provider |
| 关联上下文 | userId / agentId / sessionId / traceId |

### Agent 执行轨迹

Agent 每个执行步骤记录为 Span，形成完整执行树：

```text
AgentExecution (root span)
  ├── Perception（感知）
  ├── Planning（规划）
  │     └── SubTask × N
  ├── Execution（执行）
  │     ├── ToolCall: search
  │     ├── ToolCall: code_exec
  │     └── LlmCall
  └── Evaluation（评估）
```

### 可重复执行

执行轨迹记录完整入参，支持重新触发：

- 选择历史执行记录 → 查看完整输入/输出/中间状态
- 一键重新执行（原参数）或修改参数后重新执行
- 对比两次执行结果差异

### 技术实现

- 基于 OpenTelemetry 采集 Span/Metric/Log
- AgentScope Observability Studio 提供 Agent 执行可视化
- 数据存储：短期热数据（Redis）+ 长期归档（PostgreSQL）

## 指标系统

### 内置指标

| 类别 | 指标 |
|------|------|
| 业务指标 | DAU/MAU、Agent 调用量、任务成功率、平均会话轮次 |
| 性能指标 | 响应时延（P50/P95/P99）、吞吐量、错误率 |
| 成本指标 | Token 消耗趋势、模型费用分布、各 Agent 成本占比 |
| 质量指标 | 用户满意度评分、任务完成率、降级触发频率 |

### 自定义指标

- 用户通过 DSL 定义业务指标和采集规则
- 支持计数器、直方图、仪表盘三种类型
- 运行时热生效，无需重启

### 对外暴露

- 标准 `/metrics` endpoint（Prometheus 格式）
- 框架默认不内置出站上报，用户自行接入 Prometheus / Grafana / OpenTelemetry Collector
- 提供开箱即用的 Grafana Dashboard 模板（可选）

## Token 统计

> Token 预算控制（预估 + 超限介入）由元引擎负责，监控引擎只做统计分析。

| 统计维度 | 说明 |
|---------|------|
| 按用户 | 每用户 Token 消耗趋势、配额使用率 |
| 按 Agent | 各 Agent 消耗对比，识别高消耗 Agent |
| 按模型 | 各模型调用量和费用分布 |
| 按时间 | 日/周/月消耗趋势，异常峰值告警 |

## 审计日志

| 类别 | 记录内容 |
|------|---------|
| 操作审计 | 谁、何时、对什么资源、做了什么操作 |
| AI 决策审计 | Agent 自主决策记录（决策点/选项/理由/置信度） |
| 安全审计 | 异常访问、权限变更、敏感操作 |
| 结算审计 | 积分/资金流水（与结算引擎联动） |

审计日志不可篡改，保留周期按合规要求配置。

## 与其他模块的关系

| 模块 | 关系 |
|------|------|
| 元引擎 | 元引擎负责 Token 预算控制，监控引擎采集执行数据 |
| intelligent/core/token/ | Token 计量产生消耗数据，监控引擎聚合分析 |
| 结算引擎 | 结算流水同步到审计日志 |
| 文档引擎 | 执行轨迹可持久化为执行文档（存储载体），观测逻辑在监控引擎 |
