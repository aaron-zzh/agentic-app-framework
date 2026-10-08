---
level: Practice
layer: Product
purpose: 索引数字人视频生成的设计、Blueprint 部署与集成测试文档
status: active
version: 1.1.0
date: 2026-10-08
author: AaronZZH & Kiro
---

# 数字人视频生成

视频生成通过 AIGC 的 `video.generate` 动作触发，数字人视频项目的该动作绑定到执行器 `digital_human_video_generation`。执行器直接消费脚本产出的场景：单场景且不超过 15 秒走阿里云，其余场景逐段调用阿里云出片后交给 Hyperframes 合成。外部接口目前是占位实现，见 [实施状态](../status.md)。

## 文档

- [执行器设计](./design.md)：绑定方式、入参契约、数字人校验、路由规则。
- [Blueprint 部署](./blueprint-deployment.md)：Blueprint 与执行绑定种子数据的部署和验证步骤。
- [集成测试](./testing.md)：集成测试用例与 SQL 校验。

## 相关目录

- [数字人设计](../)
- Blueprint 以数据库为准：`apps/service/aaf-api/src/main/resources/db/seed/v24__digital_human_seed.sql`。
