# 后端 service 详细代码审查（分区文档）

> 这是对 [2026-05-30 抽样审查](../2026-05-30-service-code-review.md) 的加深版，按区域记录审查与复核结果。
> 审查依据：[代码审查规范](../../../reference/dev/code-review-standard.md)、[架构约束](../../../reference/dev/architecture-constraints.md)、[编码规范硬约束](../../../../.kiro/skills/coding-standards/SKILL.md)。
> 当前快照已汇总四阶段复核结果；下表仅保留 **OPEN** 与 **PARTIAL**，已确认 **FULL_FIXED** 的问题不再列入当前问题总表。

## 元信息

| 字段 | 值 |
|------|-----|
| 审查人 | AI/architect |
| 初始审查日期 | 2026-05-30 |
| 汇总更新日期 | 2026-07-31 |
| 范围 | apps/service 全部 4 模块（深度抽样，非逐行全覆盖） |
| 当前结论 | 不通过：仍有 OPEN/PARTIAL blocker |

## 分区文档

| 文档 | 覆盖范围 |
|------|---------|
| [01-security-and-authz.md](01-security-and-authz.md) | 租户隔离、鉴权、Mock Token、API Key、JWT、AuthService、企微回调 |
| [03-channel-livechat.md](03-channel-livechat.md) | 渠道路由/配置、Webhook、客服会话、坐席分配 |
| [04-ai-engines-and-tools.md](04-ai-engines-and-tools.md) | 工具权限守卫、脚本沙箱、价值规则、占位引擎、知识库 |
| [05-autodev.md](05-autodev.md) | Git、CI/CD、代码生成、文档服务 |
| [06-architecture-and-quality.md](06-architecture-and-quality.md) | 分层/实体外泄、重复抽象、命名/包结构、占位实现、通用工具 |
| [07-system-admin-and-rbac.md](07-system-admin-and-rbac.md) | 用户/角色/权限点/行级数据权限，以及当前仍需收敛的角色、SELF 与 org 授权边界 |
| [08-ai-chat-tools-company-stats.md](08-ai-chat-tools-company-stats.md) | 对话/流式、持久任务、企业运营编排、行为统计、Prompt 引擎 |
| [09-file-sms-aigc.md](09-file-sms-aigc.md) | 文件上传/下载、短信模板与发送、AIGC 图像/媒资生成 |
| [10-authorization-matrix.md](10-authorization-matrix.md) | Controller 鉴权冻结基线与剩余资源级授权矩阵；不再沿用旧数量统计 |
| [11d-framework-controllers.md](11d-framework-controllers.md) | framework REST 暴露面；当前残留 UEL value、工作流授权/Webhook 与 SSRF 风险 |
| [11e-framework-data-ai.md](11e-framework-data-ai.md) | 数据处理/AI/知识库；当前残留提示词注入、知识库检索旁路与抓取 SSRF |
| [11f-framework-infra.md](11f-framework-infra.md) | 基础设施；当前残留消费幂等、多实例缓存、审计脱敏与锁语义问题 |
| [11g-framework-orchestration-api-remainder.md](11g-framework-orchestration-api-remainder.md) | 工作流/编排/AI 能力与 API 收官复审；当前残留 framework 配额旁路与语义提示注入 |
| [14-remaining-tasks-handoff.md](14-remaining-tasks-handoff.md) | 当前交接：历史完成记录 + OPEN/PARTIAL 剩余任务；新对话从这里接续 |

## 状态口径

| 状态 | 含义 |
|------|------|
| OPEN | 分区复核确认问题仍完整存在 |
| PARTIAL | 主路径或部分入口已修复，但仍有明确残余风险 |
| FULL_FIXED | 已确认完整修复；不进入当前问题总表，仅在历史记录中保留 |

## 当前问题总表

### Blocker

