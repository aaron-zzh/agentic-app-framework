---
level: Practice
layer: Product
purpose: AAF-023 项目基础框架搭建的技术任务清单
status: active
version: 3.0.0
date: 2026-05-09
author: AaronZZH
changelog:
  - 2026-05-09 | 按执行顺序重排任务，去除 Phase 分组改为后端主线/前端并行线；新增 #30 框架基础能力脚手架；#25 拆分前后端
  - 2026-05-08 | 追加 Phase 4：tech-stack.md v2.0 定稿后的工程化细化（#26-#29 pom 清理/BOM 完善/common 轻量化/包结构约定）
  - 2026-05-07 | 调整执行策略：先参考成熟项目建完整目录结构，再做功能验证；新增 UniApp 结构任务；格式化方案改为 Spotless
  - 2026-05-05 | 初始版本
---

# 项目基础框架搭建（AAF-023）

> 需求：项目基础框架搭建（详见 [aaf-v0.1.0.md 业务需求](../../aaf-v0.1.0.md#项目基础框架搭建)）
> 设计：[后端技术选型](../../../design/apps/service/tech-stack.md) | [后端模块结构](../../../design/apps/service/module-structure.md)
> 负责人：architect + developer-api + developer-web + developer-app | 创建：05-05

## 任务列表

> **执行策略**：按依赖链线性推进，后端主线 → 前端并行线。
> 参考项目：后端 → 芋道（ruoyi-vue-pro）+ JeecgBoot；前端 → Dify + xueji + ag-ui/ai-sdk；UniApp → kids-app

### 后端主线（按执行顺序）

1. ✅ #17 Maven 多模块拆分 + 完整包目录 — architect + developer-api
   - 参考芋道 `yudao-framework` 模块结构、JeecgBoot 模块拆分方式
   - 拆分为：aaf-dependencies / aaf-common / aaf-framework / aaf-auto-dev / aaf-api
   - 对照 [module-structure.md](../../../design/apps/service/module-structure.md) 创建完整包目录（含占位 `package-info.java`）
   - verify: `pnpm nx run service:build` 成功，五个模块独立编译通过

2. ✅ #26 根 pom 清理与 Preview 特性启用 — developer-api (依赖: #17)
   - 移除 WebFlux / R2DBC 全栈依赖（仅保留 SSE / `Flux<ChatResponse>` 所需部分）
   - 统一 `annotationProcessorPaths`：Lombok + MapStruct + `spring-boot-configuration-processor`
   - 三处同步开启 `--enable-preview`：compiler / surefire / spring-boot plugin
   - verify: `pnpm nx run service:build` 成功；preview 特性示例编译通过

3. ✅ #27 aaf-dependencies BOM 完善 — developer-api (依赖: #17)
   - 参考 ruoyi-vue-pro `yudao-bom/pom.xml` 结构
   - 按 [tech-stack.md §3](../../../design/apps/service/tech-stack.md#三核心依赖清单) 分组管理版本
   - verify: 子模块 pom 无显式版本号散落

4. ✅ #28 aaf-common 依赖轻量化 — developer-api (依赖: #27)
   - Spring Web / Validation / Jackson / Jakarta Servlet 使用 `<scope>provided</scope>`
   - verify: 临时模块仅依赖 aaf-common（不引 starter-web）可编译通过

5. [ ] #23 测试规范清理（去 Cucumber） — developer-api
   - 去除规范文档里的 Cucumber 引用，pom 显式加 `spring-boot-starter-test`
   - 来源：[ADR-003](../../../design/adr/ADR-003-remove-cucumber.md)
   - verify: `grep -r "Cucumber" docs/` 无命中（除 ADR 历史记录）

6. [ ] #21 ArchUnit 分层规则激活 — developer-api (依赖: #17)
   - 替换 LayeringTest.java 占位，启用 5 条真实分层规则
   - verify: `pnpm nx run service:test` 运行 LayeringTest，违规即报错

7. [ ] #25 统一格式化方案（后端 Spotless） — developer-api (依赖: #17)
   - `spotless-maven-plugin` + Google Java Format
   - verify: `pnpm nx run service:format` + `pnpm format:check` 全绿

8. [ ] #30 框架基础能力脚手架 — developer-api (依赖: #27, #28)
   - **aaf-common**：`Result<T>` / 错误码体系 / `BaseEntity` / 分页封装
   - **aaf-api/config**：全局异常处理 / Jackson 配置 / CORS
   - **aaf-framework**：Spring Security JWT 骨架 + `ActorContext` 占位
   - verify: `GET /api/hello` 返回 `Result<String>`；JWT 认证通过；无 Token 返回 401

9. [ ] #20 Flyway 数据库迁移初始化 + 环境配置分离 — developer-api (依赖: #30)
   - 初始表：sys_user、doc_document、autodev_request、autodev_generated_code、autodev_execution_log
   - application-dev/prod/test.yaml 环境隔离
   - verify: `pnpm nx run service:serve` 启动后数据库自动建表

10. [ ] #24 测试环境基础设施（本地 + CI） — developer-api (依赖: #20)
    - 本地：PostgreSQL 17（pgvector）+ Neo4j 5，`aaf_test` 独立数据库
    - CI：GitHub Actions service container
    - 来源：[ADR-002](../../../design/adr/ADR-002-local-env-vs-testcontainers.md)

11. [ ] #29 包结构约定与 ArchUnit 规则扩展 — developer-api + architect (依赖: #21, #30)
    - `aaf-framework` 新建 `protection` 包（限流/幂等/分布式锁聚合）
    - 业务模块 `api/` 子包暴露跨模块接口 + DTO
    - 扩展 ArchUnit：跨模块只允许访问 `api` 包；防护注解必须在 `protection` 包下
    - verify: LayeringTest 新增用例全绿；违规代码即时报错

### 前端并行线（不阻塞后端）

12. [ ] #18 前端参考调研 + 完整目录结构 — architect + developer-web
    - 参考 Dify、xueji、ag-ui/ai-sdk
    - 完善 `apps/webui/` 目录结构 + 基础依赖（TanStack Query + Zustand + ai-sdk）
    - 创建 [docs/design/apps/webui/tech-stack.md](../../../design/apps/webui/tech-stack.md)
    - verify: `pnpm nx run webui:dev` 启动成功

13. [ ] #22 前端测试栈对齐（Vitest + Playwright） — developer-web (依赖: #18)
    - 来源：[ADR-001](../../../design/adr/ADR-001-vitest-vs-jest.md)
    - verify: `pnpm nx run webui:test` + `pnpm nx run webui-e2e:e2e` 跑通

14. [ ] #25b 前端格式化（ESLint + Prettier） — developer-web (依赖: #18)
    - ESLint 整合 Prettier，IDEA 保存时自动触发
    - verify: `pnpm format:check` 全绿

15. [ ] #19 UniApp 参考调研 + 完整目录结构 — architect + developer-app
    - 参考 kids-app 项目结构
    - 创建 [docs/design/apps/uniapp/tech-stack.md](../../../design/apps/uniapp/tech-stack.md)
    - verify: 目录结构创建完成，`package.json` 就绪

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
