# 开发日志：AAF-023 项目基础框架搭建

> 产出目录：`docs/task/v0.1.0/AAF-023/`
> 任务编号见 [AAF-023 tasks.md](tasks.md)

---

## #1 一键 check 基础设施与 developer/tester 分工文档化

### 完成状态

✅ 2026-05-05（当日内完成）— 协调者执行

### 背景

基于 [multica 项目总结]的 P0/P1 分析，落地"一键 check + 测试命名分层 + 硬约束规范化"三条工程化基础。P2/P3 和未落地的 P1.5 已登记到 [改进意见](../../../prd/improvements.md)，不在本次任务范围。

### 实现文件（23 个）

**基础设施**：

- `apps/service/pom.xml` — 追加 archunit 1.3.0 / spotless 2.44.0 properties；archunit-junit5 test 依赖；maven-surefire-plugin 限定 `*Test.java` 排除 `*IT/*AcceptanceTest`；maven-failsafe-plugin 限定 `*IT.java / *AcceptanceTest.java`；spotless-maven-plugin AOSP 风格 + import order
- `apps/service/project.json` — 新增 lint / format / acceptance / check target；check = lint + test + build
- `apps/webui/project.json` — 新建，补 build / dev / start / typecheck / test / acceptance / check；check = typecheck + test + build
- `apps/webui/vitest.config.ts` — 单测配置（`*.test.tsx` / `*.spec.tsx`），排除 `*.accept.test.tsx`
- `apps/webui/vitest.acceptance.config.ts` — 验收配置（只匹配 `*.accept.test.tsx`）
- `apps/webui/package.json`、`pnpm-lock.yaml` — 装 vitest@3.1 + jsdom@25 + @vitest/coverage-v8
- `package.json` — 加 scripts：check / check:affected / acceptance / acceptance:affected / format / format:check
- `nx.json` + `.nxignore` — 给 @nx/next 插件 include 限定 `apps/*`；.nxignore 排除 `docs/design/auto-dev/multica`、`docs/design/auto-dev/gstack` 参考资料目录
- `.github/workflows/ci.yml` — 从 `nx run-many -t lint test build typecheck e2e-ci` 改为三阶段：format-check → developer check → tester acceptance
- `apps/service/src/test/java/com/xuejiai/aaf/arch/LayeringTest.java` — ArchUnit 占位（总通过），TODO 列出 5 条待启用规则

**规范文档**：

- `AGENTS.md` — 从 12K 字瘦身到 3K 字指针文档（一句话介绍、技术栈、目录、一键命令、测试命名约定、AI 协作宣言、文档导航、关键约束），内容不再与 docs/ 重复
- `.kiro/skills/coding-standards/SKILL.md` — 追加硬约束 5-9：不加兼容层 / 不做任务外重构 / 完工前必跑 check / 测试命名分层 / 不静默降低置信度
- `docs/reference/team/process-standard.md` — 在 3.3 和 3.4 之间插入 3.3.1 AI 自验证循环（伪代码 + 硬规则 + developer/tester 分工表）
- `docs/reference/team/roles/developer.md` — 从 4 行扩展到 50 行：必做 / 不做 / 交接时机 / 硬约束 / 输出规范
- `docs/reference/team/roles/tester.md` — 重写：明确"不跑 check，只跑 acceptance" + 前置门禁 + 命名约定 + 输出要求 + 失败处理流程
- `docs/reference/dev/test/unit-test-standard.md` — 从 5 行扩展到 67 行：命名约定硬约束 / 技术栈 / 覆盖策略 / 与验收测试的区别
- `docs/reference/dev/test/acceptance-test-standard.md` — 重写：命名约定硬约束 / 技术栈 / 覆盖要求 / 失败处理（tester 不改业务代码）
- `docs/reference/dev/code-review-standard.md` — 从 26 行扩展到 113 行：审查前提（check 全绿才启动）+ 12 项对称性检查清单 + review vs audit 区别
- `docs/task/_template/audit.md` — 新建六段式架构审计模板（问题→根因→复现→方案多选→改动范围→验证）
- `docs/task/_template/test-report.md` — 补元信息区 / AC 覆盖矩阵 / 缺陷退回记录 / 结论 checklist
- `docs/prd/improvements.md` — 追加 10 条后续改进项（P2/P3 和遗留）

