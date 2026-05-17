# AAF v0.1.0 迭代

> 目标：搭建配置驱动的结构化视图引擎——以 EntityDef 为核心，实现"注册配置即生成完整 CRUD 应用"的前后端框架。

## 一级用户故事

| 编号 | 名称 | 状态 | 依赖 |
|------|------|------|------|
| AAF-023 | 项目基础框架搭建 | ⏳ 后端完成，前端进行中 | 无 |
| AAF-028 | 视图引擎核心 | ✅ 已完成 | AAF-023 |
| AAF-029 | 数据交互层 | [ ] 待开始 | AAF-028 |
| AAF-030 | 表单引擎 | [ ] 待开始 | AAF-028 |
| AAF-031 | 协作与通知 | [ ] 待开始 | AAF-028 |
| AAF-032 | 权限与流程 | [ ] 待开始 | AAF-029, AAF-030 |
| AAF-033 | 平台能力 | [ ] 待开始 | AAF-032 |
| AAF-021 | Auto Dev 平台 | [ ] 待开始 | AAF-023 |
| AAF-034 | 企业 Landing Page | [ ] 待开始 | AAF-023 |
| AAF-035 | Nx 工程化持续优化 | [ ] 待排期 | AAF-028 |
| AAF-024 | 协作基础设施优化 | ✅ 已完成 | AAF-023 |
| AAF-026 | 对外文档站点 | ✅ 已完成 | AAF-023 |

## 目录结构

每个用户故事一个文件夹，包含该故事的所有产出物：

```text
v0.1.0/
├── Readme.md              ← 本文件（迭代索引）
├── AAF-023/               ← 项目基础框架搭建
│   ├── tasks.md
│   └── dev-log.md
├── AAF-024/               ← 协作基础设施优化
│   └── dev-log.md
├── AAF-026/               ← 对外文档站点
│   ├── requirement.md
│   ├── design.md
│   ├── tasks.md
│   └── test-report.md
├── AAF-028/               ← 视图引擎核心
├── AAF-029/               ← 数据交互层
├── AAF-030/               ← 表单引擎
├── AAF-031/               ← 协作与通知
├── AAF-032/               ← 权限与流程
├── AAF-033/               ← 平台能力
├── AAF-034/               ← 企业 Landing Page
└── AAF-035/               ← Nx 工程化持续优化
```

## 已归档用户故事

以下故事在 2026-05-13 重规划时合并到 AAF-028~033：

| 原编号 | 原名称 | 合并到 |
|--------|--------|--------|
| AAF-018 | 开源框架授权控制 | AAF-032 |
| AAF-019 | 文档管理系统 | AAF-029 |
| AAF-020 | 聊天协作界面 | AAF-033 |
| AAF-022 | 用户认证与访问控制 | AAF-032 |

## 相关文档

- [迭代任务计划](../aaf-v0.1.0.md)
- [Backlog](../backlog.md)
- [路线图](../../prd/roadmap.md)
- [结构化交互模式设计](../../design/apps/webui/interaction-mode-structured-view.md)

## 设计文档覆盖情况

| 故事 | 覆盖度 | 已有文档 | 待补充 |
|------|--------|---------|--------|
| AAF-028 | ✅ 100% | interaction-mode-structured-view.md + directory-structure.md + tech-stack.md | — |
| AAF-029 | ⚠️ 95% | 同上 + realtime-data-strategy.md | `apps/service/generic-crud-api.md`（通用 CRUD API 协议） |
| AAF-030 | ✅ 100% | 同上 + rich-text-editor.md + custom-fields.md | — |
| AAF-031 | ⚠️ 90% | 同上 + change-history-design.md | 消息中心/待办数据模型（可内联到开发时补） |
| AAF-032 | ⚠️ 70% | permission-ui.md + access-control.md | `apps/service/workflow-integration.md`（Flowable 集成）<br>`apps/service/automation-engine.md`（自动化规则引擎）<br>`framework/security/data-permission.md`（行级权限实现） |
| AAF-033 | ⚠️ 85% | chat-livechat-module.md + interaction-modes.md | `apps/webui/ai-awareness-service.md`（AI 感知实现方案） |

**结论**：028/030 可直接开发；029/031/033 缺口小可边开发边补；**032 需在开发前集中补充 3 份后端设计文档**。
