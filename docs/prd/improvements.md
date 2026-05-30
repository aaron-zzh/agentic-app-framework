---
level: 2
version: 0.1.0
date: 2026-05-06
purpose: 记录协作过程中发现的改进点，按主题分类，由协调者定期审阅
---

# 改进意见

AI 智能体和人类用户协作中发现的改进点，协调者定期审阅后纳入规范或排入待办。

- **格式**：`日期 | 条目 | 状态`（提出者默认协调者）
- **状态**：`待评估` / `已采纳`（可附实施状态） / `已完成` / `已拒绝`

## 已完成

- 2026-04-28 | **AGENTS.md 补充"基本原则"和"AI 协作宣言"** | 已完成
- 2026-05-05 | **.nxignore 排除 `docs/design/auto-dev/multica` 和 `gstack` 参考资料目录**；后续新增参考目录需同步追加 | 已完成
- 2026-05-05 | **AAF-024 #10 文档真理源归一**：steering 红线化（84 行）+ AGENTS.md 指针化 + 7 个 agent 的 resources 精确配置 | 已完成
- 2026-05-05 | **AAF-024 #11 Agent 派发触发条件规则**：🟢 低风险 kiro_default 兼任不派发 / 🟡 中风险派发 developer+tester / 🔴 高风险完整派发 product→architect→developer→tester→qa。反思见 [AAF-024 dev-log #2](../task/v0.1.0/AAF-024/dev-log.md#2-过度工程化判断的反思记录) | 已完成
- 2026-05-06 | **Agent 产出物契约机制化（gstack 借鉴）**：把下游 agent 对上游产出物的依赖从"协调者 prompt 转述"升级为"结构化契约+启动前校验"。gstack 设计"下游 skill 只读上游产物"（office-hours → design doc → plan-ceo-review；plan-eng-review → test plan → qa），产物不全下游不启动。AAF 现有 product→architect→developer→tester→qa 链条，但产出物靠 prompt 传递易丢失上下文。建议在 `collaboration-standard.md` 新增"派发产出物契约表"：product 必产 `docs/prd/AAF-xxx.md` + 验收标准清单；architect 必产设计文档 + 测试矩阵；tester 必读测试矩阵生成验收脚本；下游 agent 启动时校验上游产物存在且完整，不满足则拒绝执行并回退到上游 | 已完成

## 已采纳

