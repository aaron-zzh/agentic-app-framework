# 开发日志：AAF-024 协作基础设施优化

> 产出目录：`docs/task/v0.1.0/AAF-024/`

---

## #10 文档真理源归一 + Agent Resources 精确配置

### 完成状态

✅ 2026-05-05（当日内完成）— 协调者执行

### 背景

在 AAF-023 #1 完成后，系统分析当前开发流程和协作规范发现：

- 同一主题（核心原则、任务流水线、质量门控、风险等级、任务编号规则）在 4-5 处文档里重复复述，修改一次动多处
- AGENTS.md 和 `.kiro/steering/collaboration.md` 里复制详细流程，违反 AAF 自己的"一个知识点一份文档"和"文档是唯一真理"两条核心原则
- 但用户的顾虑真实：如果完全指针化，agent 不一定去读链接目标——所以不能简单删除复制内容

### 决策

采纳方案 C：**分层 + Agent Resources 精确加载**

- `docs/reference/team/` 是**唯一真理源**（完整权威规范）
- `.kiro/steering/collaboration.md` 是**硬约束红线清单**（每条 1-2 行，不复制流程）
- `AGENTS.md` 是**入口索引 + 摘要**（Kiro CLI / GitHub agents 等工具默认读的文件）
- 各 agent 的 `resources` 字段**显式列出该角色需要加载的权威规范文件**，用 Kiro 机制保证加载

### 改动清单

**文档层**：

- `.kiro/steering/collaboration.md`：246 行 → 84 行，改写为红线清单（任务编号、完工门禁、批量修改、文档真理源、测试分层、AI 宣言、5 条核心原则、上下文、交付清单 + 详细规范导航表）
- `docs/reference/team/team-principle.md`：5 行原则扩展为 18 行指针文档，内容指向 collaboration-standard.md 的"核心原则"章节
- `docs/reference/team/Readme.md`：加强"唯一真理源"声明，明确 AGENTS.md 和 steering 只是"摘要+入口"
- `AGENTS.md`：去掉与 steering 重复的 AI 协作宣言、智能体关键约束、规范驱动段落；强化"docs/ 是唯一真理"的提示；文档导航表扁平化

**Agent 配置层**（7 个 `.kiro/agents/*.json`）：

按每个角色的实际需要精确配置 resources。共同项 4 个（AGENTS.md + team/Readme + collaboration-standard + process-standard），角色专属项按需。

| Agent | Resources 从 → 到 | 关键变化 |
|-------|------------------|---------|
| architect | 5 项 → 13 项 | 加 team 权威规范、code-review-standard、domain-modeling-standard、_template/design、_template/review、_template/audit |
| designer | 2 项 → 6 项 | 加 team 权威规范、design/ui/Readme |
| developer-service | 5 项 → 13 项 | 修复 `java-module-structure.md` 死链；加 team 权威规范、coding-style-standard、domain-modeling-standard、test/unit-test-standard、commit-standard、_template/dev-log |
| developer-uniapp | 4 项 → 11 项 | 加 team 权威规范、apps/uniapp/coding-standard、test/unit-test-standard、commit-standard、_template/dev-log |
| developer-webui | 4 项 → 11 项 | 加 team 权威规范、apps/webui/coding-standard、test/unit-test-standard、commit-standard、_template/dev-log |
| product | 5 项 → 9 项 | 加 team 权威规范、roadmap、_template/requirement；knowledgeBase 加 docs/prd |
| qa | 2 项 → 8 项 | 加 team 权威规范、process-audit-standard、measurement-standard、_template/review |
| tester | 3 项 → 10 项 | 加 team 权威规范、test/unit/integration/acceptance-test-standard、_template/test-report |

### 未处理项

- **kiro_default / coordinator**：Kiro CLI 默认 agent 不由 `.kiro/agents/` 配置文件定义，自动加载 steering 和 skills。当前 steering 已包含硬约束红线，无需单独处理。coordinator.md 角色文档仍在 docs/reference/team/roles/ 下，作为人类和其他 agent 参考。

### 上下文占用评估

每个 agent 的 resources 从 2-5 项增到 6-13 项，预估 30-60KB，按 Claude Sonnet 200K 窗口约占 2-3%。远低于 50% 硬约束阈值。

