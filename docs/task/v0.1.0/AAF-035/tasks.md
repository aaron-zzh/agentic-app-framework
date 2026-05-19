---
level: Practice
layer: Product
purpose: AAF-035 Nx 工程化持续优化任务清单
status: done
version: 1.0.0
date: 2026-05-14
author: AaronZZH
---

# Nx 工程化持续优化（AAF-035）

> 参考：[Nx Monorepo 最佳实践](../../guide/development/nx-monorepo-best-practices.md)
> 前置：AAF-028 packages/ 首个共享包落地后启动
> 负责人：architect + developer-webui | 创建：05-14

## 背景

v0.1.0 已完成基础工程化（namedInputs 精细化 + pnpm.overrides 统一版本）。本故事收集 packages/ 落地后才有意义的工程化优化项，按优先级分批执行。

## 任务列表

### P0：packages/ 落地时同步做

1. ✅ #1 共享 tsconfig 包 — developer-webui
   - 创建 `packages/_config/tsconfig/`（base.json / nextjs.json / library.json）
   - 各项目 tsconfig.json 改为 extends 共享配置
   - verify: `pnpm check:affected` 全绿

2. ✅ #2 enforce-module-boundaries 激活 — architect
   - 为各项目添加 scope tag（scope:app / scope:package）
   - 在 nx.json 或 Biome 中配置依赖方向约束
   - verify: 违反方向的 import 报错

3. ✅ #3 publint + attw 发布检查 — developer-webui
   - packages/ 各包 project.json 添加 publint / attw target
   - 加入 check 依赖链
   - verify: 故意写错 exports 字段时 publint 报错

### P1：多包发布时做

4. ✅ #4 Nx Release 版本管理 — architect
   - nx.json release 配置（conventionalCommits + workspaceChangelog + GitHub release）
   - 脚本：`pnpm release`（nx release）/ `pnpm changelog`（nx release changelog）
   - verify: `pnpm release --dry-run` 正确生成版本号和 changelog

5. ✅ #5 共享 tailwind-config 包 — developer-webui
   - 创建 `packages/_config/tailwind/`（共享 preset）
   - webui 的 CSS 引用共享预设
   - verify: 主题 token 统一生效

### P2：团队规模扩大 / 性能瓶颈时评估

6. ✅ #6 lefthook 替代 husky — architect
   - 评估迁移收益（并行执行 + stage_fixed + glob 过滤）
   - 迁移 .husky/ → lefthook.yml
   - verify: pre-commit / commit-msg hook 行为不变

7. ✅ #7 Nx DTE 分布式任务执行 — architect
   - 评估 CI 时间是否超过 10 分钟阈值
   - 配置 Nx Cloud DTE agents
   - verify: CI 时间线性缩短

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