- 2026-05-05 | **缺失关键流程**：hotfix / 技术债 / 依赖升级 / 回滚 / 超时无规范。遇到首次真实案例时各建 20-30 行规范，不预先写 | 已采纳
- 2026-05-06 | **前端状态管理规范细化（4 个子规则）**：补充已登记条目：① **Zustand 选择器必须返回稳定引用**——`s => ({ a: s.a, b: s.b })` 或 `s => s.items.map(...)` 会触发无限重渲，用分开选 primitive 或 shallow 比较；② **Query 缓存键必须带 workspace/租户 id**（如 `['issues', wsId]`），让切换空间自动换数据无需手动 invalidate；③ **Mutations 默认 optimistic**：本地先改 → 发请求 → 失败回滚 → settle 后 invalidate；④ **Persist 硬规则**：用户偏好/草稿/tab 布局可持久化，模态框/临时选择/服务端数据绝不持久化。参考 multica CLAUDE.md `State Management` 节 | 已采纳
- 2026-05-05 | **Vitest + Playwright + @nx/vite 技术选型**：不引入 Jest/@nx/jest。见 [ADR-001](../design/adr/ADR-001-vitest-vs-jest.md) | 已采纳
- 2026-05-05 | **后端测试环境走本地真实 DB + CI service container，不引入 Testcontainers**：见 [ADR-002](../design/adr/ADR-002-local-env-vs-testcontainers.md) | 已采纳
- 2026-05-05 | **Cucumber 移除**：6 处规范宣称但 pom 零依赖零 `.feature`；Cucumber BDD 与"`docs/prd/**` 是唯一真理"冲突。Gherkin 仅保留在需求文档 + 测试 `@DisplayName`。见 [ADR-003](../design/adr/ADR-003-remove-cucumber.md) | 已采纳
- 2026-05-05 | **Playwright E2E 引入**：登记为 [AAF-023 #6](../task/v0.1.0/AAF-023/tasks.md) | 已采纳（未实施）

## 协作流程与智能体

- 2026-05-06 | **指定任务类型 LLM**：为不同任务（代码生成/文档/测试/架构）配置专门 LLM，优化效果和成本（参考 multica 支持 8 种 Agent CLI） | 远期
- 2026-05-06 | **Sprint 流水线模板固化（gstack "流程 > 角色"理念）**：把典型迭代派发顺序从协调者现场组装升级为可复用 skill 模板。gstack 原话 "without a process, ten agents is ten sources of chaos"——流程结构化是并行的前提。AAF 已有完整角色集（product/architect/developer-*/qa/tester）和 🟢🟡🔴 风险分级（AAF-024 #11），但 subagent 派发的流水线编排仍靠协调者临场组装。建议新增 `.kiro/skills/sprint-orchestration/SKILL.md`，封装三级派发模板（Think→Plan→Build→Review→Test→Ship→Reflect），与风险分级共同构成"触发条件 + 流程模板"二维框架 | 待评估
- 2026-05-06 | **/autoplan 式一键评审 skill（gstack 借鉴）**：把 CEO→design→eng 三轮评审封装为一键 skill，只把"品味决策"（taste decision）暴露给用户。gstack `/autoplan` 在每轮编码"决策原则"，可机械判断的都自动处理，只在不可机械化的选型点才 AskUserQuestion。AAF kiro_planner/协调者现在派发 subagent 需反复转述需求。建议：作为 `sprint-orchestration` skill 的高层包装，内部按 🔴 级别自动跑 product→architect→developer 规划阶段，仅在架构选型/接口删除/权限变更点向用户发问，降低协调者转述摩擦 | 待评估

- 2026-05-06 | **product agent 六问挑战模板（gstack `/office-hours` 借鉴）**：把"推翻用户自述需求"从隐含职责升级为结构化必过检查。gstack 例子中用户说"我要日报应用"，agent 识别出"实际在描述 chief of staff AI"，抽取 5 个隐含能力 + 挑战 4 个前提 + 给 3 个实现方案。AAF product agent 职责含"需求分析"但无强制挑战前提的 prompt 结构。建议在 product agent prompt 中加六问必过项：① 描述的是需求还是方案？② 真正痛点是什么？③ 有哪些隐含前提？④ 最窄 MVP 是什么？⑤ 完整愿景成本多少？⑥ 推荐哪个、为什么？没过六问不进架构阶段 | 待评估
- 2026-05-06 | **迭代 retro 产出机制化（gstack `/retro` 借鉴）**：把迭代回顾从"随意总结"升级为结构化产出物。gstack `/retro` 周度回顾含按人分解贡献 / 发货连续性 / 测试健康趋势 / 成长机会。AAF qa agent 负责度量分析和质量门控，但无定期 retro 产出物。建议在 `iteration-management` skill 加 retro 步骤：迭代归档前必产 `docs/task/v0.x.0/retro.md`，含按角色分解贡献 / 计划 vs 实际对比 / 测试覆盖趋势 / 质量门控通过情况 / 下迭代改进点；作为 qa agent 的标准交付物之一 | 待评估
- 2026-05-06 | **Tests follow the code, not the app（测试放置硬规则）**：参考 multica CLAUDE.md 的分层——共享业务逻辑测试 → `packages/*.test.ts`（pure logic）；共享 UI 组件测试 → `packages/views/*.test.tsx`（jsdom，不 mock 框架）；平台特定接线测试 → `apps/web/*.test.tsx` / `apps/desktop/`（需要 mock 框架）；E2E → `e2e/*.spec.ts`。**硬规则：如果测试需要 mock `next/navigation` 或 `react-router` 来测共享组件，测试放错了位置，必须移到 `packages/`**。依赖 P2.3 packages/ 落地 | 待评估
- 2026-05-06 | **修 bug 必加回归测试（gstack `/qa` 借鉴）**：让"测试完备性"从纪律变成机制。gstack 每次修 bug 都自动生成对应回归测试。AAF 测试规范已分层明确（Surefire/Failsafe、`*.test.ts`/`*.accept.test.ts`），但"修一个 bug 必须加一个测试"未机制化。建议在 `acceptance-test-standard.md` 和 tester agent SKILL.md 硬编码：每修一个 bug 必须在对应层级新增一个 failing-then-passing 的测试用例；在 dev-log 记录 bug 链接 + 新增测试文件路径 + 复现步骤；缺失即 blocker | 待评估
- 2026-05-06 | **视觉审计原子提交规范（gstack `/design-review` 借鉴）**：前端样式修复每个 finding 一个 commit，格式 `style(design): FINDING-NNN`，可 bisect 回退。gstack 80 项视觉审计后逐项修复，CSS-only 变更免风险预算，JSX 变更计入风险。AAF 提交规范有 `style` type 但无"一修一提交"约束。建议：① 在 `commit-standard.md` 补充"视觉/样式修复场景"——每个独立 finding 一个 commit，scope 为组件名或页面名；② designer agent 或 tester agent 做视觉审计时遵循此规范；③ 纯 CSS/Tailwind 变更标记为低风险可批量提交，涉及组件结构变更需逐个提交。v0.2 webui 有实际页面后生效 | 待评估
- 2026-05-06 | **Polymorphic Actor 落地路径**：① 新建 `docs/design/framework/actor.md` 定义 Actor 抽象、类型枚举、操作接口、审计协议；② 更新 `requirement-standard.md` 强制"谁做了什么"字段用 Actor；③ 更新 ~~`permission-system.md`~~→`access-control.md` 把"Agent 作为权限主体"从 Principal 层上升为 Actor 层；④ `packages/types/actor.ts` + `com.xuejiai.aaf.common.actor.Actor` 作为首个共享类型（同时验证 P2.3）；⑤ ArchUnit/ESLint 约束 `createdBy`/`assignee`/`owner` 必须为 Actor 类型 | "已实施（设计+规范层面），代码层面待首个业务实体开发时落地

## AAF 框架业务能力

- 2026-05-06 | **WebSocket 实时事件层分房间规范**：按 workspace 分房间 + 事件类型枚举 + "WS 只 invalidate query 不直接写 store"。前端状态管理条目已覆盖后半条，分房间/事件类型协议未记录 | 待评估
- 2026-05-05 | **Agent Runtime / Daemon 架构 P3.2**：AAF 作为后端本身不需要 Daemon；若要作为"被调度 Agent 框架"被 multica 式平台管理，参考 multica Daemon 机制设计 Agent Sandbox：探测 → 注册 → 轮询认领 → 心跳 → 隔离执行 → 反注册。v0.2+ Agent Sandbox 模块设计时评估 | 待评估
- 2026-05-06 | **后台巡检任务三件套规范**：借鉴 multica Server 启动的三个 goroutine——① **Runtime Sweeper**（每 30s）：标记离线 runtime / 回收孤儿任务 / GC 长期离线 ② **Autopilot Scheduler**（每 30s）：扫 cron 触发器到点 dispatch ③ **DB Stats Logger**（周期性）：打印连接池状态。AAF 编排引擎/协作控制台 Phase 3 Autopilot 落地前需定义后台任务规范，放在 `docs/design/framework/background-tasks.md` | 待评估
- 2026-05-06 | **WebSocket 心跳规范 + 事件分类处理**：补充已登记的"WebSocket 分房间规范"。① **心跳**：server 每 54s ping，client 60s 内必须 pong，否则断连；② **事件分类处理**：即时更新事件（issue/comment/task 这类需要高响应的）前端**直接 patch 本地缓存**，其他 less-critical 事件触发 **query invalidate 重拉**——不是所有事件都走 invalidate 一种模式。参考 multica 实时层设计 | 待评估
- 2026-05-06 | **Autopilot 细化规范**：补充已登记的"Autopilot 多触发机制"。① **Run 状态机**：`pending → issue_created → running → completed/failed/skipped`；② **每 Autopilot 实例独立 `concurrency_policy`**（skip/queue/replace），与任务生命周期的并发策略语义不同（一个管实例级，一个管任务级）；③ **内置模板样板**：daily news digest / PR review reminder / bug triage / weekly progress report / dependency audit / security scan 等 6 种，作为产品启动样板（用户一键创建而非从零写 cron） | 待评估
- 2026-05-06 | **Task 实体字段与关系规范化**：补充已登记的"任务生命周期状态机"。① **Task 核心字段**：多态 `assignee_type/id` + `creator_type/id`、`parent_task_id`（子任务）、`project_id`/`epic_id`（归属）、`origin_type/id`（追溯来源如 autopilot run）、`acceptance_criteria` JSONB、`due_date`、`position`（手动排序）；② **依赖关系类型**：`blocks` / `blocked_by` / `related` 三种；③ **Comment 分类**：`comment` / `status_change` / `progress_update` / `system` 四种类型，时间线混合展示；④ **订阅自动来源**：creator / assignee / commenter / mentioned / manual 五种，自动订阅规则明确；⑤ **Inbox 严重性分级**：`action_required` / `attention` / `info` 三级，决定通知强度 | 待评估
- 2026-05-05 | **packages/ 首个共享包 + 包边界 ArchUnit P2.3**：v0.1 末期或 v0.2 启动时，出现第一个可提取共享模块（如 types/common-utils）时建立首包；配套 ArchUnit（Java）+ ESLint `no-restricted-imports` / `import/no-restricted-paths`（TS）。参考 multica `packages/core` 零 react-dom、`packages/ui` 零业务包导入 | 已采纳（待首个跨 app 共享需求出现时触发）
- 2026-05-05 | **ESLint 配置 + webui lint target**：`apps/webui/project.json` 的 `check` 缺 lint（Next.js 16 linter 被 off）。引入 ESLint + eslint-config-next 最小配置，`webui:lint` 加入 `webui:check` 的 `dependsOn` | 待评估
- 2026-05-05 | **首次全仓 prettier 对齐**：`pnpm format:check` 发现 80+ 文件不合规（历史遗留）。启用 CI format-check 前先跑一次 `pnpm format`，在 AAF-023 收尾单独提交 | 待评估


## developer agent 跨任务越界提交问题

**发现时间**：2026-05-30，webui 代码审查分批修复过程中

**现象**：
developer-webui agent 在执行本任务期间多次带进后端代码：
- 批次 1 commit 多带 `apps/service/aaf-api/src/main/resources/db/migration/v1__system_schema.sql`。
- 批次 2 执行期间误提交 `9c1db9e feat(profile)` 后端用户画像模块（14 个文件 +714 行）。

**原因**：
- agent 未限制 `git add` 路径，误将工作区中其他未提交文件一并提交。
- 任务 prompt 有说明仅递 webui + docs 路径，但未被严格遵守。

**建议**：
1. 所有 developer agent 提交前必须运行 `git status` 检查，只 `git add <任务明确指定的路径>`，严禁 `git add .` 或 `git add -A`。
2. 可考虑在 commit hook 中加路径过滤，跨项目提交强制警告。
3. 在 `.kiro/agents/developer-webui` 的提示词中补充：跨路径提交视为 blocker。