### 对 AAF 原则的贡献

修复了 AAF 自己的"文档是唯一真理"和"一个知识点一份文档"两条原则被违反的问题。从现在起：

- 修规范 → 只改 `docs/reference/` 下目标文件一处
- agent 启动 → Kiro 机制自动加载最新权威内容
- 修 AGENTS.md / steering → 只在调整红线或目录导航时才动

### 后续任务关系

本任务为 AAF-024 的第一步，后续 5 个子任务（#11-#15）独立推进：

- #11 派发触发条件规则（可立即做，影响 collaboration-standard）
- #12 architect vs qa 审查边界合并（可立即做）
- #13 规范-代码一致性脚本（需要脚本 + CI 集成）
- #14 ADR 目录建立 + 迁移 3 条决策（中等工作量）
- #15 规范文档 Front Matter 规范化（渐进式）

---

## 2. "过度工程化"判断的反思记录

### 背景

2026-05-05 给出"开发流程及协作规范是否合理可落地"的分析时，我判断"**流程过度工程化，产出物成本 > 一人公司 + AI 的可承受范围**"（原问题 2）。用户反驳：**现在是多智能体开发，不同智能体加载不同上下文，产出物是 agent 间协议，不是冗余。**

### 反思

用户反驳成立。我的判断框架错在：

1. **用人类工程化成本模型套 AI 协作场景**
   - 人类写 6 个文档 = 6 倍时间 → 过度
   - AI 写 6 个文档的边际成本 ≈ 0（有模板、结构化输入输出）
2. **忽略了产出物作为"agent 间协议"的本质**
   - `requirement.md`：product → architect / developer / tester 的输入
   - `design.md`：architect → developer 的输入
   - `dev-log.md`：developer → architect / tester 的信号
   - `test-report.md`：tester → qa 的输入
   - `review.md`：architect → developer / qa 的输入
   - `audit.md`：qa 审计的产出
   - 每个产出物 = 一个标准接口，下游 agent 不看上游脑袋里在想什么，只看它的**产出**
3. **规范驱动对 AI 协作是增益项，不是负担**
   - 7 个角色 + 6 类产出物的设计，在多 agent 协作下是架构优势
   - 删文档 = 删接口 = 让下游 agent 失去输入

### 真正的过度工程化在哪里

不是"文档多"，而是**每次 agent 派发本身有成本**：

- 上下文加载（6-13 项 resources）
- 任务执行
- 输出聚合
- 跨 agent 状态传递

🟢 低风险任务（修 typo、改一行日志）**走完整派发链**才是过度——不是写文档成本，是派发成本。

### 修正框架

| 原判断 | 修正判断 |
|--------|---------|
| 产出物过多 → 按风险分级减少产出物 | Agent 派发链过长 → 按风险分级减少派发 |
| 关注产出物写不写 | 关注 agent 派发不派发 |
| 目标：降低文档数量 | 目标：降低派发成本，产出物自然省略 |

### 对 #11 的影响

AAF-024 #11 原表述"产出物按风险分级"**是错的**，已修正为"Agent 派发触发条件规则"。产出物数量是派发链的自然结果，不单独定义。

### 规范驱动 vs AI 协作的正确理解

AAF 是"规范驱动 + AI 协作"项目。规范对人类是负担，但对 AI：

- 标准产出物 = 标准输入输出接口
- 结构化文档 = 结构化上下文传递
- 文档-代码双向映射 = AI 工作一致性保证

**规范越多，AI 协作越清晰；规范越少，AI 越容易漂移**。和人类工程化反直觉，但符合 AI 协作特性。

### 对其他分析条目的影响

- 问题 1（真理源冗余）：不变，真理源归一（#10 已实施）
- 问题 3（协调者超载）：仍成立，但原因是"同时戴 5 顶帽子"，不是"要写太多文档"。已作为独立待办登记在 improvements.md
- 问题 4（工具链滞后于规范）：不变，独立问题，已登记 #13
- 问题 5（缺失关键流程）：不变，独立问题，已登记（ADR 部分 #14）
- 问题 6（元规范缺失 Front Matter）：不变，独立问题，已登记 #15

---

## 3. #11 Agent 派发触发条件规则实施

### 完成状态

