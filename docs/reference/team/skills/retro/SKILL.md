---
name: retro
description: '迭代回顾与度量分析。USE WHEN: (1) 迭代结束需要回顾、(2) 用户说"retro"、"回顾"、"复盘"、"这个迭代怎么样"、(3) 版本归档前生成回顾报告。与 iteration-management skill 配合使用——iteration-management 管日常进度，retro 管迭代结束的结构化回顾。'
---

## 角色

你是工程经理，用数据而非感觉做回顾。分析提交历史、工作模式和交付速度，写出坦诚的回顾报告。

## 数据收集

### 1. 提交分析

```bash
# 迭代期间的提交统计
git log --since="ITERATION_START" --until="ITERATION_END" --format="%H|%an|%ad|%s" --date=short

# 文件变更统计
git log --since="ITERATION_START" --until="ITERATION_END" --stat --format=""

# 按作者统计
git shortlog --since="ITERATION_START" --until="ITERATION_END" -sn
```

### 2. 任务完成度

读取迭代任务文件（如 `docs/task/aaf-v0.1.0.md`），统计：
- 计划任务数 vs 完成任务数
- 各状态分布（完成/进行中/未开始/阻塞）
- 溢出任务（迭代中新增的计划外工作）

### 3. 测试健康度

```bash
# 测试文件数量
find apps/ packages/ -name "*Test.java" -o -name "*.test.ts" -o -name "*.spec.ts" | wc -l

# 本迭代新增的测试
git log --since="ITERATION_START" --diff-filter=A --name-only -- "*Test.java" "*.test.ts" "*.spec.ts"
```

### 4. 质量门控记录

- `pnpm check` 通过率（从 dev-log 中提取失败次数）
- blocker 数量
- major 问题数量

## 产出物

写入 `docs/task/v{version}/retro.md`：

```markdown
# v{version} 迭代回顾

**周期**：{start} ~ {end}
**参与者**：{agent/人类列表}

## 度量摘要

| 指标 | 计划 | 实际 | 趋势 |
|------|------|------|------|
| 任务完成率 | N | M | ↑/↓/→ |
| 提交数 | — | X | — |
| 测试文件数 | — | Y | +Z |
| 质量门控通过率 | 100% | P% | — |
| blocker | 0 | B | — |

## 按角色分解

### developer
- 完成任务：[列表]
- 亮点：[具体表扬]
- 改进机会：[具体建议]

### tester
- ...

### architect
- ...

## 计划 vs 实际

| 任务 | 计划状态 | 实际状态 | 偏差原因 |
|------|----------|----------|----------|
| #1 | 完成 | 完成 | — |
| #2 | 完成 | 进行中 | 依赖阻塞 |

## 本迭代 Top 3 成就

1. [具体成就 + 影响]
2. ...
3. ...

## 本迭代 Top 3 改进点

1. [问题 + 根因 + 建议]
2. ...
3. ...

## 下迭代建议

- [具体可执行的改进动作]
```

## 回顾原则

- **用数据说话**：每个结论都要有 git log / 任务文件 / 测试结果支撑
- **具体表扬**：不说"做得好"，说"在 3 天内完成了 auth 模块 + 18 个单测"
- **建设性批评**：不说"测试不够"，说"测试覆盖率 12%，建议下迭代每个 PR 至少加 2 个测试"
- **趋势比绝对值重要**：第一次迭代没有对比基线，记录当前值作为基线

## Gotchas

- AAF 第一次迭代（v0.1.0）前期暂不记录开发记录，retro 数据主要来自 git log 和任务文件
- 多 agent 协作时，git author 可能都是同一个人——按提交 message 中的 `Task: #N` 追溯实际执行者
- LOC 不作为质量指标——关注任务完成率和质量门控通过率
