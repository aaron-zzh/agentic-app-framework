---
level: Practice
layer: Product
purpose: 索引数字人模块的设计文档
status: active
version: 1.0.0
date: 2026-10-08
author: AaronZZH & Kiro
---

# 数字人设计

数字人是 AAF 的业务模块（后端包 `com.xuejiai.aaf.module.digitalhuman`，前端 `features/digital-human`）：用一张形象图加预设音色，经 AIGC Blueprint 生成数字人资产，再基于数字人和脚本生成营销视频。

## 文档

- [系统设计](./system.md)：业务定位、三个模块的方案选型、关键决策。其中数据库和接口草稿已改为指向权威来源。
- [API](./api.md)：接口、权限、错误码和数据模型，以代码为准。
- [前端](./frontend.md)：组件、数据流和权限展示规则。
- [视频生成](./video-generation/)：按时长路由阿里云与 Hyperframes 的执行器设计、Blueprint 部署与集成测试。
- [实施状态](./status.md)：已实现范围、已知缺口、未验证项。

## 相关目录

- [需求](../../prd/digital-human/)：产品需求文档。
- Blueprint 定义以数据库为准：种子见 `apps/service/aaf-api/src/main/resources/db/seed/v24__digital_human_seed.sql`（表 `aigc_project_blueprint`）。早期的 YAML 设计草稿已删除，可从 git 历史查看。
- 表结构：`apps/service/aaf-api/src/main/resources/db/migration/v19__digital_human_schema.sql`；权限码与种子：`db/seed/v24__digital_human_seed.sql`。