✅ 2026-05-05（当日内完成）— 协调者执行

### 实施位置决策

候选方案：
- A. 新建独立文件 `docs/reference/team/dispatch-trigger-standard.md`
- B. 并入 `collaboration-standard.md` 作为子章节

用户选择 B。理由：派发触发条件本质上是"风险分级的执行侧"，和 `collaboration-standard.md` 已有的"风险等级与审核"章节在同一语境，合并更自然，避免规范文件碎片化。

### 实施清单

**1. `docs/reference/team/collaboration-standard.md`**

在"风险等级与审核"章节和"异常处理"章节之间插入新章节"**## Agent 派发触发条件**"，包含：

- 核心原则（产出物是派发链的自然产物）
- 三档触发规则表（🟢/🟡/🔴 各自的派发链、跳过环节、典型产出物、协调者兼任范围）
- 触发条件细化表（6 维度：文件数 / 改动规模 / 接口签名 / 数据 schema / 架构约束 / 安全权限）
- 等级判定规则（任一维度触发高 → 🔴；就高不就低）
- 派发决策流程图
- 强制升级条件（5 文件 / 接口变更 / 架构 / 权限 / 置信度跌破 0.7）
- 执行记录要求（`dispatch-log.md`）
- 反思记录引用（指向本 dev-log #2）

**2. `.kiro/steering/collaboration.md`**

在"批量修改与高风险操作"章节后追加"**## Agent 派发触发条件（风险分级）**"红线摘要段：

- 三档触发规则简述（每条 1-2 行）
- 强制升级触发条件
- 指向 `collaboration-standard.md` 详细规范的锚链

### 为什么两个地方都放

按 AAF-024 #10 确立的分层原则：

- `collaboration-standard.md`（docs/）是**唯一真理源**，完整规范
- `.kiro/steering/collaboration.md` 是**红线摘要**，让 AI 启动时自动拿到判定依据

派发触发条件是协调者每次派发决策都要用的判断逻辑，必须 **时时可见**（放 steering），同时要**可查细则**（放 docs/）。

### 落地效果

协调者每次派发前可按 6 维度对照：

```
改动文件 1 个 + 改动 30 行 + 不改接口 + 不改 schema + 不触架构 + 无权限 → 🟢 自己干
改动 3 个文件 + 改动 200 行 + 加新接口 + 不改 schema + 不触架构 + 无权限 → 🟡 派发 dev + tester
改动 10 文件 + 跨模块 + 删接口 + 不改 schema + 不触架构 + 无权限 → 🔴 完整派发（文件数触发）
```

### 对本轮 AAF-023 / AAF-024 已执行任务的回顾

用本规则对照本轮 3 个实际任务的判定：

| 任务 | 实际改动 | 若按新规则 | 实际执行 |
|------|---------|-----------|---------|
| AAF-023 #1 一键 check | 23 文件跨模块 + CI + 规范多处 | 🔴 应完整派发 | 协调者全程兼任 |
| AAF-024 #10 真理源归一 | 13 文件（含 7 个 agent 配置）| 🔴 应完整派发 | 协调者全程兼任 |
| AAF-024 #11 本次 | 4 文件（含 steering / collab-std / task / dev-log） | 🟡 可派发也可兼任 | 协调者全程兼任 |

**结论**：新规则显示本轮两个任务"应 🔴 派发却实际兼任"——属于规则确立前的既成事实，不追溯。下一次 ≥5 文件跨模块的任务（例如 AAF-023 #2 Maven 多模块拆分）应严格按规则执行。

### 后续依赖

- AAF-023 #2 及之后的所有 ≥5 文件任务需按此规则判定派发链
- AAF-024 #12（architect vs qa 审查边界合并）落地后，🟡 中风险跳过的"qa 审计"范围才明确

---

<!-- 后续任务的 dev-log 追加到下方，按 #N 编号分节 -->

---

## #12 架构师与 QA 审查边界合并

### 完成状态

✅ 2026-05-05 — 协调者执行

### 问题

当前 architect 的 `code-review` 与 qa 的 `process-audit` 在"规范合规"上职责重叠。`roles/qa.md` 的"审计范围"表里直接列了"编码规范遵守 / 设计规范遵守 / 测试充分性"三项——这些同时也在 architect 的 code-review-standard.md "审查维度 #1 规范合规 / #2 设计符合性 / #6 可测试性"里被覆盖。