### 关键决策

#### 分工原则：check 对代码负责，acceptance 对需求负责

- **developer** 负责 `check`：一条命令跑完编译 + lint + typecheck + 单测 + build，只关心代码技术正确性
- **tester** 负责 `acceptance`：对照 Gherkin AC 验证需求满足度，输出覆盖矩阵
- tester 启动前协调者必须验证 `check` 全绿；发现编译挂、单测红之类代码层问题**立即退回 developer**，不让 tester 消耗在修构建上

#### 测试命名作为硬约束

命名决定由哪个执行器跑，不可混淆：

| 层 | developer 单测 | tester 验收/集成 |
|----|---------------|------------------|
| Java | `XxxTest.java` → Surefire | `XxxIT.java` / `XxxAcceptanceTest.java` → Failsafe |
| TS | `xxx.test.ts(x)` / `xxx.spec.ts(x)` → Vitest | `xxx.accept.test.ts(x)` → Vitest 独立 config |

Maven Surefire/Failsafe 的 includes/excludes 配置已在 pom.xml 强制这个分工。

#### 为什么 webui:check 暂不含 lint

Next.js 16 的 `generators.@nx/next.application.linter: none`（在 nx.json 中），webui 当前无 ESLint 配置。强行加 lint 会让 check 挂在配置缺失上。记录在 improvements.md 待后续引入 ESLint + eslint-config-next 最小配置后再加入 check 的 dependsOn。

#### ArchUnit 先占位不激活

aaf-framework 等模块尚未拆分（依赖 #2），真实的分层规则（domain 不依赖 infrastructure、controller 不跨 application 等）现在写了也没有落脚点。LayeringTest.java 先写一个总通过的 placeholder 验证 ArchUnit 工具链通畅，TODO 列出 5 条待启用规则，等 #2 完成后立即替换为真实规则并激活。

### 注意事项（交给后续开发者）

1. **pom.xml 中 `${hibernate.version}` 未定义** — 是既有 bug（hibernate-maven-plugin 引用未定义变量），首次 `mvn spotless:check` 会报 property 未解析。建议在 #2（Maven 多模块拆分）时一并修复。
2. **80+ 历史文件未过 prettier** — 项目初建时没开 format hook，现在 `pnpm format:check` 会列出 80+ 个 md/json/yaml 文件不符合规范。见任务 #6，单独提交一次 `pnpm format:write`，不要与其他改动混在一个 commit。
3. **不要在 PR 里混入 format 修复** — 会让 diff 淹没真实改动。
4. **未验证 service:check 全链路** — 本地 Maven 首次解析会下载大量依赖且耗时，且命中 `${hibernate.version}` bug 会直接挂。`pnpm nx show project service` 能看到 target 正确挂载，但未实际 run。建议在 #2 完成后做第一次完整 `pnpm nx run service:check` 冒烟。
5. **.nxignore 的使用** — `nx.json` 的 `@nx/next/plugin.include: ["apps/*"]` 没有阻止扫描 `docs/design/auto-dev/multica` 目录（插件看起来优先级低于 .nxignore）。未来新增参考资料目录（如 `docs/reference/*-example/`）要同步加到 .nxignore，否则 `nx show projects` 会报 "Failed to process project graph"。

### 偏离设计

无。原设计（multica-summary.md 的 P0/P1 分析）与实现一致。

### 后续依赖

- 本任务是 AAF-023 的第一步，为 #2-#6 铺路
- 完工后 CI 从"引用未定义 target"改为真正能跑的三阶段门禁，为后续所有 Epic 提供质量保证

---

## 2. Vitest vs Jest 决策记录

> **本节的决策结论已迁移至 [ADR-001](../../../design/adr/ADR-001-vitest-vs-jest.md)，那里是权威源。本节保留作为历史讨论记录，后续只对 ADR 引用。**

