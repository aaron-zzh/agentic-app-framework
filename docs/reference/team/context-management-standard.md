---
level: 2
version: 0.1.0
date: 2026-05-06
purpose: 规定 AAF 项目中各类内容在 Kiro 上下文系统中的归属位置，避免 agent 资源臃肿、漏载或错载
changelog:
  - 2026-05-06 | 重构为"决策手册"风格：新增内容类型映射、决策树、agent 配置清单（参考 multica CLAUDE.md 的高密度规则文档风格）
  - 2026-05-04 | 初版
---

# 上下文管理规范

本项目基于 [Kiro Context Management](https://kiro.dev/docs/cli/chat/context/) 的四层机制，规定**哪些内容放什么位置**，让每个 agent 只看该看的。

## 1. 总原则（硬约束）

- **能 Steering 绝不 Session**：全局不变的规则放 Steering，临时文件才放 Session
- **能 Skill 绝不 Resources**：偶尔才用的放 Skill（按需加载），每次都用的才放 Resources（始终加载）
- **大文档绝不硬塞上下文**：> 10MB 或文件数多 → Knowledge Base 走搜索
- **外部参考资料不进全局**：`docs/design/auto-dev/multica/`、`docs/design/auto-dev/gstack/` 等外部项目代码，只有在具体任务需要时才 `/context add`
- **上下文使用率 ≤ 50%**：steering 硬约束（见 `.kiro/steering/collaboration.md`），违反视为配置错误

## 2. 四层策略速览

| 层级 | 配置方式 | 加载时机 | 持久性 | 典型大小 |
|------|---------|---------|--------|---------|
| **Steering** | `.kiro/steering/*.md` | 所有 agent 自动加载 | 跨会话 | 单文件 < 5KB，总量 < 20KB |
| **Agent Resources** | agent `resources: file://...` | 指定 agent 启动时全量加载 | 跨会话 | 单 agent 总量 < 100KB |
| **Skills** | agent `resources: skill://...` | 启动加载元数据，触发时加载正文 | 跨会话 | 正文 < 500 行/SKILL.md |
| **Knowledge Base** | `knowledgeBase` / `/knowledge add` | 搜索时才加载 | 跨会话 | 不限 |

外加 **Session Context**（`/context add`）：始终加载但仅当前会话有效，适合临时文件。

**上下文窗口限制**：上限为模型窗口的 75%，超出自动丢弃最旧文件，触发 compaction 摘要。默认 agent（kiro_default）自动加载所有 skill，自定义 agent 必须显式 `skill://`。

## 3. 哪些内容放什么位置（决策手册）

### 3.1 内容类型 → 位置映射表

| 内容类型 | 具体举例 | 推荐位置 | 理由 |
|---------|---------|---------|------|
| **协作红线** | "完工前必跑 `check:affected`"、"上下文 ≤ 50%"、"≥5 文件需评估" | `.kiro/steering/collaboration.md` | 所有 agent 必须随时知道 |
| **全局 AI 行为硬规则** | "禁兼容层"、"不做 broad refactors"、"代码注释语言统一"、"Prefer existing patterns" | `.kiro/steering/` 新建单独文件 或 并入 collaboration.md | 所有 agent 写代码都适用 |
| **提交规范要点** | Conventional Commits 格式 + `Task: #N` 脚注 | steering（红线摘要）+ `docs/reference/dev/git/commit-standard.md`（详细）引用 | 每次提交都要遵守 |
| **可复用工作流** | "如何创建 Maven 模块"、"如何写 ADR"、"如何审查代码" | `.kiro/skills/{slug}/SKILL.md` | 偶尔才需要，按需激活省上下文 |
| **Skill 详细参考** | Java 编码规范细则、验收测试模板代码 | `.kiro/skills/{slug}/references/*.md` | 渐进披露，SKILL.md 引用时才加载 |
| **角色必看源码/规范** | developer-api 的 Controller 基类、coding-style-standard.md、roles/developer.md | agent `resources: file://` | 该 agent 每次都要 |
| **规范总览入口** | `docs/reference/team/Readme.md`、`docs/reference/dev/architecture-constraints.md` | 相关 agent `resources: file://` | 规范导航表 |
| **任务模板** | `docs/task/_template/requirement.md` / `design.md` / `dev-log.md` / `review.md` / `audit.md` | 对应产出 agent `resources: file://` | product 用 requirement 模板，architect 用 design 模板 |
| **本次任务上下文** | 本次 `AAF-025` 的需求/设计/验收标准 | subagent `prompt_template` 内嵌 或 精确到单文件的 `resources` | 每任务不同，不走全局 |
| **历史任务产出物** | `docs/task/v0.1.0/AAF-023/dev-log.md` | Knowledge Base `docs/task/` | 不污染 resources，靠搜索 |
| **整模块设计文档** | `docs/design/framework/meta-engine.md`（50KB+） | Knowledge Base `docs/design/framework` | 整体大，按搜索命中片段 |
| **外部参考项目代码** | `docs/design/auto-dev/multica/`、`gstack/` | **不进任何层级**，按需 `/context add` | 大且与本项目直接代码无关 |
| **整个代码库** | `apps/service/`、`apps/webui/` 源码 | Knowledge Base（必要时）或 `code` 工具按需读 | 不应全量载入 |

### 3.2 决策树（N 步判断）

```text
Q1: 这个内容是否所有 agent 都要看？
  是 → Steering（.kiro/steering/）
  否 → Q2

Q2: 这个内容是否 > 10MB 或文件很多（> 20 个）？
  是 → Knowledge Base（knowledgeBase 字段）
  否 → Q3

Q3: 对这个 agent 来说是"每次都要看"还是"偶尔才需要"？
  每次 → Agent Resources（resources: file://）
  偶尔 → Skill（resources: skill://）

Q4: 是否仅本次任务临时用？
  是 → prompt_template 内嵌 或 /context add（会话结束即失效）
  否 → 走上面几层
```

### 3.3 反例清单（最常见错误）

| ❌ 错误做法 | 后果 | ✅ 正确做法 |
|-----------|------|-----------|
| 把整个 `docs/reference/` 塞进所有 agent 的 resources | 每次启动浪费数十万 token，触发 compaction | 只给该 agent 角色相关的规范；其他走 Knowledge Base |
| 把外部参考资料（`multica/`、`gstack/`）放进 steering | 每次加载，内容与本项目代码无关 | 已在 `.nxignore`；**按需 `/context add`**，任务结束不保留 |
| Skill 描述写成 "helps with code review" | AI 匹配不到关键词，永远激活不了 | `"Review pull requests for security, test coverage, and breaking changes. Use when reviewing PRs or preparing code for review."` |
| 本任务的需求文档硬编码到 steering | 下个任务的需求覆盖不了，污染所有 agent | 放到 subagent 的 `prompt_template` 或精确到单文件 `resources` |
| SKILL.md 写成 800 行的长文 | AI 匹配后全量加载，浪费上下文 | SKILL.md < 500 行，详细规则放 `references/`，SKILL.md 引用时才加载 |
| 多个 agent 都加载相同的 `commit-standard.md` | 提交规范适用全员，应上升为 steering | 要么 steering 内摘录红线，要么 steering 引用链接（不硬塞全文） |
| knowledgeBase 里放 `docs/`（整个目录） | 搜索命中率乱，上下文无关内容进来 | 按子目录分：product → `docs/prd`；architect → `docs/design/framework`；tester → `docs/reference/dev/test` |

## 4. 本项目 Agent 资源配置清单

### 4.1 通用（所有 agent 必配）

```json
{
  "resources": [
    "file://AGENTS.md",
    "file://docs/reference/team/Readme.md",
    "file://docs/reference/team/collaboration-standard.md",
    "file://docs/reference/team/process-standard.md",
    "file://docs/reference/team/roles/{role}.md"
  ]
}
```

外加 Steering 自动加载的 `.kiro/steering/collaboration.md`，所有 agent 都无需显式配置。

### 4.2 各 agent 差异化配置

| Agent | 差异化 resources（在通用之外） | knowledgeBase | 典型 Skill |
|-------|-------------------------------|---------------|-----------|
| **kiro_default**（协调者） | `docs/task/backlog.md`、`docs/task/aaf-v0.1.0.md`、`docs/task/_template/dispatch-log.md`、`docs/task/_template/context-stats.md` | `docs/task/` | 自动加载所有 skill（默认 agent 特性） |
| **product** | `docs/prd/Readme.md`、`requirement-standard.md`、`roadmap.md`、`task/_template/requirement.md` | `docs/task/backlog.md`、`docs/prd` | `doc-writing` |
| **architect** | `docs/design/Readme.md`、`architecture-constraints.md`、`code-review-standard.md`、`domain-modeling-standard.md`、`task/_template/{design,review,audit}.md` | `docs/design/framework` | `coding-standards`、`architecture-audit`（拟立） |
| **developer-api** | `architecture-constraints.md`、`coding-style-standard.md`（Java）、`domain-modeling-standard.md`、`unit-test-standard.md`、`commit-standard.md`、`task/_template/dev-log.md` | `docs/design/service` | `coding-standards` |
| **developer-web** | `architecture-constraints.md`、前端 `coding-style-standard.md`（若有）、`unit-test-standard.md`、`commit-standard.md`、`task/_template/dev-log.md` | `docs/design/webui` | `coding-standards`、`frontend-state-management`（拟立） |
| **developer-app** | 类似 developer-web，替换为 uniapp 规范 | `docs/design/uniapp` | 同上 |
| **designer** | `docs/design/ui/Readme.md`、`ui-experience.md`、`design-system.md` | `docs/design/ui` | `design-review`（拟立） |
| **qa** | `docs/reference/team/process-audit-standard.md`、`task/_template/process-audit.md` | `docs/task/` | `process-audit`（拟立） |
| **tester** | `docs/reference/dev/test/*`（单测/集成/验收 3 份）、`task/_template/test-report.md` | `docs/design/service` | `e2e-testing`（拟立，配合 AAF-023 #6 Playwright） |
| **kiro_planner** | （默认配置，规划任务少量加载） | — | — |
| **kiro_guide** | 官方 Kiro 文档索引 | — | — |

### 4.3 配置原则

- **单 agent resources 总量控制在 < 100KB**（约 25K token），给任务上下文留足余量
- **禁止 resources 通配符 `**/*.md`**：精确列出每个文件，避免扩张到整个目录
- **新增规范文档时先问**：这是"所有 agent 要看的"（→ steering）、"某 agent 每次要看的"（→ resources）、还是"偶尔用到的"（→ skill）？错位是上下文膨胀的主因

## 5. Steering 规范（硬约束摘要）

Steering 的作用是**红线清单**，不是完整规范。红线来自 `docs/reference/team/collaboration-standard.md` + 编码规范 + 测试规范的**不可违反硬点**。

**Steering 文件硬规则**：
- 每个 `.md` < 5KB（约 1250 字）
- 不内联完整规范，只摘录关键硬约束 + 链接到 `docs/reference/` 的详细文档
- 新增 steering 文件需协调者审批；不属于"所有 agent 必须知道"的内容不进 steering

当前 AAF 仅有一份 `collaboration.md`（84 行红线）。新增候选：
- `coding-rules.md`（全局 AI 行为硬规则：禁兼容层/不 broad refactors/注释语言/优先已有模式）——等编码规范 A/B/C/D 落地后抽取

## 6. Skill 规范（精简）

Skill 遵循开放标准 [Agent Skills](https://agentskills.io)，是可移植的指令包。

### 6.1 作用域

| 位置 | 作用域 | 用途 |
|------|--------|------|
| `.kiro/skills/` | Workspace | AAF 项目特定工作流（优先） |
| `~/.kiro/skills/` | Global | 个人通用工作流，跨项目 |

同名冲突时 workspace 优先。

### 6.2 SKILL.md 格式要点

```markdown
---
name: skill-slug           # 必须与文件夹名一致
description: 精确描述何时激活此 skill（带关键词和动作）。最长 1024 字符
---

## 主内容（< 500 行）

详细参考：`references/xxx.md`
校验脚本：`scripts/xxx.sh`
```

**Frontmatter 字段约束**：
- `name`：小写字母 + 数字 + 连字符，不能以 `-` 开头/结尾，≤ 64 字符
- `description`：包含具体关键词和触发场景；AI 靠这个判断是否匹配——写得精确才激活得对
- 可选：`license` / `compatibility` / `metadata` / `allowed-tools`

### 6.3 Skill 最佳实践

- **描述精确**：`"Review PRs for security and test coverage"` 优于 `"helps with code review"`
- **SKILL.md 精简**：< 500 行，细节放 `references/`
- **确定性任务用脚本**：校验/文件生成/API 调用放 `scripts/`，比让 LLM 生成可靠
- **纳入版本控制**：`.kiro/skills/` 提交仓库

### 6.4 AAF 当前 skill 清单

详见 `.kiro/skills/`。当前已有：
- `coding-standards`：编码规范
- `doc-writing`：文档编写
- `iteration-management`：迭代管理
- `skill-creator`：skill 创建工具
- 其他 Nx 相关（`nx-generate` / `nx-workspace` / `nx-plugins` 等）

拟立：`architecture-audit`、`process-audit`、`e2e-testing`、`frontend-state-management`、`design-review`（见改进意见池）。

## 7. 上下文监控（强制产出）

### 7.1 硬约束

- **上下文使用率 ≤ 50%**（来自 `.kiro/steering/collaboration.md`）
- 每个 agent 在任务结束前**必须**自行统计并写入 `docs/task/v*/AAF-xxx/context-stats.md`：
  - 上下文使用百分比 + 最大历史水位
  - 加载的 resources 文件列表及字节数
  - 实际用到的 vs 未用到的文件（由 agent 自我报告）
  - 任务耗时（对话轮次数）

### 7.2 协调者汇总

任务完成后协调者在 `dispatch-log.md` 汇总：
- 哪些 agent resources 配置需要精简（某些文件每次都不用）
- 哪些 prompt_template 过长
- 流程中哪个环节消耗上下文最多

这些数据是**过程改进的核心输入**，驱动 agent 配置的持续优化。

### 7.3 常用命令

| 命令 | 用途 |
|------|------|
| `/context show` | 查看当前上下文使用情况和文件列表 |
| `/context add <path>` | 临时添加文件到当前会话 |
| `/context remove <path>` | 从当前会话移除文件 |
| `/context clear` | 清除所有会话级上下文 |
| `/compact` | 手动触发上下文压缩 |
| `/knowledge add <path>` | 添加内容到知识库 |
| `/knowledge list` | 列出所有知识库 |
| `/knowledge remove <id>` | 移除知识库 |

---

## 参考

- [Kiro Context Management](https://kiro.dev/docs/cli/chat/context/)
- [Agent Skills Specification](https://agentskills.io/specification)
- [.kiro/steering/collaboration.md](../../../.kiro/steering/collaboration.md) —— 协作硬约束
- [docs/reference/team/collaboration-standard.md](./collaboration-standard.md) —— 协作详细规范
- [AGENTS.md](../../../AGENTS.md) —— agent 入口索引