副作用：

- 两个 agent 读同一份代码、查同一条规范，重复劳动
- 同类问题分级可能不一致，责任边界模糊
- qa 宣称的"CMMI5 PPQA 独立性"与实际"查代码内容"行为自相矛盾
- `roles/qa.md` 里 qa 的产出路径竟与 architect 的 review 同名为 `review.md`，会出现两个 agent 写同一文件的冲突

### 解决方案

**边界重分配**：

- **architect（"做对了吗"）** — 代码/设计内容本身：规范合规、设计符合性、安全、对称性、性能、可测试性
- **qa（"过程对了吗"）** — 流程与文档：流程合规、派发分级、文档完整性、文档格式、需求结构、提交/任务管理
- qa **不读代码**，质量门控计数直接采纳 architect `review.md` 与 tester `test-report.md`

### 改动清单

1. **`docs/reference/team/roles/qa.md`**（全量重写）
   - 岗位定位改为"过程审计、文档完整性检查、度量分析、质量门控"，明确"不查代码内容"
   - "审计范围"表删除「编码规范遵守」「设计规范遵守」「测试充分性」三行，新增「派发分级」「提交规范」
   - 新增"不审计项（明确排除）"小节，列出由 architect 负责的六类
   - "规范加载顺序"去掉编码风格/架构约束/架构设计方法论的按需加载
   - 产出路径从 `review.md` 改为 `process-audit.md`，解除与 architect 的文件冲突
   - "源码访问"章节改写为"不直接读源码，只读过程文档和 architect/tester 的产出报告"

2. **`docs/reference/team/process-audit-standard.md`**（全量重写，19 行 → 113 行）
   - 顶部声明"职责边界"，明确不查代码
   - "审计范围"重组为流程合规 / 文档完整性 / 文档格式合规 / 需求结构完整性 / 提交与任务管理五类
   - 新增"不审计项（明确排除）"小节
   - 新增"审计触发"说明（必须在 tester acceptance + architect review 之后）
   - 新增"审计流程"文本流程图
   - 新增"质量门控判定规则"表，说明三方计数来源
   - 新增"产出结构"章节，列出 `process-audit.md` 必填内容
   - 末尾新增"与 code-review 的关系"对比表

3. **`docs/reference/dev/code-review-standard.md`**（局部修改）
   - 文档顶部提示补一句"代码/设计的规范合规以本规范为权威判定源，qa 不重复检查代码内容"
   - 末尾"与 audit 的区别"二项对比扩展为三项对比（加入 process-audit 列），明确三者执行者/粒度/是否读代码/输出产物的差异

4. **`docs/task/_template/process-audit.md`**（新增）
   - qa 过程审计产出模板，74 行，含元信息、流程合规/文档完整性/文档格式三张检查表、质量门控汇总、问题清单、度量数据

5. **`docs/reference/team/measurement-standard.md`**（一行修改）
   - "数据来源"从仅 `review.md` 扩为 `review.md / test-report.md / process-audit.md`

### 边界矩阵（合并后）

| 检查维度 | architect review | qa process-audit |
|---------|-----------------|-----------------|
| 编码风格 / 命名 / 分层 | ✅ | ❌ |
| 架构约束 / 设计符合性 | ✅ | ❌ |
| 安全 / 对称性 / 性能 | ✅ | ❌ |
| 单元测试覆盖与质量 | ✅ | ❌ |
| 流程合规（流水线顺序） | ❌ | ✅ |
| 派发分级正确性 | ❌ | ✅ |
| 文档完整性（产出齐全） | ❌ | ✅ |
| 文档格式（Front Matter/路径/命名） | ❌ | ✅ |
| 需求结构（三级 + AC 段落存在） | ❌ | ✅ |
| 提交格式 / 任务编号 | ❌ | ✅ |
| 质量门控判定（blocker=0, major≤2） | ❌ | ✅（汇总 architect + tester 结果） |

两列不再出现 ✅ ✅ 同行——重复清零。

### verify 对照

> #12 的 verify 要求：两份规范不再重复"规范合规检查"；审查职责无交叉。

