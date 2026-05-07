---
level: Practice
layer: Principle
purpose: 前端单测框架选型决策
status: accepted
version: 1.0.0
date: 2026-05-05
author: AaronZZH
deciders: [AaronZZH, 协调者]
---

# ADR-001: 前端测试框架选型 — Vitest vs Jest

## Context and Problem Statement

AAF-023 #1 一键 check 基础设施落地时已安装 `vitest@3.1 + jsdom@25 + @vitest/coverage-v8` 作为前端单测运行器。随后收到"为什么不用 Jest"的质疑，并给出一组包含 `@nx/jest@22.3.3` 的参考依赖。需要在继续建设前端测试栈前明确决策：继续 Vitest，还是回切 Jest。

决策会影响：

- `apps/webui/vitest.config.ts` 是否保留
- 是否引入 `@nx/jest` / `@nx/vite`
- 后续所有前端项目（webui-e2e、packages/ui）的测试栈
- `docs/reference/dev/test/unit-test-standard.md` 的前端技术栈描述

## Decision Drivers

- 速度：AI 协作内循环依赖快速反馈（毫秒级 vs 秒级直接影响一次对话能否完整走完）
- 未来红利：Rolldown / Turbopack 等现代打包器的生态倾向
- Nx 默认推荐：减少与平台惯例的摩擦
- ESM / TypeScript 原生支持：减少配置层，降低"配置即 bug"风险
- 现代主流框架的生态默认选择

## Considered Options

- Vitest（已安装 3.1）
- Jest（需装 `@nx/jest`、`jest@29` 等）

## Decision Outcome

**Chosen option**: "Vitest"，理由：在速度、现代栈适配、未来发展、Nx 默认选择四个维度胜出；Jest 只在"社区示例数量"一个维度胜出，但这个优势在 AI 协作场景下持续缩小。

### Positive Consequences

- 冷启动与 watch 模式毫秒级，AI 协作内循环反馈快
- 原生 ESM / TypeScript 支持零配置
- 与 Rolldown / Turbopack 未来生态红利直接挂钩
- 与 Vue / Nuxt / Svelte / Solid / Astro / Remix / Storybook 8 等现代框架默认选择对齐
- `@nx/vite`（可选引入）能自动推断 test target，减少 `project.json` 手写量

### Negative Consequences

- 社区示例数量少于 Jest（但 AI 生成质量差异可忽略）
- 历史遗留文档或课程更多引用 Jest 的 API 习惯（mock hoisting 等），新人可能有短暂适应期
- 若未来团队出现 React Native 开发者，RN 生态仍以 Jest 为主

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切 Jest：

1. 存量 100+ 个 Jest 测试文件需要迁移（AAF 当前 0 个）
2. 重度依赖 `jest.mock` 的 hoisting 行为（AAF 不涉及）
3. 团队纳入 React Native 场景（v0.1-v0.3 无此规划）
4. 依赖 `jest-expo` 等 RN 生态特化扩展

## Pros and Cons of the Options

### Vitest

| 维度 | 评估 |
|------|------|
| 未来发展势头 | 陡增，3-4 个月一个大版本 |
| Nx 22.x 默认推荐 | `@nx/vite`（新项目默认） |
| 主流框架默认 | Vue / Nuxt / Svelte / Solid / Astro / Remix / Storybook 8 |
| ESM 原生 | 是 |
| TS 支持 | 原生 esbuild 零配置 |
| 冷启动 / watch 速度 | 毫秒级 |
| 错误输出 source map 准确度 | 高 |
| 生态示例数量 | 中（追赶中） |
| 与 Rolldown / Turbopack 未来红利 | 可直接吃到 |

### Jest

| 维度 | 评估 |
|------|------|
| 未来发展势头 | 平稳，12-18 个月一版 |
| Nx 22.x 推荐 | `@nx/jest`（保留但非默认） |
| 主流框架默认 | React Native、部分 Next.js 历史示例 |
| ESM 原生 | 实验性 |
| TS 支持 | 需 `@swc/jest` 或 `ts-jest` 额外层 |
| 冷启动 / watch 速度 | 秒级 |
| 错误输出 source map 准确度 | 中 |
| 生态示例数量 | 高（历史积累） |
| 与 Rolldown / Turbopack 未来红利 | 不受益 |

## More Information

### 历史讨论

- 原始决策记录：[AAF-023 dev-log #2](../../task/v0.1.0/AAF-023/dev-log.md#2-vitest-vs-jest-决策记录)
- 相关 improvements 条目：`docs/prd/improvements.md` 中"Vitest vs Jest 技术选型决策"（已采纳）

### 后续动作

- AAF-023 #6 前端测试栈对齐：保留 Vitest，新增 Playwright 作为 E2E runner，删除 `vitest.acceptance.config.ts`
- 拒绝引入 `@nx/jest`
- Storybook（v0.2 再议）默认走 Vitest 运行 Story 测试
- `docs/reference/dev/test/unit-test-standard.md` 前端测试工具章节补"起因：ADR-001"标注

### 与 ADR-003 的关系

ADR-003（移除 Cucumber）延续了"真理源归一"原则，但作用于后端 JUnit 5 / Cucumber 的选择，与本 ADR 的前端选型彼此独立、方向一致。
