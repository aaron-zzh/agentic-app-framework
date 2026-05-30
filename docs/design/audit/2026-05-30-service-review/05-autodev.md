# 05 AutoDev（Git · CI/CD · 代码生成）

> 覆盖：`aaf-auto-dev` 的 Git 操作、CI/CD 集成、代码生成、文档服务。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B4 | 🔴 | `autodev/git/GitController` | `/api/autodev/git/**` 仅需登录无角色鉴权，任意用户可 commit/push/建 PR/**触发 CI**/**触发任意环境部署**；`/webhook/github` 无 `X-Hub-Signature-256` 验签 | 全端点加管理员鉴权；webhook 加 HMAC 验签；部署环境白名单+权限分级 |
| B8 | 🔴 | `autodev/codegen/CodegenService#buildPath/writeFile` | 输出路径 `outputDir/.../module/{module}/{pkg}/{name}.java` 用 `def.module()`/`def.name()` 直接拼接，未校验→`module="../.."` 可路径穿越任意写文件 | 校验 module/name 为 `[a-zA-Z0-9_]+`；规范化后校验仍在 outputDir 内 |
| M7 | 🟠 | `autodev/git/CiCdService` | `buildCache`（ConcurrentHashMap）只增不删→内存泄漏；`triggerWorkflow` 用 String.format 拼 JSON（ref 含引号即注入/破坏）；`static HttpClient/ObjectMapper` 与注入风格不一致；`queryLatestRunId` 用 `Thread.sleep(2000)` 阻塞 | 缓存改有界(TTL/LRU)；JSON 用 ObjectMapper；统一注入；轮询改回调/异步 |

## 良好实践

- `GitService` 基于 JGit 而非 shell 调用，规避了命令注入；操作路径来自配置而非用户输入。
- `CodegenService` 基于 FreeMarker 模板生成四层代码，模型构建清晰（仅输出路径需加固）。

## 对称性提示

- 创建 vs 删除（清单#2）：`CiCdService.buildCache` 写入无淘汰（M7）。
- 认证 vs 鉴权（清单#8）：autodev 全模块缺角色级鉴权（B4），且这些是高危运维操作。

## 待确认

- `PullRequestService`、`AutodevDocService`/`AutodevDocImportService`（8KB+）未深读：文档导入是否有路径/大小限制、PR 创建是否带凭证泄露风险。
- `KiroAgentController`（9.8KB）未深读：对外暴露的 agent 控制接口鉴权与输入校验需补审。