| 编号 | 状态 | 区域 | 当前残余 |
|------|------|------|---------|
| B1 | PARTIAL | 01 | framework 非标准仓储仍未统一纳入租户过滤，workspace 行级过滤未统一 |
| B4 | PARTIAL | 05 | GitHub webhook 仍无 HMAC 验签，部署 environment 无服务端白名单 |
| B5 | OPEN | 04 | `ScriptSandbox` 仍以裸子进程/关键词黑名单执行脚本，与受限 GraalVM 路径并存 |
| B7 | OPEN | 03 | Channel/Webhook 配置实体携带敏感凭证并经接口返回 |
| B8 | OPEN | 05 | Codegen 输出路径仍信任 module/name，存在路径穿越任意写文件风险 |
| B9 | PARTIAL | 07/10 | 角色仍偏宽，SELF 资源归属与 org 边界尚未清零 |
| B10 | PARTIAL | 07/10 | `viewSource` 与工具列表仍缺 owner/org/share scope 资源级授权 |
| B-mock | OPEN（条件） | 01 | Mock Token 仍依赖配置关闭，缺少非生产 Profile/构建级隔离 |

### Major

| 编号 | 状态 | 区域 | 当前残余 |
|------|------|------|---------|
| M1 | PARTIAL | 01 | 当前用户接口仍暴露 userId，并在认证上下文缺失时 fallback |
| M6 | PARTIAL | 03/06 | Channel/Webhook service 仍返回 Entity |
| M7 | OPEN | 05 | CI/CD 使用静态 HttpClient，轮询以 `Thread.sleep` 阻塞 |
| M9 | PARTIAL | 01 | API Key 认证未继承关联用户真实角色 |
| M15 | PARTIAL | 07/08/11g | Company 与 Channel/Webhook 写入口已 DTO 化，Company 出参已 VO 化；SMS、AI Output、Document、Team 等仍有实体出参 |
| M17 | OPEN | 07 | 用户角色与角色权限分配语义不对称 |
| M21 | PARTIAL | 09 | 生产短信测试号码仍缺白名单和环境隔离 |
| M22 | PARTIAL | 09 | Controller 仍直连 Repository，并返回 Entity |
| M23 | PARTIAL | 09 | image-to-image/edit 仍旁路统一权益 precheck、扣减与补偿 |
| M42 | OPEN | 11e | AiEnricher 仍将外部数据原文拼入 LLM 提示词 |
| M45 | PARTIAL | 11e | 危险两参检索重载当前无生产调用，但仍可在未来绕过 kbId 过滤 |
| M49 | PARTIAL | 11f | 副作用完成后、ACK 前崩溃仍可能重复执行 |
| M50 | OPEN | 11f | TwoLevelCache 失效仅本机，多实例缓存可能陈旧 |
| M51 | OPEN | 11f | OperationLogAspect 原样记录参数/响应，缺少敏感数据脱敏 |
| M53 | PARTIAL | 11g | registry/streaming/embedding 等 framework 路径未统一门控 |
| 重复2 | OPEN | 07/14 | permission 与 role 两套 PermissionService/Controller 职责仍重叠 |
| 占位 | OPEN | 04/06 | engine 多个未进入实现阶段的空接口仍存在 |

### Minor 与结构性改进

| 编号/主题 | 状态 | 区域 | 当前残余 |
|-----------|------|------|---------|
| m1 | OPEN | 03 | Repository 查询条件与全局软删除限制重复 |
| m7 | OPEN | 01 | ServletUtils 盲信代理头 |
| m8 | OPEN | 01 | API Key last_used_at 每请求同步写入 |
| m9 | PARTIAL | 03 | 客服回调虚拟线程 executor 仍无背压 |
| m10 | OPEN | 01/全局 | 部分签名比较仍使用非常量时间比较 |
| m36 | OPEN | 04 | 内容安全依赖硬编码关键词黑名单 |
| m25 | OPEN | 11d | CodeExecutionNode 仍通过 `node -e` 子进程执行 JS |
| m29 | OPEN | 11e | ResilientChatService 对所有异常统一 fallback，可能双倍计费并掩盖错误 |
| m30 | OPEN | 11f | TwoLevelCache.invalidateAll 使用 Redis KEYS |
| m32 | PARTIAL | 11f | 分布式锁获取失败仍返回 null；当前暂无 `@DistributedLock` 使用点，属潜在语义风险 |
| 包结构 | OPEN | 06 | 业务模块内分层结构仍不一致 |
| 示例 | OPEN | 06 | 示例代码仍混入主 API 构建 |
| 兼容 | OPEN | 06 | OperatorContext 别名和 ToolPermissionGuard 重载仍形成兼容路径 |
| 异常 | OPEN | 06 | 全局异常处理对 401/403 与约束信息脱敏仍不完整 |
| 并行抽象 | OPEN | 04/06 | 脚本执行与多租户机制仍存在双轨抽象 |

