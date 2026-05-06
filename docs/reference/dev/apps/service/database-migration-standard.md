---
level: Practice
layer: Model
purpose: 定义数据库迁移脚本的编写、命名和执行规范
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 数据库迁移规范

> 定义数据库 schema 变更的迁移流程和安全要求。

## 工具

- Flyway（Spring Boot 集成）
- 迁移脚本路径：`src/main/resources/db/migration/`

## 命名规则

```
V{版本号}__{描述}.sql
```

示例：`V1.0.1__add_user_email_column.sql`

## 编写要求

- 每个迁移脚本必须可重复执行或幂等
- 必须提供回滚方案（单独的回滚脚本或说明）
- 禁止在迁移脚本中删除列或表（先标记废弃，下个版本再删）
- 大表变更需评估锁表影响

## 审核

- 🔴 所有迁移脚本必须人类审核后才能执行
- 测试环境先验证，通过后再应用到生产

## 产出

- 迁移脚本：`db/migration/` 下
- 回滚方案：记录在迁移脚本注释或 dev-log.md 中
