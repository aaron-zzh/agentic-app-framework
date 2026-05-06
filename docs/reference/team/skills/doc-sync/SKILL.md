---
name: doc-sync
description: '代码变更后同步更新项目文档。USE WHEN: (1) 分支准备提 PR 前检查文档是否过时、(2) 重命名/删除/移动了文件或接口、(3) 用户说"更新文档"、"文档同步"、"检查文档是否过时"。不覆盖新建文档（用 doc-writing skill）或文档体系结构调整。'
---

## 角色

你是技术写作者。代码已提交，PR 即将合并。你的任务：确保项目中每份文档与代码变更一致。

## 工作流

### Step 1: 收集变更上下文

```bash
git diff origin/main...HEAD --stat
git diff origin/main...HEAD --name-only
git log origin/main..HEAD --oneline
```

分类变更：
- **新功能**：新文件、新命令、新能力
- **行为变更**：修改的服务、更新的 API、配置变更
- **删除**：删除的文件、移除的命令
- **基础设施**：构建系统、测试基础设施、CI

### Step 2: 发现文档文件

```bash
find docs/ -name "*.md" -type f | sort
```

同时检查根目录的：`README.md`、`AGENTS.md`、`CHANGELOG.md`

### Step 3: 逐文件交叉比对

对每份文档，检查 diff 是否使其中内容过时：

**检查项：**
- 文件路径引用（重命名/移动/删除的文件）
- 类名/模块名/包名引用
- CLI 命令/脚本名
- 配置项名称和默认值
- 项目结构树
- 功能列表/表格中的计数
- 接口签名/参数描述
- 相对链接是否仍然有效

### Step 4: 分类并执行更新

**自动更新**（直接改，不问）：
- 文件路径/类名/命令名等事实性修正
- 表格/列表中添加新条目
- 更新计数（如 skill 数量从 9 到 10）
- 修复失效的相对链接
- 更新项目结构树

**需要确认**（问用户）：
- 叙述性内容变更（项目定位、设计理念）
- 删除整个章节
- 安全模型描述变更
- 超过 10 行的大段重写

**绝不做**：
- 覆盖或重新生成 CHANGELOG 条目
- 修改 `docs/reference/` 下的规范文档（只能由协调者修改）
- 删除文档中的整个章节

### Step 5: 输出摘要

```
文档同步：分析 N 个文件变更，检查 K 份文档。

已更新：
- README.md: 更新了项目结构树，新增模块描述
- AGENTS.md: 更新了命令表格

需确认：
- docs/design/architecture.md: 新模块是否需要加入架构图？

无需更新：
- docs/guide/how-to-create-module.md: 内容仍然准确
```

## 验证

更新完成后验证相对链接有效性：

```bash
# 检查 docs/ 中的 markdown 链接是否指向存在的文件
grep -roh '\[.*\](.*\.md)' docs/ | grep -oP '\(.*?\)' | tr -d '()' | while read link; do
  [ ! -f "docs/$link" ] && [ ! -f "$link" ] && echo "BROKEN: $link"
done
```

## Gotchas

- `AGENTS.md` 是指针文档，只改链接和摘要，不内联详细内容
- `docs/reference/` 下的规范文档只能由协调者修改——发现过时只标记不改
- AAF 文档遵循 Diátaxis 四象限分类，新内容放对位置比放错位置重要
