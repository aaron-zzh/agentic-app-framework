# 05 AutoDev（Git · CI/CD · 代码生成）

> 覆盖：`aaf-auto-dev` 的 Git 操作、CI/CD 集成、代码生成、文档服务。

## 问题清单（2026-08-01 复核）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| B4 | 🔴 | OPEN | `autodev/git/GitController` + `CiCdService` | `/webhook/github` 仍未校验 `X-Hub-Signature-256`，部署接口的 environment 仍缺少服务端白名单，未在本轮处理（由另一对话跟踪） |
| B8 | 🔴 | OPEN | `autodev/codegen/CodegenService#buildPath/writeFile` | 输出路径 `outputDir/.../module/{module}/{pkg}/{name}.java` 用 `def.module()`/`def.name()` 直接拼接，未校验→`module="../.."` 可路径穿越任意写文件，未在本轮处理（由另一对话跟踪） |
| M7 | 🟠 | FIXED | `autodev/git/CiCdService` | `HttpClient` 由 `static` 改为实例字段（注入风格，加 connectTimeout）；`queryLatestRunId` 改用注入的 `TaskScheduler` 延迟调度 + `HttpClient.sendAsync`，不再 `Thread.sleep` 阻塞调用线程；`GitController` 两端点改 `Callable<Result<Long>>` 返回类型，对外 HTTP 契约不变 |

## 良好实践

- `GitController` 的高危写操作与 CI/CD 触发已增加管理员角色鉴权。
- `GitService` 基于 JGit 而非 shell 调用，规避了命令注入；操作路径来自配置而非用户输入。
- `CodegenService` 基于 FreeMarker 模板生成四层代码，模型构建清晰（仅输出路径需加固）。

## 对称性提示

- 认证 vs 鉴权（清单#8）：高危运维端点已限制管理员，剩余风险是 webhook 缺少来源认证及部署环境未受白名单约束（B4）。

## 待确认

- `PullRequestService`、`AutodevDocService`/`AutodevDocImportService`（8KB+）未深读：文档导入是否有路径/大小限制、PR 创建是否带凭证泄露风险。
- `KiroAgentController`（9.8KB）未深读：对外暴露的 agent 控制接口鉴权与输入校验需补审。
