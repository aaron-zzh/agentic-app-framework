# 05 AutoDev（Git · CI/CD · 代码生成）

> 覆盖：`aaf-auto-dev` 的 Git 操作、CI/CD 集成、代码生成、文档服务。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B4 | 🔴 | `autodev/git/GitController` + `CiCdService` | `/webhook/github` 仍未校验 `X-Hub-Signature-256`，部署接口的 environment 仍缺少服务端白名单 | webhook 增加 HMAC 验签；部署环境使用服务端白名单并按环境分级授权 |
| B8 | 🔴 | `autodev/codegen/CodegenService#buildPath/writeFile` | 输出路径 `outputDir/.../module/{module}/{pkg}/{name}.java` 用 `def.module()`/`def.name()` 直接拼接，未校验→`module="../.."` 可路径穿越任意写文件 | 校验 module/name 为 `[a-zA-Z0-9_]+`；规范化后校验仍在 outputDir 内 |
| M7 | 🟠 | `autodev/git/CiCdService` | `HttpClient` 仍为 `static` 实例，与注入风格不一致；`queryLatestRunId` 用 `Thread.sleep(2000)` 阻塞 | 注入 `HttpClient`；轮询改回调/异步 |

## 良好实践

- `GitController` 的高危写操作与 CI/CD 触发已增加管理员角色鉴权。
- `GitService` 基于 JGit 而非 shell 调用，规避了命令注入；操作路径来自配置而非用户输入。
- `CodegenService` 基于 FreeMarker 模板生成四层代码，模型构建清晰（仅输出路径需加固）。

## 对称性提示

- 认证 vs 鉴权（清单#8）：高危运维端点已限制管理员，剩余风险是 webhook 缺少来源认证及部署环境未受白名单约束（B4）。

## 待确认

- `PullRequestService`、`AutodevDocService`/`AutodevDocImportService`（8KB+）未深读：文档导入是否有路径/大小限制、PR 创建是否带凭证泄露风险。
- `KiroAgentController`（9.8KB）未深读：对外暴露的 agent 控制接口鉴权与输入校验需补审。
