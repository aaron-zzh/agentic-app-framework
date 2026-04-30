# 上下文管理

本文档说明 Kiro 的上下文管理机制，以及本项目如何利用这些机制为智能体提供精准上下文。

官方文档：[Context Management](https://kiro.dev/docs/cli/chat/context/)

## 四层上下文策略

Kiro 提供四种上下文方式，按加载时机和持久性分层：

| 层级 | 配置方式 | 加载时机 | 持久性 | 适用场景 |
|------|---------|---------|--------|---------|
| Steering | `.kiro/steering/*.md` | 所有智能体自动加载 | 跨会话持久 | 全局规范：协作流程、提交规范、通用约定 |
| Agent Resources | agent `resources` + `file://` | 指定智能体启动时全量加载 | 跨会话持久 | 角色必备：核心源码、编码标准、项目配置 |
| Skills | agent `resources` + `skill://` | 启动时加载元数据，内容按需加载 | 跨会话持久 | 按需参考：最佳实践、排查指南、可复用工作流 |
| Knowledge Base | `knowledgeBase` 配置或 `/knowledge add` | 搜索时才加载 | 跨会话持久 | 大数据集：大型代码库、海量文档 |

此外还有 **Session Context**（`/context add`），始终加载但仅当前会话有效，适合临时文件和快速实验。

### 决策原则

1. 内容超过 10MB 或包含大量文件 → **Knowledge Base**
2. 所有智能体都要看 → **Steering**（不用每个智能体单独配）
3. 某个角色每次都需要 → **Agent Resources**（`file://`，始终在上下文中）
4. 偶尔才用的参考 → **Skills**（`skill://`，省上下文空间）
5. 临时用一下 → **Session Context**（`/context add`）

### 重要限制

- 上下文文件上限为模型上下文窗口的 **75%**，超出时自动丢弃最旧的文件
- 默认 agent（kiro_default）自动加载 `.kiro/skills/` 下的所有 skill
- 自定义 agent（如 developer-xxx、tester）**不会**自动加载 skill，需要在 `resources` 中显式添加 `skill://` URI
- 上下文溢出时会触发 **Compaction**（自动摘要旧消息），可通过 `/compact` 手动触发

## Skill 详解

Skill 是遵循开放 [Agent Skills](https://agentskills.io) 标准的可移植指令包，可跨工具和团队共享。完整规范见 [Agent Skills Specification](https://agentskills.io/specification)。

### 渐进式披露（Progressive Disclosure）

1. **元数据**（~100 tokens）— 启动时只加载 name 和 description
2. **指令**（建议 < 5000 tokens）— 用户请求匹配时加载完整 SKILL.md
3. **资源**（按需）— `scripts/`、`references/`、`assets/` 中的文件仅在需要时加载

### 触发方式

- **自动**：AI 根据用户请求自动匹配 skill 描述并激活
- **斜杠命令**：skill 名即命令名，如 `pr-review` skill → `/pr-review` 命令直接调用
- 用 `/context show` 查看当前已加载的 skill

### Skill 文件夹结构

```
my-skill/
├── SKILL.md             # 必需，主指令文件（建议 < 500 行）
├── scripts/             # 可选，可执行脚本（Python、Bash、JavaScript 等）
├── references/          # 可选，参考文档（详细说明放这里，SKILL.md 中引用时才加载）
└── assets/              # 可选，静态资源（模板、配置、数据文件等）
```

### SKILL.md 格式

必须以 YAML frontmatter 开头：

```markdown
---
name: pr-review
description: Review pull requests for code quality, security issues, and test coverage. Use when reviewing PRs or preparing code for review.
---

## Review checklist

1. Check for vulnerabilities, injection risks, exposed secrets
2. Verify edge cases and failure modes are handled
3. Confirm new code has appropriate tests

For detailed checks, see `references/checklist.md`.
Run validation: `scripts/validate.py`
```

**Frontmatter 字段**：

| 字段 | 必需 | 说明 |
|------|------|------|
| `name` | 是 | 必须与文件夹名一致。仅限小写字母、数字、连字符，不能以连字符开头/结尾，不能连续连字符，最长 64 字符 |
| `description` | 是 | 描述何时激活此 skill，AI 据此判断是否匹配。应包含具体关键词和动作。最长 1024 字符 |
| `license` | 否 | 许可证名称或引用（如 `Apache-2.0`） |
| `compatibility` | 否 | 环境要求（如依赖的工具、网络访问），最长 500 字符 |
| `metadata` | 否 | 附加键值数据（如 `author`、`version`） |
| `allowed-tools` | 否 | 空格分隔的预批准工具列表（实验性） |

### 作用域

| 位置 | 作用域 | 用途 |
|------|--------|------|
| `.kiro/skills/` | Workspace | 项目特定工作流、团队约定 |
| `~/.kiro/skills/` | Global | 个人通用工作流，跨项目生效 |

同名时 workspace 优先于 global。

### 默认 agent vs 自定义 agent

- **默认 agent**（kiro_default）：自动加载两个位置的 skill，无需配置
- **自定义 agent**：需要在 `resources` 中显式添加 `skill://` URI：

```json
{
  "name": "my-agent",
  "resources": [
    "skill://.kiro/skills/*/SKILL.md",
    "skill://~/.kiro/skills/*/SKILL.md"
  ]
}
```

### Skill vs Steering

| | Skill | Steering |
|---|---|---|
| 标准 | 开放标准（[Agent Skills](https://agentskills.io)） | Kiro 专有 |
| 加载方式 | 按需激活 | 始终加载 |
| 可包含子资源 | 是（`scripts/`、`references/`、`assets/`） | 否 |
| 适用场景 | 可复用工作流、可共享的指令包 | 项目规范和约定 |
| 版本控制 | 建议提交到仓库，团队共享 | 同左 |

### 最佳实践

- **描述要精确**：AI 靠 description 决定是否激活。"Review pull requests for security and test coverage" 优于 "helps with code review"
- **SKILL.md 保持精简**：建议 < 500 行，详细文档放 `references/`
- **确定性任务用脚本**：校验、文件生成、API 调用等放 `scripts/`，比让 LLM 生成更可靠
- **选对作用域**：个人习惯 → global，团队流程 → workspace
- **纳入版本控制**：将 `.kiro/skills/` 提交到仓库，确保团队共享相同工作流
- **文件引用用相对路径**：从 SKILL.md 出发引用，保持一层深度，避免深层嵌套

### 本项目推荐目录结构

```
.kiro/skills/
├── coding-standards/
│   ├── SKILL.md          ← Java 编码规范详细指南
│   └── references/       ← 详细规则文档
├── architecture-patterns/
│   ├── SKILL.md          ← 架构模式参考
│   └── references/
└── troubleshooting/
    ├── SKILL.md          ← 常见问题排查指南
    ├── scripts/          ← 诊断脚本
    └── references/
```

## 上下文监控

每个智能体（包括协调者）在任务结束前，自行统计上下文使用情况并写入任务目录的 `context-stats.md`：

- 上下文使用百分比
- 加载的 resources 文件列表及大小
- 实际用到 / 未用到的文件
- 任务耗时（轮次数）

协调者在任务完成后汇总所有智能体的统计数据，写入 `dispatch-log.md`，分析：

- 哪些 agent 的 resources 配置需要精简
- 哪些任务的 prompt_template 过长
- 流程中哪个环节消耗上下文最多

这些数据是过程改进的核心输入，用于持续优化智能体配置和协作流程。

### 常用命令

| 命令 | 用途 |
|------|------|
| `/context show` | 查看当前上下文使用情况和文件列表 |
| `/context add <path>` | 临时添加文件到当前会话 |
| `/context remove <path>` | 从当前会话移除文件 |
| `/context clear` | 清除所有会话级上下文 |
| `/compact` | 手动触发上下文压缩 |
| `/knowledge add <path>` | 添加内容到知识库 |
