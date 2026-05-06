---
level: Practice
layer: Model
purpose: Git 工作流规范目录索引
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# Git 工作流规范

## 规范定位

本规范定义了 Git 版本控制的使用标准，包括分支管理、提交规范、协作流程等，确保团队协作的高效性和代码历史的可追溯性。

## 核心原则

原子化提交、规范提交信息、主分支保护、代码审查、自动化质量检查，确保历史清晰可追溯、代码安全可回滚。分支策略明确、流程标准化，支持不同项目类型和渐进式采用。

## 规范架构

本规范包含以下章节：

1. **[分支管理](./branch-manage-standard.md)** - 分支模型、命名规范、保护策略
2. **[提交规范](./commit-standard.md)** - Conventional Commits 标准和最佳实践
3. **[协作流程](./collaboration-guide.md)** - PR 流程、代码审查、冲突解决
4. **[版本发布](./release-standard.md)** - 版本标签、发布流程、回滚策略
5. **[工具配置](./tool-guide.md)** - Git Hooks、CI 集成、自动化工具

## 快速参考

### 分支类型

```bash
feature/JIRA-123-user-auth  # 新功能
fix/JIRA-456-login-bug      # 问题修复
hotfix/JIRA-789-security    # 紧急修复
release/1.2.0               # 发布分支
```

### 提交格式

```bash
# 标准格式
<type>(<scope>): <subject>

# 示例
feat(auth): add OAuth2 login support
fix(ui): resolve button alignment issue
docs(api): update REST API documentation
```

### 常用命令

```bash
# 创建功能分支
git checkout -b feature/JIRA-123-description

# 规范的提交
git commit -m "feat: add user authentication"

# 更新分支
git pull --rebase origin main

# 推送分支
git push origin feature/JIRA-123-description
```

## 工作流选择指南

### GitHub Flow（推荐用于应用）

```text
main → feature → PR → review → merge → deploy
```

- 适合：Web 应用、微服务、持续部署项目
- 特点：简单、快速、持续集成

### Git Flow（推荐用于库）

```text
main ← release ← develop ← feature
     ↑                    ↑
     └──── hotfix ────────┘
```

- 适合：SDK、组件库、需要版本管理的项目
- 特点：严格、稳定、版本控制

## 工具生态

### 必需工具

- **Git**: 2.28+ （支持 init.defaultBranch）
- **Git Hooks Manager**: Lefthook 或 Husky
- **Commitlint**: 提交信息格式验证

### 推荐工具

- **GitLens**: VSCode 扩展，增强 Git 功能
- **Conventional Changelog**: 自动生成 CHANGELOG
- **Semantic Release**: 自动化版本发布

## 预期效果

通过执行本规范，预期达到：

- 清晰可追溯的提交历史
- 减少代码冲突和合并问题
- 提高代码审查效率
- 加快问题定位和修复
- 支持自动化发布流程
