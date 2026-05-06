---
level: Practice
layer: Product
purpose: AAF-023 项目基础框架搭建的技术任务清单
status: active
version: 1.0.0
date: 2026-05-05
author: AaronZZH
---

# 项目基础框架搭建（AAF-023）

> 需求：项目基础框架搭建（详见 [aaf-v0.1.0.md 业务需求](../../aaf-v0.1.0.md#项目基础框架搭建)）
> 设计：[后端技术选型](../../../design/apps/service/tech-stack.md) | [后端模块结构](../../../design/apps/service/module-structure.md)
> 负责人：architect + developer-api + developer-web | 创建：05-05

## 任务列表

1. [ ] #1 后端 Maven 多模块拆分（aaf-dependencies / aaf-common / aaf-framework / aaf-auto-dev / aaf-api）
   verify: `pnpm nx run service:build` 成功，五个模块独立 jar 产出
2. [ ] #2 Flyway 数据库迁移初始化 + 环境配置分离（sys_user / doc_document / autodev_request 等初始表）
   verify: `pnpm nx run service:serve` 启动后数据库自动建表，application-dev/prod.yaml 隔离
3. [ ] #3 前端 Next.js 16 + TypeScript 脚手架规范化（目录结构、tsconfig、Nx 集成完善）
   verify: `pnpm nx run webui:dev` 启动成功，App Router 页面可访问
4. [ ] #4 ArchUnit 分层规则激活（替换 LayeringTest.java 占位，启用 5 条真实分层规则）(依赖: #2)
   verify: `pnpm nx run service:test` 运行 LayeringTest，违规用例即时报错
5. [ ] #5 首次全仓 prettier 格式化对齐 + 启用 CI format-check 强制门禁
   verify: `pnpm format:check` 通过，CI format-check 阶段绿
6. [ ] #6 前端测试栈对齐（Vitest + Playwright + @nx/vite） — developer-web
   - 保留 Vitest 作为单测 runner（已装，性能与 ESM/TS 原生支持优于 Jest；未来 Rolldown/Turbopack 红利可直接吃到）
   - 新增 Playwright 作为 E2E / 验收（取代现有 `vitest.acceptance.config.ts` 占位）
   - 可选装 `@nx/vite` 让 test target 自动推断，替代 webui/project.json 手写的 command
   - 不装：`@nx/jest`（走 Vitest 不走 Jest）、`@nx/web`（Next.js 已用 @nx/next）、`@nx/storybook` + `@nx/react`（v0.1 无组件库需求，延到 v0.2）
   - 落地步骤：
     1. `pnpm add -Dw @nx/vite@22.7.0 @nx/playwright@22.7.0 @playwright/test@^1.57.0 @parcel/watcher@^2.5.1`
     2. `pnpm nx g @nx/playwright:configuration --project=webui`（生成 `apps/webui-e2e/`）
     3. 删除 `apps/webui/vitest.acceptance.config.ts`
     4. 改 `apps/webui/project.json`：acceptance target 指向 webui-e2e 的 playwright；可让 @nx/vite 推断 test/build
     5. 更新 AGENTS.md / unit-test-standard.md / acceptance-test-standard.md / tester.md 的前端测试命名与工具栈
     6. dev-log.md 追加说明 #1 的 vitest.acceptance 被 #6 取代
   - 来源：[ADR-001 Vitest vs Jest](../../../design/adr/ADR-001-vitest-vs-jest.md)
   - verify: `pnpm nx run webui:test` Vitest 跑通；`pnpm nx run webui-e2e:e2e` Playwright 跑通；`pnpm nx show projects` 正确识别 service + webui + webui-e2e
7. [ ] #7 后端测试规范清理（去 Cucumber + 显式测试依赖）— developer-api + 协调者
   - 去除 6 处规范文档里的 Cucumber 引用（保留 Gherkin 作为 AC 表达格式，不落地为 `.feature` 文件）：
     - Readme.md（技术栈段）
     - docs/explanation/design-principles.md:64
     - docs/reference/dev/requirement-standard.md:141-142
     - docs/reference/dev/development-standard.md:30
     - docs/reference/dev/apps/service/coding-style-standard.md（3 处）
     - docs/reference/dev/snippets/testing-snippets.md（2 处 + 片段重写为 JUnit 5 `@DisplayName` 示例）
   - pom.xml 显式加 `spring-boot-starter-test`（兜底，不再依赖其他 starter-*-test 的传递）
   - 来源：[ADR-003 Cucumber 移除](../../../design/adr/ADR-003-remove-cucumber.md)
   - verify: `grep -r "Cucumber" docs/` 无命中（除了历史决策记录）；`mvn dependency:tree` 能看到 spring-boot-starter-test 显式依赖
8. [ ] #8 后端测试环境基础设施（本地真实环境 + CI service container）— developer-api (依赖: #3)
   - 本地开发：PostgreSQL 17（pgvector 扩展）+ Neo4j 5 Community，`aaf_test` 独立数据库
   - CI：GitHub Actions service container（PostgreSQL + Neo4j），配在 `.github/workflows/ci.yml` 的 acceptance 阶段
   - 测试配置：`apps/service/src/test/resources/application-test.yaml` 指向 localhost，CI 里用环境变量覆盖
   - 清理策略：`@Transactional`（Spring Test 自动回滚）+ Neo4j `@BeforeEach` 显式清库
   - Mock LLM：`@MockBean ChatClient`，CI 不消耗真实 API
   - 文档更新：
     - `docs/reference/dev/dev-environment.md` 补充"本地数据库"章节（PostgreSQL / Neo4j 安装 + pgvector + aaf_test 创建脚本）
   - 来源：[ADR-002 本地环境 vs Testcontainers](../../../design/adr/ADR-002-local-env-vs-testcontainers.md)

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->
<!-- 完成任务时标注负责人：✅ #1 任务描述 - {agent} -->

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入。

<!-- 格式同上，追加到任务列表 -->
