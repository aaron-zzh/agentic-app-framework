---
level: Practice
layer: Model
purpose: 定义多人协作的 Git 工作流程和冲突解决策略
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 协作流程

## PR 流程

创建分支 → 开发 → 本地测试 → 推送 → 创建 PR → 审查 → 合并 → 删除分支

### PR 标题格式

遵循 Conventional Commits：

```text
feat(auth): implement OAuth2 authentication
fix(api): resolve timeout issue in user service
docs: update contribution guidelines
```

### PR 描述模板

`.github/pull_request_template.md`：

```markdown
## 概述
<!-- 这个 PR 做了什么 -->

## 改动类型
- [ ] 新功能 (feat)
- [ ] 问题修复 (fix)
- [ ] 文档更新 (docs)
- [ ] 重构 (refactor)
- [ ] 其他 (chore)

## 测试方法
<!-- 如何验证这些改动 -->

## 关联任务
Closes #

## 检查清单
- [ ] 本地测试通过
- [ ] 无调试代码残留
- [ ] 相关文档已更新
```

### PR 大小建议

- 尽量保持 < 400 行，超过时考虑拆分
- 按层次拆分：数据模型 → API → 前端 → 测试

## 代码审查

- 至少 1 人审查通过后合并
- 审查重点：功能正确性、边界处理、安全漏洞、代码可读性

### 评论格式

```
[必须] 这里需要添加空值检查，否则会崩溃
[建议] 可以用 Map 代替 Object 提高查找性能
[问题] 这里为什么用同步而不是异步？
[提示] 可以用解构语法简化
```

### 作为作者回复

- 接受：`已按建议修改 ✅`
- 解释：说明选择当前方案的原因
- 延后：`好建议，已创建 #xxx 跟踪`

## 冲突解决

PR 作者负责解决冲突：

```bash
git checkout feature/my-feature
git fetch origin
git rebase origin/main
# 解决冲突后
git add <resolved-files>
git rebase --continue
git push --force-with-lease origin feature/my-feature
```

预防冲突：保持 PR 小、频繁同步 main、提前沟通大改动。

## 合并策略

| 场景 | 策略 |
|------|------|
| 小功能（< 5 commits） | Squash and merge |
| 大功能（≥ 5 commits） | Rebase and merge |
| 紧急修复 | Create a merge commit |

## 原则

- PR 保持小而专注，不要混合多个功能
- 及时响应审查评论
- 合并前确保所有 CI 检查通过
- 合并后删除源分支
