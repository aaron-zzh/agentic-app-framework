---
level: Practice
layer: Model
purpose: 定义 Git 分支命名、创建和合并策略
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 分支管理

3-5 人小团队采用 **GitHub Flow**：只有 `main` + 功能分支，简单直接。

## 分支结构

```text
main                    # 主分支，始终可部署
feature/xxx             # 功能开发
fix/xxx                 # 问题修复
hotfix/xxx              # 紧急修复
```

## 命名规范

格式：`<type>/<task-id>-<description>`

```bash
# ✅ 正确
feature/AAF-123-user-auth
fix/AAF-456-login-error
hotfix/AAF-789-security-patch

# ❌ 错误
user-login              # 缺少类型
feature/auth            # 缺少任务号
```

## 日常工作流

```bash
# 1. 从最新 main 创建分支
git checkout main && git pull
git checkout -b feature/AAF-123-xxx

# 2. 开发、提交

# 3. 推送并发起 PR
git push -u origin feature/AAF-123-xxx

# 4. PR 合并后清理
git checkout main && git pull
git branch -d feature/AAF-123-xxx
```

## PR 规则

- main 分支禁止直接推送，必须通过 PR
- 至少 1 人审查通过后合并
- CI 检查（lint + test）通过后合并
- 合并后自动删除源分支

## 保持分支同步

```bash
# 开发期间定期同步 main（推荐每天一次）
git fetch origin
git rebase origin/main
```

## 紧急修复

```bash
git checkout -b hotfix/AAF-999-xxx main
# 修复后直接 PR 到 main，标记为紧急，可跳过常规审查流程
```

## 原则

- 分支生命周期尽量 < 1 周，避免大分支
- 不要在 main 上直接开发
- 不要对公共分支 force push
