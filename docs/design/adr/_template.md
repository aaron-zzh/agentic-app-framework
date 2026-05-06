---
level: Practice
layer: Principle
purpose: ADR 模板（MADR 3.0 + AAF Front Matter）
status: template
version: 1.0.0
date: 2026-05-05
author: AaronZZH
---

<!--
使用说明：
1. 复制本文件为 ADR-{三位数}-{slug}.md，slug 使用 kebab-case
2. 替换 Front Matter 的 status 从 template → proposed / accepted
3. 填写各章节，可删除不适用的章节
4. accepted 后不再修改内容，只改 status（如 deprecated / superseded-by）
5. 在 README.md 的索引表中登记新 ADR
-->

---
status: proposed  # proposed / accepted / deprecated / superseded-by ADR-NNN
date: YYYY-MM-DD
deciders: [协调者]
consulted: []
informed: []
related-tasks: [AAF-XXX / #N]
---

# ADR-NNN: {标题}

## Context and Problem Statement

{一到两段描述当时面临的问题或触发本次决策的情境。避免只写"我们要选一个 X"，要说清楚"为什么现在必须做这个决策"。}

## Decision Drivers

- {驱动因素 1，例如"AI 协作内循环成本"}
- {驱动因素 2}
- {驱动因素 3}

## Considered Options

- {选项 A}
- {选项 B}
- {选项 C}（如适用）

## Decision Outcome

**Chosen option**: "{选项 X}"，理由：{核心论据，一到两句话}。

### Positive Consequences

- {好处 1}
- {好处 2}

### Negative Consequences

- {代价 1}
- {代价 2}

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切：

1. {触发条件 1}
2. {触发条件 2}

## Pros and Cons of the Options

### {选项 A}

- Good: {优点}
- Bad: {缺点}
- Neutral: {中性}

### {选项 B}

- Good: {优点}
- Bad: {缺点}

## More Information

- {历史讨论链接：dev-log / issue / PR}
- {相关规范文档：需要回链"起因：ADR-NNN"的条目}
- {后续动作：哪些任务需要基于本 ADR 执行}
