---
level: Practice
layer: Product
purpose: 索引老人回忆录平台的设计文档
status: draft
version: 0.1.0
date: 2026-11-09
author: AaronZZH & Kiro
tags:
  - 回忆录
  - 设计
---

# 老人回忆录平台设计

## 文档

| 文档 | 说明 |
|------|------|
| [智能体工作流](./agent-workflow.md) | 从线索到交付的七层流水线、每层人工卡点、指标体系与技术风险 |
| [P0 数据与接口设计](./p0-data-api.md) | 一期一图一事的数据表与接口草案；原方案 A 的对话访谈、事实卡片、人工链路部分标注为二期或后推 |
| [事实核对模块](./fact-check-engine.md) | P0-09 详细设计：数据模型与状态机、陈述抽取、证据与风险规则、核对流程、家属确认、报告模板、评测与工时 |
| [功能模块](./module-map.md) | 22 个功能模块的分层、优先级、与 PRD 和任务卡的映射、依赖主干 |

## 相关目录

- 需求文档：[老人回忆录平台](../../prd/memoir/Readme.md)
- 数字人与视频生成能力：[数字人设计](../digital-human/Readme.md)