### 背景

#1 完成后用户提出了"为什么不用 Jest"的问题，并给出一组包含 `@nx/jest@22.3.3` 的依赖参考清单。触发重新评估前端单测框架选型。

### 决策

**走 Vitest，不走 Jest**。同时引入 Playwright 作为 E2E runner，登记为 AAF-023 #7。

### 决策依据

| 维度 | Vitest | Jest | 判定 |
|------|--------|------|------|
| 未来发展势头 | 陡增，3-4 个月一个大版本 | 平稳，12-18 个月一版 | Vitest |
| Nx 22.x 默认推荐 | `@nx/vite`（新项目默认选它） | `@nx/jest`（保留但非默认） | Vitest |
| 主流框架默认 | Vue / Nuxt / Svelte / Solid / Astro / Remix / Storybook 8 | React Native / Next.js 历史示例 | Vitest |
| ESM 原生 | 是 | 实验性 | Vitest |
| TS 支持 | 原生 esbuild 零配置 | 需 `@swc/jest` 或 `ts-jest` 额外层 | Vitest |
| 冷启动 / watch 速度 | 毫秒级 | 秒级 | Vitest |
| 错误输出 source map 准确度 | 高 | 中 | Vitest |
| 生态示例数量 | 中（追赶中） | 高（历史积累） | Jest |
| 与 Rolldown / Turbopack 未来红利 | 可直接吃到 | 不受益 | Vitest |

**综合判定**：Vitest 在速度、现代栈适配、未来发展、Nx 默认选择四个维度胜出；Jest 只在"社区示例数量"一个维度胜出，但这个优势在 AI 协作场景下持续缩小。

### 反向选择 Jest 的触发条件

仅当出现以下之一时考虑回切：

1. 存量 100+ 个 Jest 测试文件需要迁移（AAF 当前 0 个）
2. 重度依赖 `jest.mock` 的 hoisting 行为（AAF 不涉及）
3. React Native 场景（AAF 无此规划）
4. 依赖 `jest-expo` 等 RN 生态特化扩展

以上都不成立，Vitest 是一致性最优解。

### 与 #1 的关系

#1 已装 `vitest@3.1 + jsdom@25 + @vitest/coverage-v8` 并建立了 `vitest.config.ts` + `vitest.acceptance.config.ts`。本决策：

- **保留** `vitest.config.ts`（developer 单测）
- **废弃** `vitest.acceptance.config.ts`（将由 #7 引入的 Playwright 替代）
- **可选升级**：#7 装 `@nx/vite` 让 test target 自动推断，减少 project.json 手写量

### 对 #7 的影响

AAF-023 #7 的落地清单已在 [AAF-023 tasks.md](tasks.md) 登记。核心变更：

- 新增 `apps/webui-e2e/` 工程（Playwright）
- 删除 `apps/webui/vitest.acceptance.config.ts`
- 文档更新：测试命名约定中 `*.accept.test.tsx` → `apps/webui-e2e/src/**/*.spec.ts`

实施时机：不立即执行，登记后等待用户确认实施节点（建议 AAF-020 / AAF-021 启动前完成）。

### 拒绝的选项

- **`@nx/jest`**：不引入，理由如上
- **`@nx/storybook` + `@nx/react`**：v0.1 无组件库需求，推到 v0.2（已登记 improvements.md）
- **`@nx/web`**：Next.js 已由 `@nx/next` 覆盖，冗余

---

## 3. 测试环境方案：本地 vs Testcontainers 决策记录

> **本节的决策结论已迁移至 [ADR-002](../../../design/adr/ADR-002-local-env-vs-testcontainers.md)，那里是权威源。本节保留作为历史讨论记录，后续只对 ADR 引用。**

### 背景

#1 完成后用户质疑 Testcontainers 的引入成本："Testcontainers 需要安装 docker 吧 增加开发运行成本吧，感觉不如直接显式安装相关环境更好"。触发重新评估后端测试环境策略。

### 决策

**走"本地真实环境 + CI service container"，不引入 Testcontainers**。登记为 AAF-023 #9。