## 系统性剩余主题

- **租户与资源授权**：B1、B9、B10、M9、M37、M45。当前核心不再是“普遍无注解”，而是角色过宽、SELF 归属、org 强制过滤和资源 scope 未闭合（08 区会话归属 M18、统计 org 过滤 M19 已闭环）。
- **敏感数据与实体边界**：B7、M6、M15、M22、M51。统一以 DTO/VO、字段脱敏和日志脱敏收敛。
- **脚本、表达式与外部输入**：B5、B17、M39、M42、M46、m25。仍需统一受限执行、SSRF 防护和语义输入边界。
- **资金、权益与成本控制**：M23、M25–M27、M53。重点是统一 pre-call 门控和真实对账（充值服务端定价、入账幂等与权益并发控制已闭环）。
- **回调与外部信任边界**：B4、B-mock、M21、M28、M31、M37。回调 HMAC、防重放、环境隔离和 OAuth 强制原语仍需闭环。
- **分布式正确性**：M26、M36、M49–M50、m32。重点是稳定幂等键、ACK 窗口、新旧状态机和跨节点失效。
- **占位与重复抽象**：M27、重复2、占位、并行抽象。只保留真实用例需要的单一路径。

## 交接摘要

- 前四阶段已确认 FULL_FIXED 的问题已从当前问题总表和系统主题移除；详细修复历史保留在各分区文档与 [14 交接单](14-remaining-tasks-handoff.md)。
- 02 区（积分/支付/充值/权益/订阅/对账）的 B2、M3、M4 已修复，分区文档已删除，修复记录与残余待确认项并入 [14 交接单](14-remaining-tasks-handoff.md)。
- M16 已完成：用户密码、OAuth token、Channel/Webhook 密钥、SMTP 密码、模型及供应商 API Key 均增加 `@JsonIgnore`。
- 当前最高优先级是剩余 blocker：租户/授权边界、脚本沙箱、敏感配置外泄、代码生成路径、存储多入口校验、UEL value 与 Mock 环境隔离。
- Controller 鉴权不再使用旧数量基线或“几乎全部无方法级鉴权”的旧结论；现行判断以 [10 鉴权矩阵](10-authorization-matrix.md) 的冻结基线及剩余资源级风险为准。
- 12/13 是历史设计与开发日志，本轮不修改；后续工作仅更新当前实现、测试产出与 14 交接状态。
- 门控仍为**不通过**：存在 OPEN/PARTIAL blocker。解除门控前需逐项完成代码验证、单测与 `pnpm check:affected`；本轮仅汇总文档，未执行这些命令。

## 覆盖与边界

已完成 01–11g 的分区深审，覆盖 service 四模块的安全敏感主路径、framework 结算/存储/认证/智能核心/REST/数据 AI/基础设施/编排，以及 API Controller 鉴权矩阵。

剩余未逐文件读取区域主要是编排细节与 provider 适配，可随修复迭代抽查：

- framework：`intelligent/cognition`、`engine/{memory,checkpoint,budget,metadata,monitor,meta}`、`intelligent/agent/agentscope`，以及 embedding/rerank/speech/omni/music/model3d 的具体 provider。
- API：按 [10 鉴权矩阵](10-authorization-matrix.md) 继续验证角色、SELF、org 和资源 scope，不再以 Controller 总数代替风险判断。
- 配置与启动：历史 Flyway/迁移现场及待验证项见 [14 交接单](14-remaining-tasks-handoff.md)。
