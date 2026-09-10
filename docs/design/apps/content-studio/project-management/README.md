---
level: Practice
layer: Model
purpose: 汇总通用项目管理模型的持久化与实现边界设计
status: draft
version: 1.0.0
date: 2026-09-10
author: AaronZZH & Kiro
dependencies:
  - ../general-project-management-model.md
scope:
  includes:
    - 项目核心实体与非实体内容的持久化边界
  excludes:
    - 通用项目管理完整领域模型
    - 字段级数据库与接口设计
gains:
  - 能定位项目管理持久化裁剪与真理源规则
---

# 项目管理设计

> 本目录承载通用项目管理模型的持久化与实现边界设计；完整领域语义见[通用项目管理模型](../general-project-management-model.md)。

## 文档列表

1. [通用项目管理持久化边界](./project-persistence-boundary.md) — 列出 24 个类型化核心实体表、3 个语义元数据表及非实体内容的承载方式。
