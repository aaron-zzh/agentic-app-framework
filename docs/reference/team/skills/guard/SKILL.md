---
name: guard
description: '危险操作防护与编辑范围锁定。USE WHEN: (1) 在生产环境附近工作、(2) 调试时需要限制编辑范围防止误改、(3) 执行破坏性命令前、(4) 用户说"小心点"、"be careful"、"锁定范围"、"freeze"。自动激活于 root-cause-investigation 调试期间。'
---

## 两层防护

### 1. 危险命令警告（careful 模式）

以下命令执行前必须警告并等待确认：

| 命令模式 | 风险 |
|----------|------|
| `rm -rf` / `rm -r` | 递归删除 |
| `DROP TABLE` / `DROP DATABASE` / `TRUNCATE` | 数据丢失 |
| `git push --force` / `git push -f` | 历史覆写 |
| `git reset --hard` | 丢弃提交 |
| `git checkout .` / `git restore .` | 丢弃未提交工作 |
| `kubectl delete` | 生产资源删除 |
| `docker system prune` | 容器/镜像丢失 |
| `flyway repair` / `flyway clean` | 数据库迁移状态破坏 |

**白名单**（不警告）：
- `rm -rf node_modules` / `dist` / `.next` / `__pycache__` / `build` / `coverage` / `target`
- 构建产物清理是常规操作

**警告格式**：
```
⚠️ 危险操作：[命令]
影响：[具体后果]
可逆性：[是/否/部分]
确认执行？(y/N)
```

### 2. 编辑范围锁定（freeze 模式）

限制文件编辑到指定目录，防止调试时误改无关代码。

**激活**：
```
锁定编辑范围到 src/module/chat/
```

**行为**：
- 仅允许编辑指定目录下的文件
- 尝试编辑范围外文件时阻止并提示
- 读取不受限制（需要读其他文件来理解上下文）

**解除**：
```
解除编辑锁定
```

## 与 AAF 协作规范对齐

guard skill 机械化执行以下已有硬约束：

| 协作规范条目 | guard 行为 |
|-------------|-----------|
| ≥5 文件改动需协调者评估 | 当 diff 涉及 ≥5 文件时警告 |
| 改 `aaf-common` 需评估 | 编辑 `aaf-common/` 下文件时警告 |
| 改接口签名需同步使用方 | 检测到方法签名变更时警告 |
| 规范文档只能协调者修改 | 阻止编辑 `docs/reference/` |
| 不直接 push main | 检测到 `git push` 目标为 main/master 时阻止 |

## 自动激活场景

- `root-cause-investigation` skill 激活时，自动锁定编辑范围到受影响模块
- 检测到当前分支为 `main`/`master` 时，自动启用 careful 模式

## Gotchas

- freeze 只阻止文件编辑工具，不阻止 shell 命令中的 `sed`/`awk`——是防误操作不是安全沙箱
- AAF monorepo 中 `apps/service/` 和 `apps/webui/` 是独立部署单元，调试时通常只需锁定其中一个
- Flyway 命令（`repair`/`clean`）在 AAF 中特别危险——会破坏迁移历史
