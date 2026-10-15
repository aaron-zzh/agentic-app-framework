---
code: builtin-agent-delegation
name: 派生子智能体
summary: USE WHEN 协调者判断任务可拆为多个独立子任务，需要委派给多个执行者并行或串行完成。
version: 1
visibility: INTERNAL
toolAccessMode: INHERIT
changeSummary: 内置初始版本
systemBinding: ALWAYS | 112
---
# 派生子智能体

仅协调者在任务态内可用。任务涉及不同领域分工、可并行拆为独立子任务时，调用 `delegate_agents` 委派执行者。

## 入参

- `goal`：本次委派的目标
- `maxParallelism`：最大并行度
- `aggregationContract`：聚合方式，`kind` 为 `PASS_THROUGH`、`ORDERED_CONCAT` 或 `AGGREGATOR_REDUCE`；
  `AGGREGATOR_REDUCE` 必须同时提供 `requirements`（写给聚合者的聚合要求，默认上限 8000 字符）
- `executors`：执行者列表，每项含 `nodeId`、`description`、`modelMode`，可选 `roleKey`、`skillKey`、`dependsOn`、`inputBindings`。
  省略 `roleKey`/`skillKey` 即与你相同；需要其它 Role 或 Skill 时指定，必须属于当前 Assistant 的候选目录，且该
  Role 的工具集不得超出你的 Role，否则会被拒绝并返回候选目录
- `iterationGroup`：可选迭代组

## 规则

提交后协调者本轮工作结束，不需要再产出最终答案——执行者会各自独立完成分配到的子任务，系统随后汇总。调用
`delegate_agents` 之前不要输出面向用户的正文。

在每个执行者的 `description` 中写明目标、边界与期望的结果格式，便于汇总时综合。

聚合要求 `requirements` 是写给聚合者的指令，聚合者看不到你的推理过程，只会收到原始请求、你提交的目标、聚合要求、
执行者清单和各执行者结果。应写明：用户真正要什么、各执行者结果如何综合、输出风格与格式篇幅约束、需要核对的事项。
不要在其中代写最终结果。

除并行度上限外，还需判断执行者之间是否存在真实的先后关系：某个执行者的工作是否必须等待另一个执行者的产出
作为输入才能开始。只在这种真实产出-输入依赖存在时才声明该先后关系；彼此不需要对方产出即可开始的执行者应
视为独立，不得声明依赖。不要凭任务描述的表面顺序、内容类型或常见习惯（例如"文本通常先于配图"）臆断依赖，
真实依赖必须来自本次协调实际掌握的输入产出关系。
