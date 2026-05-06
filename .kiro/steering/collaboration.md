# 协作红线（Steering 硬约束）

> **本文件是所有 agent 启动时强制加载的硬约束清单**。详细规范见 [docs/reference/team/Readme.md](../../docs/reference/team/Readme.md)——那是唯一真理源。
> 本文件违反以下任一条即为 blocker。修硬约束改本文件；修详细规范改 docs/。

## 任务编号与提交

- 用户故事：`AAF-{三位}`，管理在 [backlog.md](../../docs/task/backlog.md)
- 技术任务：`#{递增数字}`，管理在迭代文件 `docs/task/aaf-{version}.md`
- 提交脚注：`Task: #N`（详见 [提交规范](../../docs/reference/dev/git/commit-standard.md)）

## 完工门禁

- developer 汇报前必须 `pnpm check:affected` **全绿**，失败汇报视为未完工，协调者有权直接驳回
- tester 启动前协调者必须验证 `check` 全绿；发现编译挂/单测红立即退回 developer
- 质量门控通过条件：**blocker=0 且 major≤2**
- 详见 [AI 自验证循环](../../docs/reference/team/process-standard.md#331-ai-自验证循环developer-强制内循环)

## AI 验证循环（developer 内循环）

参考 multica "AI Agent Verification Loop"——按场景分层决定验证力度：

| 场景 | 验证要求 | 说明 |
|------|----------|------|
| 🟢 对话中小改动（探索/试错/文档） | 可跳过 | 自行判断，不阻塞对话节奏 |
| 🟡 单文件/单模块改动 | 跑对应项目 test | `pnpm nx test service` 或 `pnpm nx test webui` |
| 🔴 任务完工汇报前 / PR 前 | `pnpm check:affected` 全绿 | 失败即未完工，不得汇报 |
| 🔴 迭代交付前 | `pnpm check` + `pnpm acceptance` 全绿 | 见交付清单 |

- 违反"🔴 失败了继续汇报"视为 blocker
- 快速迭代：只改 Java 先跑 `pnpm nx test service`，只改 TS 先跑 `pnpm nx test webui`，完工前再跑全量 `check:affected`

## AI 行为硬规则（摘要）

以下条目部分来自 multica 借鉴（已登记 [改进意见 A-F](../../docs/prd/improvements.md)），作为软约束先行，评估采纳后转硬约束：

- **禁兼容层**：AAF 未 v1.0，不加 fallback / shim / legacy adapter / dual-write
- **不 broad refactors**：只改任务要求范围，相邻模块借机重构即 blocker
- **优先已有模式**：同问题已有实现必须复用，禁并行抽象
- **服务端数据不进 Zustand**：TanStack Query 管服务端，Zustand 仅管 UI；复制即双真理源
- **Tests follow the code, not the app**：共享逻辑测试放 `packages/`，测试需 mock `next/*` 测共享组件即放错位置

完整规则见 [AGENTS.md #AI 行为硬规则](../../AGENTS.md#ai-行为硬规则编码时遵守)。

## 批量修改与高风险操作

- ≥5 文件 / 跨模块 / 改 `common/` / 改接口签名 → **协调者评估后再做**
- 🔴 高风险（架构调整、接口删除、权限变更、数据迁移）**必须人类审核后才能开发**
- 改接口签名 → 协调者必须同步所有使用方
- **自动化止于开 PR**：agent 可自动创建 PR，禁止自动 merge / 自动部署。merge 与部署必须人类或协调者审核触发
- 详见 [协作规范 #风险等级与审核](../../docs/reference/team/collaboration-standard.md)

## Agent 派发触发条件（风险分级）

协调者按风险等级决定派发哪些 subagent，产出物随派发链自然产生：

- 🟢 **低**（修 typo / 加日志 / 规范微调 / <50 行小改动）→ 协调者全程兼任，不派发
- 🟡 **中**（新增接口 / 修改业务逻辑 / 依赖升级）→ 派发 developer + tester，跳过 product 细化和 qa 审计
- 🔴 **高**（架构调整 / 接口删除 / 权限变更 / 数据迁移 / ≥5 文件跨模块）→ 完整派发 product→architect→developer→tester→qa

执行中出现 5 文件以上改动 / 接口签名变更 / 涉及权限安全 → **强制升级为 🔴**。详见 [协作规范 #Agent 派发触发条件](../../docs/reference/team/collaboration-standard.md#agent-派发触发条件)。

## 文档真理源

- **`docs/` 下的规范是唯一真理**。AGENTS.md 和本文件是摘要/红线
- 一个知识点一份文档；发现重复记录到 [改进意见](../../docs/prd/improvements.md)
- 规范文档（`docs/reference/`、本文件、`docs/reference/dev/requirement-standard.md` 等）**只能由协调者修改**；其他 agent 发现问题在 `dev-log.md` 中提出
- 编写/修改文档时调用 `doc-writing` skill

## 测试分层（硬约束）

- Java 单测 `*Test.java` → Surefire → **developer** / Java 集成+验收 `*IT.java` / `*AcceptanceTest.java` → Failsafe → **tester**
- TS 单测 `*.test.ts(x)` / `*.spec.ts(x)` → **developer** / TS 验收 `*.accept.test.ts(x)` 或 Playwright → **tester**
- 命名混用即 blocker；两类测试不放同一文件
- 详见 [测试规范](../../docs/reference/dev/test/)

## AI 协作宣言

- 规范即共识 > 口头约定
- 人机协作 > 单方决策
- 响应变化 > 遵循计划
- 持续改进 > 固守流程
- 精益求精 > 简单应付
- 知其然并知其所以然

## 核心原则（5 条）

- **系统优先**：能系统化的不交给 AI，能 AI 的不交给人
- **AI 主动**：不等待指令，主动感知上下文、发现模式、推荐方案
- **意图优先**：澄清意图优先于执行，宁可多问一句，不猜测后回滚
- **渐进提交**：执行结果先暂存，确认后提交；低置信度操作不直接生效
- **文档对应**：每项活动必须有规范文档和产出文档，无规范先建规范

## 上下文管理

- 使用率不得超过 **50%**，超过必须分析原因 + 记录风险 + 优化
- 协调者检查时机：每派发 2 个子 agent 后；任务中途响应变慢时；会话结束前

## 交付清单（版本发布前）

- [ ] P0/P1 任务已完成
- [ ] 质量门控通过（blocker=0，major≤2）
- [ ] `pnpm check` 和 `pnpm acceptance` 全绿
- [ ] 接口变更有破坏性变更说明
- [ ] 数据库迁移脚本就绪（如有）
- [ ] 文档已更新；发布说明已编写
- [ ] backlog 已归档

---

## 详细规范导航（按需引用）

| 场景 | 文档 |
|------|------|
| 团队架构与角色 | [docs/reference/team/Readme.md](../../docs/reference/team/Readme.md) |
| 协作详细规范 | [collaboration-standard.md](../../docs/reference/team/collaboration-standard.md) |
| 过程详细规范 | [process-standard.md](../../docs/reference/team/process-standard.md) |
| 编码硬约束 1-9 | [coding-standards](../skills/coding-standards/SKILL.md) |
| 代码审查 | [code-review-standard.md](../../docs/reference/dev/code-review-standard.md) |
| 提交规范 | [commit-standard.md](../../docs/reference/dev/git/commit-standard.md) |
