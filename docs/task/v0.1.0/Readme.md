# AAF v0.1.0 迭代

> 目标：项目基础框架搭建 + 协作基础设施 + 核心业务模块 MVP

## 一级用户故事

| 编号 | 名称 | 状态 | 依赖 |
|------|------|------|------|
| AAF-018 | 开源框架授权控制 | [ ] 待开始 | AAF-023 |
| AAF-019 | 文档管理系统 | [ ] 待开始 | AAF-023 |
| AAF-020 | 聊天协作界面 | [ ] 待开始 | AAF-023 |
| AAF-021 | Auto Dev 平台（AI 协作开发监控与管理） | [ ] 待开始 | AAF-023 |
| AAF-022 | 用户认证与访问控制 | [ ] 待开始 | AAF-023 |
| AAF-023 | 项目基础框架搭建 | ⏳ 进行中 | 无 |
| AAF-024 | 协作基础设施优化 | ⏳ 进行中 | AAF-023 |
| AAF-025 | 在线源码查看系统 | [ ] 待开始 | AAF-023, AAF-021 |
| AAF-026 | 对外文档站点（Fumadocs） | ✅ 已完成 | AAF-023 |

## 目录结构

每个用户故事一个文件夹，包含该故事的所有产出物：

```text
v0.1.0/
├── Readme.md              ← 本文件（迭代索引）
├── AAF-023/
│   ├── tasks.md           ← 技术任务列表
│   └── dev-log.md         ← 开发记录
```

## 相关文档

- [迭代任务计划](../aaf-v0.1.0.md)
- [Backlog](../backlog.md)
- [路线图](../../prd/roadmap.md)

## 框架内置能力引入规划（v0.1.0）

> 能力跟随业务需求引入。以下是 v0.1.0 各用户故事首次需要的框架能力映射。
> 完整能力清单见 [tech-stack.md §6](../../design/apps/service/tech-stack.md#六框架内置能力开箱即用)。

### AAF-023 项目基础框架搭建（#30 脚手架）

所有 Epic 的公共基础，无业务语义：

- `Result<T>` 统一响应封装 + 错误码体系 + `BusinessException`
- `BaseEntity`（id/createTime/updateTime/deleted/version）+ JPA 审计自动填充
- `PageRequest` / `PageResult<T>` 分页协议
- 全局异常处理（`@RestControllerAdvice` → 错误码 → `Result`）
- Jackson 全局配置 + CORS
- Spring Security JWT 签发/校验骨架
- `ActorContext` 占位接口

### AAF-022 用户认证与访问控制

权限体系核心，其他模块的鉴权基础：

- 四层权限模型：RBAC（Spring Security）+ ReBAC（Neo4j 图遍历）+ 记录规则（JPA 拦截器）+ ABAC 占位
- Actor 统一抽象（`UserPrincipal` / `AgentPrincipal` → `ActorAware`）
- 数据权限 `@DataScope`（组织隔离）
- 登录失败锁定（Redis 计数 + 临时封禁）
- 验证码（图片验证码，Redis 存储）
- 登录日志
- 数据脱敏 `@Sensitive`
- 菜单管理 + 部门管理

### AAF-019 文档管理系统

文档 CRUD + 检索 + 关系图谱：

- 文件存储接口（`FileStorage`，v0.1.0 仅 Local 实现）
- 语义检索策略（PostgreSQL FTS + PgVector 三层递进）
- 操作日志（`@OperationLog`）
- 事件总线（文档变更 → Neo4j 关系同步）

### AAF-020 聊天协作界面

AI 对话 + 实时推送：

- AI 对话封装（ChatClient 统一入口 + 记忆管理 + 流式 SSE）
- 消息推送（SSE 统一封装）
- 工具白名单（Agent Tool 安全调用）
- 链路追踪（TraceId + MDC，AI 调用链可追踪）

### AAF-021 Auto Dev 平台

Agent 代码生成 + 监控：

- 轻量任务队列（Agent 异步执行，PostgreSQL + Redis）
- 输出溯源 ID（traceId + agentId + modelId + toolChain）
- API Key 管理（kiro-cli 上报鉴权）
- 代码生成器（表结构 → CRUD）

### AAF-018 开源框架授权控制

License JWT 校验：

- 列加密（JPA AttributeConverter，授权信息安全存储）

### v0.2 ~ v0.5 延后的能力

| 版本 | 能力 | 触发场景 |
|------|------|---------|
| v0.2 | 幂等控制、分布式锁、定时任务、系统参数、字典管理、VO 数据翻译、DSL 解析封装、健康检查扩展、ABAC 策略引擎、实时交互授权、AI 数据安全、配额与限流 | 元引擎实体运行时 + Agent 安全 |
| v0.3 | AI 工作流编排、多语言国际化、短信服务 | 工作流运行时 |
| v0.4 | 规则引擎封装、热部署能力、第三方集成（微信/钉钉/飞书） | 权限运行时 + 外部生态 |
| v0.5 | 支付对接、情感记忆存储、IoT 设备接入 | 商业化 + 知识库增强 |
