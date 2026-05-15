---
level: Practice
layer: Model
purpose: 定义 Git 提交信息的格式、类型和脚注规范
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 提交规范

采用[约定式提交](https://www.conventionalcommits.org/zh-hans/)。

## 格式

```text
<类型>[可选 范围]: <描述>

[可选 正文]

[可选 脚注]
```

### 格式说明

- **type**: 提交类型（必需）
- **scope**: 影响范围（可选，推荐）
- **subject**: 简短描述（必需）
- **body**: 详细描述（可选）
- **footer**: 脚注信息（可选）

### 提交示例

```bash
# 简单提交
feat: add user authentication

# 带作用域，如：agent、tool、memory
feat(auth): implement JWT authentication

# 带正文
fix(api): correct response status codes

Fixed incorrect HTTP status codes in error responses.
Now returns 404 for not found and 400 for bad request.

# 带页脚（关联 issue）
feat(payment): integrate Stripe payment

Implements Stripe payment gateway for subscription plans.

Closes #123

# 破坏性变更，在类型后加 `!`，或在脚注中写 `BREAKING CHANGE:`
feat!: drop support for Node 12

BREAKING CHANGE: Node 12 is no longer supported.
Minimum required version is now Node 16.
```

## 提交类型

| 类型 | 说明 | 版本影响 | 示例 |
|------|------|----------|------|
| feat | 新功能 | Minor | feat: add dark mode support |
| fix | 错误修复 | Patch | fix: resolve login timeout issue |
| perf | 性能优化 | Patch | perf: optimize image loading |
| docs | 文档更新 | 无 | docs: update API documentation |
| style | 代码格式 | 无 | style: format code with prettier |
| refactor | 重构（不影响功能） | 无 | refactor: simplify auth logic |
| test | 测试相关 | 无 | test: add unit tests for auth |
| chore | 构建、依赖、CI 等杂项 | 无 | chore: update dependencies |
| revert | 回滚提交 | 视情况 | revert: revert commit abc123 |

## Subject 规范

- 跟在 `<类型>(范围):` 的冒号和空格之后，是对变更的简短总结
- 不超过 100 个字符
- 使用祈使语气（动词原形）
- 不要句号结尾
- 内部项目可用中文

```bash
# ✅
feat(auth): 添加微信登录支持
fix(api): correct response status codes

# ❌
feat: Added email notification.   # 过去式 + 句号
fix: Fixes race condition         # 第三人称
fix: 修了个bug                    # 描述不清
```

## 破坏性变更

```bash
# 类型后加 ! + Footer 说明（推荐）
feat!: change API response format

BREAKING CHANGE: 响应格式从 snake_case 改为 camelCase，需更新客户端。
迁移指南：https://docs.example.com/migration
```

## 原子化提交

每个提交应包含一个完整的逻辑改动，可独立编译和测试。

```bash
# ✅
git commit -m "feat(auth): add user model"
git commit -m "feat(auth): implement registration API"
git commit -m "test(auth): add registration tests"

# ❌
git commit -m "WIP"
git commit -m "大量修改"
git commit -m "临时提交"
```

## 项目提交模板

在项目根目录创建 `.gitmessage.txt`，配置 Git 使用：

```bash
git config --global commit.template .gitmessage.txt
```

模板内容：

```text
# Format: <type>(<scope>): <subject>
#
# Example: feat(auth): add login functionality
#
# Types:
# - feat:     New feature
# - fix:      Bug fix
# - docs:     Documentation
# - style:    Code style
# - refactor: Code refactoring
# - perf:     Performance improvement
# - test:     Testing
# - chore:    Maintenance tasks
# - revert:   Revert a commit
#
# Scope: module affected (optional but recommended)
#
# BREAKING CHANGE: description (in footer if needed)
# Closes: #issue-number (in footer if applicable)
```

## 最佳实践

### DO ✅

- 类型统一使用小写
- 每个提交都应该能通过测试
- 使用有意义的提交信息
- 遵循原子提交原则，提交符合多种类型时，应拆分为多次提交
- 在提交前 review 自己的改动
- 在脚注中通过 `Task:` 关联技术任务编号（如 `Task: #02801`）
- 脚注用 `-` 作连字符（如 `Reviewed-by`），`BREAKING CHANGE` 例外

### DON'T ❌

- 不要提交调试代码（System.out.println 等）
- 不要提交敏感信息（密码、密钥）
- 避免无意义的提交信息（WIP、fix、update）
- 不要在公共分支上修改历史


### 视觉/样式修复场景（v0.2 webui 有页面后生效）

视觉审计产出的修复遵循"一修一提交"原则，便于 bisect 回退：

- 每个独立 finding 一个 commit，格式：`style(<组件名>): fix FINDING-NNN <描述>`
- scope 为组件名或页面名（如 `style(ChatPanel): fix FINDING-001 adjust spacing`）
- 纯 CSS/Tailwind 变更：低风险，可批量提交（多个 finding 合一个 commit）
- 涉及 JSX/组件结构变更：逐个提交，计入正常风险预算