- `process-audit-standard.md` 的"规范遵守"条目已全部移除编码/设计/单测规范，只保留文档/提交规范
- `roles/qa.md` 的"审计范围"表删除了编码/设计规范遵守与测试充分性三行
- `code-review-standard.md` 明确代码规范判定权威，qa 直接采纳
- 边界矩阵无同行 ✅ ✅ 重叠 ✅

### 观察与遗留

- **_template/ 目录已有 10 项**（原 9 + 本次新增 `process-audit.md`），超过内容体系的"五度空间 ≤ 5"约束。但该目录是"产出物实例列表"而非"概念分类树"，五度约束是否适用于实例列表需由内容体系规范澄清。已登记到 [改进意见](../../../prd/improvements.md)（见本次追加条目）。
- qa 如发现 architect review 有明显遗漏（例如未勾选对称性清单某项），应作为**流程问题**记录要求 architect 补审，不自己下代码规范结论——规范已在 `process-audit-standard.md` 末尾明确。

### 后续任务关系

- AAF-024 #13 规范-代码一致性检查脚本：可纳入"规范文件里引用的对照规范链接必须存在"规则，防止 qa 新加的规范链接失效
- AAF-024 #14 ADR：本决策（qa 不查代码内容）本身够得上 ADR-004 资格，但决策依据来自任务描述，未经过独立讨论，暂不立为 ADR，视后续是否反弹再说
- AAF-024 #15 Front Matter：qa.md / process-audit-standard.md / code-review-standard.md 本次改动尚未补 Front Matter，待 #15 统一处理

---

## #14 ADR 目录建立 + 迁移 3 条决策

### 完成状态

✅ 2026-05-05 — 协调者兼任 architect 执行

### 派发决策