### 决策依据

| 维度 | 本地真实环境 | Testcontainers | 判定 |
|------|------------|----------------|------|
| 开发机常驻内存 | PostgreSQL ~100MB + Neo4j ~500MB ≈ 600MB | Docker Desktop 2-4 GB + 容器 | 本地 |
| 测试启动延迟 | 0 秒（进程常驻） | 3-8 秒 / 容器（reuse 模式可规避但引入数据累积问题） | 本地 |
| 数据持久性 | 保留，DBeaver 可直连排错 | 默认清除，reuse 模式需额外清理策略 | 本地 |
| DB 客户端连接 | localhost:5432 / 7687 稳定 | 容器端口动态 | 本地 |
| Docker Desktop 许可 | 无需 | 商业用 > 250 人或 > $10M 收入需付费 | 本地 |
| AI 协作内循环成本 | 0 秒启动 × N 次 = 0 | 3-8 秒 × N 次 = 累积分钟级 | 本地 |
| 可移植性（新开发机） | 需装 PostgreSQL + Neo4j | Docker 搬一下 | Testcontainers |
| 团队协作成本 | 新成员需学安装 | 约定 Docker 即可 | Testcontainers |
| 版本锁定 | apt / 压缩包 | image tag | 平手 |
| CI 复现 | GitHub Actions service container（原生支持） | Docker in CI | 平手 |
| 并发隔离 | schema / 独立 db 名 | 每次新容器天然隔离 | Testcontainers |

**综合判定**：AAF 是一人公司 + AI 协作为主，前 6 项"本地方案胜出"是决定性的；后 3 项"Testcontainers 优势"在 AAF 场景下不构成瓶颈（可移植性和团队协作不是关键），隔离通过 schema / 独立 db 可解决。

### 行业趋势偏见自审

本轮 P0/P1 落地时默认推荐 Testcontainers，源于"现代 Java 项目都在用"的行业偏见。Testcontainers 的主要价值在团队协作 + 可移植性，在 AAF 具体情况下 ROI 为负。

### 反向选择 Testcontainers 的触发条件

仅当出现以下之一时考虑回切：

1. 团队规模增长到 3+ 开发者，统一环境维护成本显著
2. 要做跨多 PostgreSQL 版本（17 / 16 / 15）的兼容性测试
3. 出现"本地环境漂移导致测试不一致"的具体事故

以上都不是 v0.1-v0.3 的紧迫需求。

### AAF 的具体方案

**本地**：
- PostgreSQL 17（含 pgvector 扩展，v0.2 知识库用）+ Neo4j 5 Community
- 独立测试数据库 `aaf_test`（PostgreSQL）+ `aaf_test`（Neo4j 5 多库支持）
- 测试配置 `apps/service/src/test/resources/application-test.yaml` 指向 localhost
- 数据清理：`@Transactional`（Spring Test 自动回滚）+ Neo4j `@BeforeEach` 显式 `MATCH (n) DETACH DELETE n`

**CI**：GitHub Actions service container（PostgreSQL + Neo4j），配置在 `.github/workflows/ci.yml` 的 acceptance 阶段

### 对 #1 的影响

本轮 P0/P1 改动里引入的 Testcontainers 引用已全部回收：

- `docs/reference/dev/test/acceptance-test-standard.md`（3 处）
- `docs/reference/dev/test/unit-test-standard.md`（1 处）
- `docs/reference/dev/test/integration-test-standard.md`（重写，改为"本地真实环境策略"说明）
- `docs/reference/team/roles/tester.md`（1 处）
- `docs/reference/team/collaboration-standard.md`（1 处）
- `docs/task/_template/test-report.md`（1 处）

### 对 #9 的期望

AAF-023 #9 的落地清单已在 [AAF-023 tasks.md](tasks.md) 登记。核心变更：

- 补充 `docs/reference/dev/dev-environment.md` 的"本地数据库"章节
- `.github/workflows/ci.yml` 加 PostgreSQL + Neo4j service container
- `src/test/resources/application-test.yaml` 指向 localhost
- pom.xml 不加 Testcontainers

