---
level: Practice
layer: Product
purpose: 说明 AAF 项目的目录结构、模块职责与依赖关系
status: published
version: 2.1.0
date: 2026-05-02
author: AaronZZH
scope:
  includes:
    - Nx monorepo 整体结构
    - 各应用与共享包职责
    - 文档与智能体配置结构
gains:
  - 能理解项目整体结构
  - 能快速定位代码位置
---

# AAF 项目结构说明

AAF 采用 **Nx monorepo** 管理所有应用和共享包，pnpm 作为包管理器，Maven 管理后端 Java 模块。

## 整体目录

```text
agentic-app-framework/                  # Nx monorepo 根目录
│
├── apps/                               # ── 应用目录 ──
│   ├── service/                        # Spring Boot 4 后端（Java 25）
│   ├── webui/                          # Next.js 16 前端（React 19, App Router）
│   └── uniapp/                         # UniApp 小程序/APP（待开发）
│
├── packages/                           # ── 共享库目录（待建设）──
│
├── docs/                               # ── 项目文档（Diátaxis 四象限）──
│   ├── Readme.md                       # 文档导航入口
│   ├── project-structure.md            # 本文件
│   ├── design/                         # 设计文档（架构、元引擎、模块、交互）
│   ├── explanation/                    # 概念解释（架构思想、设计原则、SDD）
│   ├── guide/                          # 操作指南（构建、配置、模块创建、AI协作）
│   ├── reference/                      # 参考规范（开发规范、内容规范、团队协作）
│   ├── prd/                            # 需求管理（用户故事、需求规格、路线图）
│   ├── task/                           # 任务管理（backlog、迭代任务、工作日志）
│   ├── api/                            # API 文档
│   ├── learn/                          # 学习资料
│   ├── tutorial/                       # 教程
│   └── tmp/                            # 临时文件
│
├── .kiro/                              # ── Kiro 智能体配置 ──
│   ├── steering/                       # 全局协作规则
│   ├── agents/                         # 智能体定义
│   ├── prompts/                        # 提示词模板
│   ├── hooks/                          # 钩子脚本
│   └── skills/                         # 技能定义
│
├── .github/                            # GitHub CI/CD 配置
│
├── AGENTS.md                           # AI 智能体协作入口文档
├── Readme.md                           # 项目主 README
├── LICENSE                             # Apache 2.0
│
├── nx.json                             # Nx 配置
├── package.json                        # pnpm 根包（Nx 22.7.0）
├── pnpm-workspace.yaml                 # pnpm workspace: apps/* + packages/*
├── pom.xml                             # Maven 根 POM（聚合 apps/service）
├── tsconfig.base.json                  # TypeScript 基础配置
└── mvnw / mvnw.cmd                     # Maven Wrapper
```

## 应用说明

| 应用 | 技术栈 | 状态 |
|------|--------|------|
| `apps/service` | Spring Boot 4.0.6, Spring AI 2.0-M4, PostgreSQL/PgVector, Neo4j, Redis, WebFlux, GraphQL, Flyway | 当前单体，规划拆分为多模块 |
| `apps/webui` | Next.js 16, React 19, TypeScript | 初始脚手架 |
| `apps/uniapp` | UniApp | 待开发 |
| `packages/` | — | 待建设，用于跨应用共享的前端组件、工具函数、类型定义 |

- 后端模块架构详见 [后端模块结构](design/apps/service/module-structure.md)
- 后端通过 `project.json` 桥接 Maven 命令为 Nx targets，统一用 `pnpm nx <target> service` 执行

## Nx Monorepo 配置

| 配置文件 | 说明 |
|---------|------|
| `nx.json` | Nx 插件：@nx/js/typescript, @nx/next/plugin, @nx/maven |
| `pnpm-workspace.yaml` | 工作区：apps/\*, packages/\* |
| `package.json` | 根包 @org/source, Nx 22.7.0 |
| `pom.xml`（根） | Maven 聚合，modules 指向 apps/service |

## 文档目录结构

采用 [Diátaxis](https://diataxis.fr/) 四象限组织，详见 [文档导航](Readme.md)。

| 目录 | 分类 | 说明 |
|------|------|------|
| `design/` | 设计 | 架构设计、元引擎、模块设计、交互设计 |
| `explanation/` | 解释 | 架构思想、设计原则、SDD 理念 |
| `guide/` | 指南 | 构建、配置、模块创建、AI 协作开发 |
| `reference/` | 参考 | 开发规范、内容规范、团队协作规范 |
| `prd/` | 需求 | 需求管理、路线图、用户反馈、改进意见 |
| `task/` | 任务 | backlog（唯一待办来源）、迭代任务、工作日志 |
