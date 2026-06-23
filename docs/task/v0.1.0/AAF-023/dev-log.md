# 开发日志：AAF-023 项目基础框架搭建

> 产出目录：`docs/task/v0.1.0/AAF-023/`
> 任务编号见 [tasks.md](tasks.md)

---

## #1 一键 check 基础设施与分工文档化

✅ 05-05 — 协调者（23 文件）

- 建 check/acceptance 命令体系，CI 改三阶段门禁
- 决策：developer 管 check（技术），tester 管 acceptance（需求）
- 测试命名硬约束落地：*Test→Surefire / *IT→Failsafe
- ArchUnit 占位，待 #17 后激活

> **沉淀**：命名即归属——Surefire includes/excludes 配置即分工边界，无需额外机制

## #2 Vitest vs Jest 决策

✅ 05-05 — 协调者 → [ADR-001](../../../design/adr/ADR-001-vitest-vs-jest.md)

- 选 Vitest：ESM 原生、Nx 默认、冷启动快
- 验收测试改由 Playwright 接管（登记 #22）

## #3 测试环境方案决策

✅ 05-05 — 协调者 → [ADR-002](../../../design/adr/ADR-002-local-env-vs-testcontainers.md)

- 选本地真实环境 + CI service container，弃 Testcontainers
- 理由：一人公司，0 秒启动 × N 次内循环优于容器延迟

## #4 Cucumber 移除决策

✅ 05-05 — 协调者 → [ADR-003](../../../design/adr/ADR-003-remove-cucumber.md)

- .feature 与"文档唯一真理"冲突，移除
- 保留 Gherkin 作 AC 格式，用 @DisplayName 映射

## #17 Maven 多模块拆分

✅ 05-07 — developer-service

- 拆为 dependencies/common/framework/auto-dev/api 五模块
- 建完整包目录，含占位 package-info.java
- 五模块独立编译通过

## #26 根 pom 清理与 Preview 启用

✅ 05-07 — developer-service

- 移除 WebFlux/R2DBC 全栈依赖，仅留 SSE 部分
- 三处同步开启 --enable-preview

## #27 aaf-dependencies BOM 完善

✅ 05-07 — developer-service

- 按 tech-stack.md 分组管理版本，子模块无散落版本号

## #28 aaf-common 依赖轻量化

✅ 05-07 — developer-service

- Spring Web/Validation/Jackson 改 provided scope
- 决策：避免 common 污染下游依赖树

## #23 测试规范清理（去 Cucumber）

✅ 05-08 — developer-service

- 去除文档 6 处 Cucumber 引用，pom 加 starter-test

## #21 ArchUnit 分层规则激活

✅ 05-08 — developer-service

- 启用 5 条真实分层规则，违规即报错

## #25 统一格式化（后端 Spotless）

✅ 05-08 — developer-service

- spotless-maven-plugin + Google Java Format
- format:check 全绿

## #30 框架基础能力脚手架

✅ 05-09 — developer-service

- common：Result<T> / 错误码 / BaseEntity / 分页
- api/config：全局异常 / Jackson / CORS
- framework：Security JWT 骨架 + OperatorContext
- 验证：GET /api/hello 通过，无 Token 返回 401

## #20 Flyway 迁移 + 环境分离

✅ 05-09 — developer-service

- 初始表五张（sys_user/doc_document/autodev_*）
- dev/prod/test 三套 yaml 隔离

## #31 用户管理 CRUD

✅ 05-10 — developer-service

- Entity→Repository→Service→Controller 完整链路
- Result<T> + PageResult<T> + SpecificationBuilder 条件查询
- OpenAPI 注解 + 批量删除 + 状态修改

## #24 CI 基础设施（GitHub Actions）

✅ 05-10 — developer-service

- service container：PostgreSQL + Redis
- workflow 跑 check:affected，PR 触发全绿

## #29 包结构约定与 ArchUnit 扩展

✅ 05-10 — developer-service + architect

- framework 新建 protection 包（限流/幂等/锁）
- 业务模块 api/ 子包暴露跨模块接口
- ArchUnit：跨模块只允许访问 api 包（占位）

## #18 前端参考调研 + 目录结构

⏳ 进行中 — architect + developer-webui

- ✅ 20 个参考项目分析 + 12 个目录结构对比
- ✅ 前端目录结构设计（两种交互模式 + packages/ 规划）
- ✅ 结构化视图 + 生成式交互两种模式设计
- ✅ CopilotKit Nx monorepo 工程化分析
- 待：创建目录 + 装依赖 + 验证 dev 启动

---

<!-- 后续任务追加到下方 -->

## 智能层代码-文档对齐审查

✅ 05-29 — developer-service

- 对比 6 份技术方案文档与实际代码，更新 4 份文档匹配代码实现
- 关键差异：AgentRuntime 接口抽象（代码更好）、Team 层规范容器设计（代码更好）、Session 适配器为静态工厂（代码更好）
- 文档已更新：agent-tech.md / assistant-tech.md / team-tech.md / retrieval-tech.md / agentscope-integration.md

> **待实现清单**（文档设计有但代码未实现）：
> - `AafLongTermMemory`（实现 AgentScope LongTermMemory 接口，委托 AAF 记忆管道）
> - `AafKnowledge`（实现 AgentScope Knowledge 接口，委托 HybridSearchService）
> - `AafToolWhitelistHook`（PreActingEvent 工具白名单，当前由 ToolPermissionGuard 实现但未接入 Hook 体系）
> - `AafTraceHook`（PostCallEvent 输出溯源）
> - `InputBuffer`（Assistant 输入缓冲区）
> - `TaskBoard`（Assistant 任务看板）
> - `GoalTracker`（Team 目标级任务管理，v0.6+）
> - AgentScope `agentscope-agui-spring-boot-starter` 引入（替换 AguiAdapter 骨架）
> - AgentScope `agentscope-a2a-spring-boot-starter` 引入（补全远程 A2A 调用）

> **沉淀**：代码引入了 `AgentRuntime` 接口层，比文档原设计（AgentFactory 直接构建 ReActAgent）更好——实现了框架无关抽象，切换底层 Agent 框架只需替换 Bean。文档已同步更新。


## D 步骤：ai_llm_call_log + ai_tool_call_log token 粒度持久化

✅ 2026-06-22 — developer-service

- 新增 `CallLogMiddleware`（onModelCall 拦截 `ModelCallEndEvent` → `ai_llm_call_log`；onActing 写工具入参 → `ai_tool_call_log`，`ToolCallEndEvent` 更新 status=COMPLETED）
- 新增 `AiLlmCallLog` / `AiToolCallLog` JPA entity + Repository（`module.chat.calllog`）
- Flyway `v11__call_log_schema.sql`：两表 + 索引（conv/thread/time）
- `ContentCreationAgentFactory.registerInfrastructure` 注册 `CallLogMiddleware`（与 JdbcTemplate 共用）
- `pnpm nx build service` BUILD SUCCESS（46.9s，6 模块全绿）