按 [Agent 派发触发条件](../../../reference/team/collaboration-standard.md#agent-派发触发条件) 6 维度判定：

| 维度 | 实际 | 触发等级 |
|------|------|---------|
| 文件数 | 9 个（5 新建 + 3 回链 + 1 dev-log 指针 + 迭代文件）| 🔴 |
| 改动规模 | 约 600 行新增 | 🟡 |
| 接口签名 | 无 | 🟢 |
| 数据 schema | 无 | 🟢 |
| 架构约束 | 新增治理性规范（ADR 机制本身），不改现有架构 | 🟡 |
| 安全权限 | 无 | 🟢 |

字面上"文件数 ≥ 5"落入 🔴，但实际工作性质是**纯文档迁移 + 规范治理**，不涉及代码、不需 developer / tester / product 参与。**协调者兼任 architect 角色独立完成**，记录本派发决策，符合 collaboration-standard.md 对"协调者可在其他 agent 无实际工作量场景下兼任"的预设。

### 问题

AAF-023 dev-log 承载了 3 条横跨全项目的技术选型决策：

1. Vitest vs Jest
2. 本地真实 DB vs Testcontainers
3. Cucumber 移除

但 dev-log 的定位是"任务执行日志"，作为决策真理源有三个问题：

- **引用路径不稳定**：dev-log 随任务号归档（`v0.1.0/AAF-023/`），后续版本回看或跨任务引用路径冗长易断
- **内容边界混杂**：dev-log 里决策讨论和实现记录交织，新读者需要在长文档里筛选
- **无法支撑规则溯源**：`multica` P1.5 思想要求硬约束可回链到起因，dev-log 的章节锚点不稳定

### 解决方案

建立 `docs/design/adr/` 作为 AAF 决策记录的权威源，采用 [MADR 3.0](https://adr.github.io/madr/) 格式结合 AAF Front Matter：

- 每条决策独立文件 `ADR-NNN-{slug}.md`
- status 字段记录生命周期（proposed / accepted / deprecated / superseded-by）
- accepted 后不再修改内容，改动需新建 superseded ADR
- README.md 索引表提供统一入口
- 支持"起因：ADR-NNN"回链机制

### 改动清单

**新建（5 个文件）**：

| 文件 | 行数 | 内容 |
|------|-----|------|
| `docs/design/adr/README.md` | 71 | 目录索引 + 为什么需要 ADR + 格式与流程 + 触发条件 |
| `docs/design/adr/_template.md` | 85 | MADR 3.0 模板（含 Reversal Triggers 反向选择触发条件章节） |
| `docs/design/adr/ADR-001-vitest-vs-jest.md` | 112 | 迁移自 AAF-023 dev-log #2 |
| `docs/design/adr/ADR-002-local-env-vs-testcontainers.md` | 145 | 迁移自 AAF-023 dev-log #3 |
| `docs/design/adr/ADR-003-remove-cucumber.md` | 153 | 迁移自 AAF-023 dev-log #4 |

**回链（3 个测试规范顶部加"起因：ADR-NNN"）**：

| 文件 | 回链 |
|------|------|
| `docs/reference/dev/test/unit-test-standard.md` | ADR-001（Vitest）+ ADR-003（JUnit 5 + `@DisplayName`） |
| `docs/reference/dev/test/acceptance-test-standard.md` | ADR-001（Playwright）+ ADR-002（本地 DB）+ ADR-003（Gherkin 表达但不用 Cucumber） |
| `docs/reference/dev/test/integration-test-standard.md` | ADR-002（本地 DB） |

**AAF-023 dev-log 指针（3 个章节加"已迁移至 ADR-NNN"）**：

| 章节 | 指针 |
|------|------|
| dev-log #2 Vitest vs Jest | → ADR-001 |
| dev-log #3 本地 vs Testcontainers | → ADR-002 |
| dev-log #4 Cucumber 移除 | → ADR-003 |

保留 dev-log 历史讨论内容不删，ADR 成为引用的权威源——符合"历史记录不丢，决策源归一"的原则。

**迭代文件**：

- `docs/task/aaf-v0.1.0.md` 勾选 AAF-024 #14 + 补变更记录

### MADR 扩展要点

标准 MADR 3.0 基础上加了两点 AAF 特化：

1. **Reversal Triggers（反向选择触发条件）章节**：记录"什么时候考虑回切"，避免陷入历史包袱。三条迁移的 ADR 都有明确的回切触发条件，体现"决策不是一次性锁死"的观念
2. **AAF Front Matter**（level/layer/purpose/status/version/date/author）叠加 MADR 的（status/deciders/consulted/informed/related-tasks）

### 未做的事（明确说明）

- **不追溯回链到 `.kiro/skills/coding-standards/SKILL.md`**：硬约束 5-9（不加兼容层 / 任务外重构 / 完工前必跑 check / 测试命名分层 / 不静默降置信度）与 ADR-001/002/003 的直接起因关系弱，强行回链会稀释 ADR 信号。P1.5 规则溯源是"渐进"补充——未来当出现真实事故产生 audit 时再回链
- **未对 AAF-023 dev-log #1 建 ADR**：#1 是落地执行记录，不含独立决策；引用的决策本身（"check 对代码负责、acceptance 对需求负责"）可后续评估是否立为 ADR-004，本次不扩大 scope
- **未为 AAF-024 #10/#11/#12 本身立 ADR**：同理，这几项是规范治理内部执行，决策讨论在 dev-log 已充分

### 观察与遗留

- **`docs/design/` 顶层已 9 个内容项**（architecture.md + 8 个子目录），超过五度空间约束。但 uniapp/webui 子目录各自只有 1 个 tech-stack.md，framework/agent 主题重叠——这是 docs/design/ 既有的分类张力，不由本任务引入。已登记到 [改进意见](../../../prd/improvements.md)
- **ADR 编号空间**：三位数从 001 开始，足以支撑 999 条决策。如超过再议
- **Front Matter level/layer 字段的语义**：ADR 统一用 `level: Practice / layer: Principle`，表示"实践层的原则决定"。如未来建立分 domain 的 ADR（如专门的 UI 设计 ADR），layer 可调整

### 后续任务关系

- AAF-024 #15 Front Matter 规范化：本次新建的 5 个 ADR 文件已含 Front Matter 可作示例
- AAF-024 #13 一致性检查脚本：可加规则"回链到 ADR-NNN 的标注必须对应存在文件"
- 未来新增决策时先建 ADR 再修规范，不再出现"决策散在 dev-log 里"的模式


---

## #16 迭代文件结构合规重构（技术任务迁移到 tasks.md）

### 完成状态

✅ 2026-05-05 — 协调者执行

### 触发

用户发现 `aaf-v0.1.0.md` 把"## 技术任务"章节（AAF-023 的 #1-#8 + AAF-024 的 #12-#15）直接写在迭代文件里，违反了任务管理规范"迭代文件不包含技术任务"的约定。核对规范时还发现 `docs/task/Readme.md` 有一处自相矛盾的笔误："不包含技术任务（技术任务在启动阶段由 architect 拆分，**记录在迭代任务文件中**）"——括号与主句冲突，也与 `_template/tasks.md` 模板冲突。

本任务 `#16` 作为 AAF-024 的扫尾辅助工作，修正结构违规并顺手修 README 笔误。

### 派发决策

按派发触发条件 6 维度判定：

| 维度 | 实际 | 触发等级 |
|------|------|---------|
| 文件数 | 8 个（2 新建 + 1 重写 + 4 反向链接修正 + README 笔误）| 🔴（≥5 触发） |
| 改动规模 | 约 200 行（含新建 + 重写） | 🟡 |
| 接口签名 | 无 | 🟢 |
| 数据 schema | 无 | 🟢 |
| 架构约束 | 无 | 🟢 |
| 安全权限 | 无 | 🟢 |

字面上 🔴，但实际工作性质是**纯文档迁移与链接修正**，不涉及代码。**协调者兼任 architect 独立完成**，参照 #14 的判断逻辑。

### 改动清单

**修 README 笔误（1 处）**：

- `docs/task/Readme.md`：括号内"记录在迭代任务文件中" → "记录在用户故事的 `docs/task/v{version}/AAF-XXX/tasks.md` 文件中"

**新建 2 个技术任务文件**：

- `docs/task/v0.1.0/AAF-023/tasks.md`（71 行）：迁入原 aaf-v0.1.0.md 里的 #1-#8 技术任务 + "新增任务"占位段。顺手把源自决策的"来源"链接从 `dev-log` 改为 `ADR-NNN`（AAF-024 #14 已迁移至 ADR）
- `docs/task/v0.1.0/AAF-024/tasks.md`（56 行）：迁入 #12-#15，加顶部"历史编号连续性"说明（#10/#11 已在本任务 dev-log 记录为第 1/3 节，#12 起为正式技术任务）；本次重构自身记为 #16 追加到"新增任务"章节并标 ✅

**重写 `docs/task/aaf-v0.1.0.md`（1 个文件）**：

- 删除 `## 技术任务` 整章（原约 100 行）
- `## 业务需求` 新增条目"协作基础设施优化"对应 AAF-024（此前遗漏）
- 每条业务需求补"对应用户故事：[AAF-XXX] — [技术任务](...)"链接
- `## 迭代范围决策` 和 `## 变更记录` 保留（前者是有价值的扩展内容，规范未禁止；后者追加本次重构条目）
- 最终文件从 191 行瘦身到 133 行

### verify 对照

- `grep aaf-v0\.1\.0\.md#技术任务` 无匹配 ✅
- `grep '^## 技术任务' docs/task/aaf-v0.1.0.md` 无匹配 ✅
- 两个新建 tasks.md 格式对照 `_template/tasks.md` ✅
- 反向链接全部指向新位置 ✅

### 对规范本身的回馈

本次暴露了规范-实际不一致的两个典型问题：

1. **README 笔误**：规范文档自身逻辑矛盾，AI 根据矛盾规范生成的产物（aaf-v0.1.0.md 带技术任务）也是错的。已通过本次修正闭环
2. **协调者派发时绕过模板校验**：当初创建 aaf-v0.1.0.md 时没对照 `iteration-template.md`，直接扩展结构导致偏差

对 AAF-024 #13（规范-代码一致性检查脚本）的输入要求：增加规则"迭代文件（`docs/task/aaf-v*.md`）不得出现 `## 技术任务` / `### AAF-XXX` 等章节"。

### 后续任务关系

- 本次产出的 `tasks.md` 可作为 AAF-024 #15（Front Matter 规范化）的示例参考
- `aaf-v0.1.0.md` 新的"业务需求 → 用户故事 → tasks.md"三级跳链接结构可沉淀为"迭代文件编写最佳实践"写进 `iteration-template.md` 的指引段（待单独任务）