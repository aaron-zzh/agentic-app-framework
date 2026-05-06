---
level: Practice
layer: Model
purpose: 所有开发者（人工 + AI）的统一规范入口
status: published
version: 1.0.0
date: 2026-05-03
author: AaronZZH
---

# 开发者统一规范

> 所有开发者（人工或 AI 生成代码）必须遵守本规范。本文档是规范索引，具体内容见各链接文档。

## 核心约束（必须遵守）

- **架构分层**：依赖方向严格向内，禁止反向依赖 → [架构约束](architecture-constraints.md)
- **编码风格**：命名、包结构、异常处理、日志规范 → [编码风格规范](apps/service/coding-style-standard.md)
- **提交规范**：约定式提交，原子化，关联技术任务 → [提交规范](git/commit-standard.md)
- **开发日志**：编码过程中记录实现决策，完成后补充 → [dev-log 模板](../../task/_template/dev-log.md)

## 规范索引

| 规范 | 说明 | 适用范围 |
|------|------|---------|
| [架构约束](architecture-constraints.md) | 依赖方向、模块边界、分层纪律 | 全端 |
| [编码风格规范](apps/service/coding-style-standard.md) | 命名、包结构、异常、日志 | 后端 |
| [领域建模规范](apps/service/domain-modeling-standard.md) | DDD 实体、聚合、仓储设计 | 后端 |
| [单元测试规范](test/unit-test-standard.md) | JUnit 5 + Mockito | 后端 |
| [验收测试规范](test/acceptance-test-standard.md) | Cucumber Gherkin | 全端 |
| [集成测试规范](test/integration-test-standard.md) | 模块间集成测试 | 后端 |
| [提交规范](git/commit-standard.md) | 约定式提交格式 | 全端 |
| [分支管理规范](git/branch-manage-standard.md) | 分支命名与合并策略 | 全端 |
| [后端模块结构](../../design/apps/service/module-structure.md) | Maven 模块结构与包规范 | 后端 |

## 开发流程要点

1. **开发前**：确认任务在 `tasks.md` 中，需求文件和设计文档已就绪
2. **开发中**：遇到与设计不符的情况，随时记录到 `dev-log.md`
3. **开发后**：补充 `dev-log.md`，编写单元测试，自测冒烟通过后提交
4. **提交时**：遵循提交规范，脚注关联技术任务编号 `Task: #N`