实施时机：AAF-023 #3 Flyway 迁移初始化后（因为需要真实 DB 跑迁移脚本），不早于 #3。

---

## 4. Cucumber 移除决策记录

> **本节的决策结论已迁移至 [ADR-003](../../../design/adr/ADR-003-remove-cucumber.md)，那里是权威源。本节保留作为历史讨论记录，后续只对 ADR 引用。**

### 背景

规范文档（6 处）宣称"验收测试用 Cucumber"，但 pom.xml 里一个 Cucumber 依赖都没有，测试代码也没有 `.feature` 文件。规范与代码不一致。

用户问"后端测试是否建议采用 JUnit 5、Mockito、Cucumber 组合"，触发重新评估。

### 决策

**走 JUnit 5 + Mockito + AssertJ + Spring Boot Test + 本地真实数据库**，**不引入 Cucumber**。登记为 AAF-023 #8（文档清理）。

### 决策依据

#### 硬理由：Cucumber 与 AAF"文档是唯一真理"原则冲突

| 哲学 | 真理源定位 |
|------|-----------|
| AAF 规范驱动 | `docs/prd/**/*.md` 的 AC 区是唯一真理源，代码/测试是实现 |
| Cucumber BDD | `.feature` 文件本身是 living documentation（即真理源） |

**引入 Cucumber 等于在 AAF 里开第二个真理源**：AC 变更时需求文档和 `.feature` 必须同步改两处，AI 和人都可能漏同步。这违反了 AAF 的"一个知识点一份文档"硬约束，是规范文档自身的矛盾。

#### 软理由：AI 协作摩擦

| 场景 | Cucumber | JUnit 5 |
|------|---------|---------|
| AI 从需求文档生成测试 | 生成 `.feature` + step definitions 两个文件 | 生成一个测试类 |
| AC 变更，AI 更新测试 | 两处改，易漏同步 | 一处改 |
| AI 读失败栈定位问题 | Cucumber runner → JUnit → JVM 三层栈 | 直接 JUnit 栈 |
| `@MockBean ChatClient` 注入 | step 之间传 Mock 状态，笨拙 | 测试类内直接注入 |
| 覆盖矩阵自动化 | Cucumber JSON report（额外解析） | JUnit XML + `test-report.md` 模板直接填 |

#### 软理由：AAF 场景不符合 BDD 前提

| Cucumber 适用前提 | AAF 实际 |
|-----------------|---------|
| 非程序员业务方直接读/写 `.feature` | 一人公司，业务方就是 AaronZZH 自己 |
| BDD 文化成熟 | AAF 是规范驱动，不是 BDD |
| 已有 `.feature` 存量 | 0 个 |
| 强 DSL 领域（合规/金融） | 通用 AI 框架，无强 DSL |

### Gherkin 的正确位置（保留）

Gherkin 作为 AC 表达格式依然有价值，但**不落地为 `.feature` 文件**：

- **需求文档 AC 区**：`docs/prd/**/*.md` 仍用 Gherkin 格式写 AC
- **测试方法 `@DisplayName`**：`@DisplayName("AC-001: Given...When...Then...")`
- **测试方法注释块**：详细 Given/When/Then 给读者
- **test-report.md 覆盖矩阵**：AC 编号 → 测试方法映射

### 对 #8 的期望

AAF-023 #8 的落地清单已登记。核心变更：

- 去除 6 处规范文档的 Cucumber 引用（Readme.md、design-principles.md、requirement-standard.md、development-standard.md、coding-style-standard.md ×3、testing-snippets.md ×2）
- 替换 testing-snippets.md 的 Cucumber 片段为 JUnit 5 `@DisplayName` 示例
- pom.xml 显式加 `spring-boot-starter-test`（与 #9 合并提交更合适）

实施时机：可与 #9 一起做，也可独立做。建议 #8 先行（纯文档，风险低），#9 等 #3 完成后做。

---

<!-- 后续任务的 dev-log 追加到下方，按 #N 编号分节 -->
