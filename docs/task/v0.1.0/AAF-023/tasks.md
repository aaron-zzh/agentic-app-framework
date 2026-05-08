---
level: Practice
layer: Product
purpose: AAF-023 项目基础框架搭建的技术任务清单
status: active
version: 2.0.0
date: 2026-05-07
author: AaronZZH
changelog:
  - 2026-05-07 | 调整执行策略：先参考成熟项目建完整目录结构，再做功能验证；新增 UniApp 结构任务；格式化方案改为 Spotless
  - 2026-05-05 | 初始版本
---

# 项目基础框架搭建（AAF-023）

> 需求：项目基础框架搭建（详见 [aaf-v0.1.0.md 业务需求](../../aaf-v0.1.0.md#项目基础框架搭建)）
> 设计：[后端技术选型](../../../design/apps/service/tech-stack.md) | [后端模块结构](../../../design/apps/service/module-structure.md)
> 负责人：architect + developer-api + developer-web + developer-app | 创建：05-05

## 任务列表

> **执行策略**：先参考成熟项目完成技术特性规划与完整目录结构搭建，再结合 v0.1.0 目标进行功能验证。
> 参考项目：后端 → 芋道（ruoyi-vue-pro）+ JeecgBoot；前端 → Dify + xueji + ag-ui/ai-sdk；UniApp → kids-app

### Phase 1：参考调研 + 完整目录结构

1. [ ] #17 后端参考调研 + 完整目录结构搭建 — architect + developer-api
   - 参考芋道 `yudao-framework` 模块结构、JeecgBoot 模块拆分方式
   - 对照 [module-structure.md](../../../design/apps/service/module-structure.md) 创建完整包目录（含占位 `package-info.java`）
   - Maven 多模块拆分：aaf-dependencies / aaf-common / aaf-framework / aaf-auto-dev / aaf-api
   - 参考芋道 BOM 写法完善 `aaf-dependencies/pom.xml`
   - 更新 [docs/design/apps/service/tech-stack.md](../../../design/apps/service/tech-stack.md)（补充参考项目对比、最终选型确认）
   - verify: `pnpm nx run service:build` 成功，五个模块独立编译通过

2. [ ] #18 前端参考调研 + 完整目录结构搭建 — architect + developer-web
   - 参考 Dify 前端目录结构、xueji 项目、ag-ui/ai-sdk 集成方式
   - 完善 `apps/webui/` 目录结构（app router 分层、组件库、hooks、store、types）
   - 配置 TanStack Query + Zustand + ai-sdk 基础依赖
   - 创建 [docs/design/apps/webui/tech-stack.md](../../../design/apps/webui/tech-stack.md)（技术选型 + 参考项目对比）
   - verify: `pnpm nx run webui:dev` 启动成功，目录结构符合规范

3. [ ] #19 UniApp 参考调研 + 完整目录结构搭建 — architect + developer-app
   - 参考 kids-app 项目结构
   - 完善 `apps/uniapp/` 目录结构（pages、components、store、api、utils）
   - 创建 [docs/design/apps/uniapp/tech-stack.md](../../../design/apps/uniapp/tech-stack.md)（技术选型 + 参考项目对比）
   - verify: 目录结构创建完成，`package.json` 依赖配置就绪

### Phase 2：基础设施

4. [ ] #20 Flyway 数据库迁移初始化 + 环境配置分离 — developer-api (依赖: #17)
   - 初始表：sys_user、doc_document、autodev_request、autodev_generated_code、autodev_execution_log
   - application-dev/prod/test.yaml 环境隔离
   - verify: `pnpm nx run service:serve` 启动后数据库自动建表

5. [ ] #21 ArchUnit 分层规则激活 — developer-api (依赖: #17)
   - 替换 LayeringTest.java 占位，启用 5 条真实分层规则
   - verify: `pnpm nx run service:test` 运行 LayeringTest，违规用例即时报错

6. [ ] #22 前端测试栈对齐（Vitest + Playwright） — developer-web (依赖: #18)
   - 新增 Playwright 作为 E2E / 验收（取代 `vitest.acceptance.config.ts` 占位）
   - 来源：[ADR-001 Vitest vs Jest](../../../design/adr/ADR-001-vitest-vs-jest.md)
   - verify: `pnpm nx run webui:test` Vitest 跑通；`pnpm nx run webui-e2e:e2e` Playwright 跑通

7. [ ] #23 后端测试规范清理（去 Cucumber + 显式测试依赖）— developer-api
   - 去除规范文档里的 Cucumber 引用，pom.xml 显式加 `spring-boot-starter-test`
   - 来源：[ADR-003 Cucumber 移除](../../../design/adr/ADR-003-remove-cucumber.md)
   - verify: `grep -r "Cucumber" docs/` 无命中（除历史决策记录）

8. [ ] #24 后端测试环境基础设施（本地 + CI）— developer-api (依赖: #20)
   - 本地：PostgreSQL 17（pgvector）+ Neo4j 5，`aaf_test` 独立数据库
   - CI：GitHub Actions service container（PostgreSQL + Neo4j）
   - 来源：[ADR-002 本地环境 vs Testcontainers](../../../design/adr/ADR-002-local-env-vs-testcontainers.md)

### Phase 3：工程化

9. [ ] #25 统一格式化方案（前端 ESLint+Prettier / 后端 Spotless）— developer-api + developer-web
   - 后端：`spotless-maven-plugin` + Google Java Format，`pnpm nx run service:format`
   - 前端：ESLint 整合 Prettier，IDEA 保存时自动触发
   - verify: `pnpm format:check` 全绿，IDEA 保存自动格式化

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->
<!-- 完成任务时标注负责人：✅ #N 任务描述 - {agent} -->

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入。

<!-- 格式同上，追加到任务列表 -->

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 05-05 | ✅ CLEAR | 🔴 是 |
| architect（技术设计） | 1 | 05-05 | ✅ CLEAR | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
