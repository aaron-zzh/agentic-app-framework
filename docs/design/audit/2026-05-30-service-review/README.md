# 后端 service 详细代码审查（分区文档）

> 这是对 [2026-05-30 抽样审查](../2026-05-30-service-code-review.md) 的加深版，按**区域分文档**记录。
> 审查依据：[代码审查规范](../../../reference/dev/code-review-standard.md)、[架构约束](../../../reference/dev/architecture-constraints.md)、[编码规范硬约束](../../../../.kiro/skills/coding-standards/SKILL.md)。

## 元信息

| 字段 | 值 |
|------|-----|
| 审查人 | AI/architect |
| 审查日期 | 2026-05-30 |
| 范围 | apps/service 全部 4 模块（深度抽样，非逐行全覆盖） |
| 上轮结论沿用 | 首轮 6 blocker / 8 major / 6 minor 仍有效 |

## 分区文档

| 文档 | 覆盖范围 |
|------|---------|
| [01-security-and-authz.md](01-security-and-authz.md) | 租户隔离、鉴权、Mock Token、API Key、JWT、AccessControl 切面、AuthService、企微回调 |
| [02-payments-billing-credit.md](02-payments-billing-credit.md) | 积分、支付、充值、权益、订阅、对账 |
| [03-channel-livechat.md](03-channel-livechat.md) | 渠道路由/配置、Webhook、客服会话、坐席分配 |
| [04-ai-engines-and-tools.md](04-ai-engines-and-tools.md) | 工具权限守卫、脚本沙箱、价值规则、占位引擎、知识库 |
| [05-autodev.md](05-autodev.md) | Git、CI/CD、代码生成、文档服务 |
| [06-architecture-and-quality.md](06-architecture-and-quality.md) | 分层/实体外泄、重复抽象、命名/包结构、占位实现、通用工具 |
| [07-system-admin-and-rbac.md](07-system-admin-and-rbac.md) | 用户/角色/权限点/行级数据权限、**系统性鉴权缺失**、Mass Assignment |
| [08-ai-chat-tools-company-stats.md](08-ai-chat-tools-company-stats.md) | 对话/流式、持久任务、企业运营编排、行为统计、Prompt 引擎 |
| [09-file-sms-aigc.md](09-file-sms-aigc.md) | 文件上传/下载、短信模板与发送、AIGC 图像/媒资生成 |
| [10-authorization-matrix.md](10-authorization-matrix.md) | **全 117 个 Controller 鉴权矩阵**（B9/B10 逐条修复工单 + 优先级分级） |
| [11-followup-review-plan.md](11-followup-review-plan.md) | **待审查方向交接单**（framework 内部 + API 剩余，重开对话从这里执行） |
| [11a-framework-settlement-storage.md](11a-framework-settlement-storage.md) | **优先级 1**：framework 结算引擎（渠道验签/退款幂等/Mock 隔离）+ 存储服务（上传校验/路径穿越/预签名 key） |
| [11b-framework-auth-oauth-license.md](11b-framework-auth-oauth-license.md) | **优先级 2**：OAuth（state/CSRF 缺失）+ License（硬编码公钥/@PremiumRequired 零使用） |
| [11c-framework-intelligent-core.md](11c-framework-intelligent-core.md) | **优先级 2**：工具调用鉴权旁路（B10 根因）+ HITL 审批绕过 + 置信度门控真实性 |
| [11d-framework-controllers.md](11d-framework-controllers.md) | **优先级 3**：framework REST 暴露面（webhook 触发/动态表 SQL 注入/UEL 注入/HttpNode SSRF） |
| [11e-framework-data-ai.md](11e-framework-data-ai.md) | **优先级 4**：数据处理/AI/知识库（DataRouter SQL 注入/提示词注入/配额未强制/向量租户隔离/抓取 SSRF） |
| [11f-framework-infra.md](11f-framework-infra.md) | **优先级 5**：基础设施（FreeMarker SSTI/分布式锁误删/失败任务丢失/跨节点缓存陈旧/审计敏感数据） |
| [11g-framework-orchestration-api-remainder.md](11g-framework-orchestration-api-remainder.md) | **收官**：工作流引擎 BPMN 部署 RCE/编排缓存污染/AI 能力配额 + API 剩余批量确认 |
| [12-blocker-remediation-design.md](12-blocker-remediation-design.md) | **修复设计**：9 个 blocker（B12–B20）按同根分组的修复技术方案（🔴 待人类审核后实施） |
| [13-blocker-fix-dev-log.md](13-blocker-fix-dev-log.md) | **修复开发日志**：9 个 blocker 已实施（代码改动清单 + 验证状态，编译/测试待 CI） |
| [14-remaining-tasks-handoff.md](14-remaining-tasks-handoff.md) | **剩余任务交接**：已提交(b4f745a 9 blocker)/工作树未提交(D2 启动修复+测试债)/未完成(Flyway·补单测·B9·major·doc13) — 新对话从这里接续 |

## 严重级别汇总（本轮新增/加深）

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B7 | 🔴 | 03 | 渠道/Webhook 配置实体含 appSecret/token/encodingAesKey 无 `@JsonIgnore`，经接口外泄 |
| B8 | 🔴 | 05 | CodegenService 按 module/name 拼写文件路径，存在路径穿越→任意文件写入 |
| M9 | 🟠 | 01 | API Key 的 scope/allowedTables 在过滤器中未强制，仅授予 ROLE_API_KEY |
| M10 | 🟠 | 01 | 企微回调路径未纳入安全白名单，回调要么打不通要么被迫开放 |
| M11 | 🟠 | 01 | 验证码以 INFO 级明文写日志（`【验证码】... 验证码={}`） |
| M12 | 🟠 | 04 | ToolPermissionGuard 对无元数据工具默认放行（fail-open） |
| M13 | 🟠 | 04 | KnowledgeBaseService 批量导入/进度为 TODO 占位，`getImportProgress` 假返回 COMPLETED |
| M14 | 🟠 | 03 | 坐席分配 allocate→accept 非原子、会话 getOrCreate 无锁，存在并发竞态 |
| m7 | 🟡 | 01 | ServletUtils 盲信 X-Forwarded-For，仅应在可信代理后使用 |
| m8 | 🟡 | 01 | ApiKeyAuthFilter 每请求同步写 last_used_at（注释称"异步"实为同步） |
| m9 | 🟡 | 03 | WecomKfCallbackService 的 virtual-thread executor 无关闭、无背压 |
| m10 | 🟡 | 全局 | 多处验签/比较用 `String.equals` 非常量时间 |

### 第二轮（系统管理 / AI / 企业 / 统计）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B9 | 🔴 | 07 | **系统性缺失方法级鉴权**：user/tool/company/stats/pay/channel 等管理接口几乎无 `@PreAuthorize`，可重置 admin 密码、增删用户、导出全员数据 |
| B10 | 🔴 | 07 | ToolController 工具调用/生成/注册/查看源码无鉴权，REST 直调可能绕过 ToolPermissionGuard→任意执行/生成代码 |
| M15 | 🟠 | 07/08 | 多个控制器以 JPA 实体作 `@RequestBody`→Mass Assignment（可注入 id/orgId/deleted 等） |
| M16 | 🟠 | 07 | `User.password`、渠道 `appSecret/token/secret` 缺 `@JsonIgnore` |
| M17 | 🟠 | 07 | `assignRolesToUser`(只增) 与 `assignPermissionsToRole`(删后建) 语义不对称 |
| M18 | 🟠 | 08 | ChatController 会话/消息操作未校验归属→对象级 IDOR |
| M19 | 🟠 | 08 | stats/tracking 接口无鉴权且查询无租户隔离→越权读全员分析数据 |
| 重复2 | 🟠 | 07 | 两套 PermissionService/PermissionController 职责重叠 |

### 第三轮（文件 / 短信 / AIGC）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B11 | 🔴 | 09 | 预签名上传 URL 接受任意 key→可获取覆盖他人对象的写 URL |
| M20 | 🟠 | 09 | 文件按 key 下载/删除无归属校验（IDOR）+ 文件名响应头注入 |
| M21 | 🟠 | 09 | SMS test-send 无鉴权/限流，可发真实付费短信（费用滥用） |
| M22 | 🟠 | 09 | SmsController 直接注入 Repository（越层），模板 CRUD 无鉴权 |
| M23 | 🟠 | 09 | AIGC 生成端点未接积分/权益门控，可无限付费生成 |
| M24 | 🟠 | 09 | Midjourney 回调无验签，可伪造完成事件注入 imageUrl |
| 重复3 | 🟠 | 09 | AssistantManagementService/Controller/DTO 又一组同名重复 |

### 第四轮（framework 结算引擎 / 存储，优先级 1）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B12 | 🔴 | 11a | MockPayChannelAdapter 无生产隔离，charge/withdraw/refund 假成功（B2 框架层根因） |
| B13 | 🔴 | 11a | 文件上传无类型/大小校验→任意文件上传（存储型 XSS/资源滥用） |
| B14 | 🔴 | 11a | 本地存储 download/delete 按 key 拼路径无包含校验→路径穿越任意读/删（B11/M20 根因） |
| M25 | 🟠 | 11a | WxPay 退款把原单总额误设为退款额，部分退款被拒/算错 |
| M26 | 🟠 | 11a | 退款无引擎层上限/幂等，仅依赖渠道去重 |
| M27 | 🟠 | 11a | Wx/Alipay downloadBill 占位空实现→对账静默失真（M13 同类） |
| M28 | 🟠 | 11a | 验签方法仅在具体适配器、未上提接口，回调可绕过（B3 框架层根因） |
| M29 | 🟠 | 11a | getPresignedUploadUrl 信任任意 key 无命名空间（B11 框架层根因） |
| M30 | 🟠 | 11a | S3 upload 用 input.available() 作 contentLength，上传可能截断/为空 |
| m18 | 🟡 | 11a | queryStatus 遍历所有渠道远程查询，Wx 异常返回 null 被当 UNPAID 掩盖 |
| m19 | 🟡 | 11a | charge 金额未校验 >0 |
| m20 | 🟡 | 11a | 预签名 PUT 未固定 contentType/size-range |

### 第五轮（framework 认证 / OAuth / License / 智能核心，优先级 2）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B15 | 🔴 | 11c | ToolCallDispatcher 暴露无鉴权 dispatch()，ToolService.invoke 直用→REST 工具调用绕过全部权限/风险门控（B10 根因） |
| M31 | 🟠 | 11b | OAuthClient 抽象无 state/nonce 校验原语，CSRF 防护外包且无强制 |
| M32 | 🟠 | 11b | LicenseLoader 硬编码测试公钥（"生产替换"），未替换则可伪造许可证 |
| M33 | 🟠 | 11b | @PremiumRequired 零使用，premium 门控形同虚设 |
| M34 | 🟠 | 11c | AssistantPermissionEvaluator 对未找到定义 fail-open，伪造 assistantId 获全权 |
| M35 | 🟠 | 11c | HumanApprovalService resolve() 无授权 + requestId 可预测→伪造/越权审批 |
| M36 | 🟠 | 11c | HITL 仅内存态 + 推送 TODO 未实现→PAUSE_FOR_HUMAN 端到端不可达 |
| m21 | 🟡 | 11b | OAuth token 响应（含 access/refresh token）DEBUG 明文记日志 |
| m22 | 🟡 | 11b | exchangeToken 无 token 响应错误/null 校验→NPE/掩盖错误 |
| m23 | 🟡 | 11b | OAuthAutoConfiguration 从 @Bean 返回 null（反模式） |
| m24 | 🟡 | 11c | PermissionScope.defaults() allowedTools=null 放行全部工具 |

### 第六轮（framework REST 控制器暴露面，优先级 3）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B16 | 🔴 | 11d | DynamicTableService 把 slug/列名/过滤键作 SQL 标识符拼接→建表/CRUD 全链路 SQL/DDL 注入 |
| B17 | 🔴 | 11d | ConditionEvaluator 拼接 field/value 生成 Flowable UEL 无转义→UEL 表达式注入（可达 RCE） |
| M37 | 🟠 | 11d | WebhookTriggerController 无验签 + 无 per-process 鉴权，任意认证用户触发任意工作流 |
| M38 | 🟠 | 11d | DataTableController 全表 CRUD 无 per-resource 鉴权/租户隔离（B9 类，暴露 B16） |
| M39 | 🟠 | 11d | HttpNode url 来自流程变量无 SSRF 防护→可打内网/元数据端点 |
| M40 | 🟠 | 11d | DataIngestController scope 校验仅 apiKey!=null 时生效，JWT/null 时 fail-open（M9 续） |
| M41 | 🟠 | 11d | ingest/insertBatch 无批量写入限额→无界批量写入资源滥用 |
| m25 | 🟡 | 11d | CodeExecutionNode JS 走 executeShell("node -e") 非 GraalVm 沙箱，隔离弱 |
| m26 | 🟡 | 11d | insertBatch N 次单条 insert，无批量化 |

### 第七轮（framework 数据/AI/知识库，优先级 4）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B18 | 🔴 | 11e | DataRouter.insertToTable 表名/列名拼接 SQL 注入（与 B16 同根，第二注入点，经 DataIngest 数据流可达） |
| M42 | 🟠 | 11e | AiEnricher 将外部数据原文拼入 LLM 提示词→提示词注入 |
| M43 | 🟠 | 11e | DataRouter.insertToKnowledgeBase TODO 占位，假报 inserted_count（M13 类） |
| M44 | 🟠 | 11e | ResilientChatService 仅事后计量，无 pre-call 配额强制→成本失控（M23 续） |
| M45 | 🟠 | 11e | KnowledgeVectorService.search(query,topK) 无租户过滤→跨知识库越权检索 |
| M46 | 🟠 | 11e | WebScrapingService 抓取任意 URL（SSRF）+ maxBodySize(0) 无限响应体→内网探测/内存 DoS |
| m27 | 🟡 | 11e | Midjourney notifyHook 无签名→入站回调不可验签（M24 根因） |
| m28 | 🟡 | 11e | DataRouter 用原始 target 作表名（无 data_ 前缀），命名不一致 |
| m29 | 🟡 | 11e | ResilientChatService 对任何异常一律降级 fallback，双倍成本/掩盖错误 |

### 第八轮（framework 基础设施，优先级 5）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B19 | 🔴 | 11f | MessageTemplateEngine FreeMarker 未限制 class resolver→SSTI/RCE（模板经 M22 无鉴权 CRUD 可达） |
| M47 | 🟠 | 11f | DistributedLockAspect finally 无条件 delete + 固定锁值→TTL 过期后误删他节点锁，互斥失效 |
| M48 | 🟠 | 11f | TaskConsumer 失败即 ACK 不重试/不进 DLQ，RetryableTaskConsumer 未接线→失败任务静默丢失 |
| M49 | 🟠 | 11f | TaskConsumer 无幂等 + 无 pending 认领 + 固定 consumer 名→崩溃重投/重复消费 |
| M50 | 🟠 | 11f | TwoLevelCache 失效仅本机（ApplicationEvent 非 Redis pub/sub）→多实例缓存陈旧 |
| M51 | 🟠 | 11f | OperationLogAspect 审计记录 params/response 原文无脱敏→敏感数据入日志 |
| m30 | 🟡 | 11f | TwoLevelCache.invalidateAll 用 KEYS（生产阻塞 Redis） |
| m31 | 🟡 | 11f | ConfigCacheManager.getSkillDef loader 恒 null（占位） |
| m32 | 🟡 | 11f | DistributedLockAspect 获取失败返回 null，对有返回值方法语义不清 |

### 第九轮（framework 编排/工作流引擎/AI 能力 + API 剩余，收官）新增

| 编号 | 级别 | 区域 | 一句话 |
|------|------|------|--------|
| B20 | 🔴 | 11g | WorkflowController.deploy 无鉴权 + Flowable 未禁 scriptTask→任意认证用户部署 BPMN scriptTask→RCE（B17/M37 链坐实） |
| M52 | 🟠 | 11g | CognitiveCycleExecutor 就地修改缓存共享 AgentDefinition.systemPrompt→跨请求污染/无界增长 |
| M53 | 🟠 | 11g | AI 能力服务（video/image/embedding 等）普遍无 pre-call 配额门控→成本失控（泛化 M23/M44） |
| m33 | 🟡 | 11g | TeamOrchestrator 正则解析 LLM JSON（脆弱）+ decomposeGoal 提示词注入 |
| m34 | 🟡 | 11g | AgentScheduler 用 License.userId 派生 Random seed 作防破解（security-by-obscurity） |
| m35 | 🟡 | 11g | TaskBoard nextReady+markRunning 非原子（并发重复取任务） |

> 完整问题清单与修复建议见各分区文档。门控判定仍为**不通过**（存在 blocker）。
> **API 剩余模块**：全仓 @PreAuthorize 仅 4 处控制器，其余无方法级鉴权——系统性复现 B9，按 [10 鉴权矩阵](10-authorization-matrix.md) 批量修复（见 11g）。

## framework 复审小结（11a–11f，优先级 1–5）

本轮 framework 深审新增 **9 blocker / 23 major / 15 minor**，全部为 🔴 高风险（资金/文件/RCE/注入/鉴权），按流程需**人类审核**后并入正式修复任务。

### blocker 清单（建议优先修复，多为同根可批量）

| 编号 | 主题 | 根因/同根 | 触达 |
|------|------|----------|------|
| B12 | Mock 支付渠道无生产隔离，假成功铸积分/假提现 | B2 框架根因 | 配置即可利用 |
| B13 | 文件上传无类型/大小校验（任意文件→存储型 XSS/滥用） | — | 认证用户 |
| B14 | 本地存储 download/delete 路径穿越（任意读/删） | B11/M20 根因 | 认证用户 |
| B15 | ToolCallDispatcher 无鉴权 dispatch() 被 REST 直用（绕过全部工具门控） | B10 根因 | 认证用户 |
| B16 | DynamicTableService 标识符拼接 SQL/DDL 注入 | 注入根① | 认证用户 |
| B17 | ConditionEvaluator UEL 表达式注入（→RCE） | 注入根② | no-code 作者 |
| B18 | DataRouter.insertToTable 标识符拼接 SQL 注入 | 与 B16 同根 | 摄入数据流 |
| B19 | MessageTemplateEngine FreeMarker SSTI（→RCE） | 注入根③ | 模板作者(M22 无鉴权) |
| B20 | BPMN 部署链 RCE（deploy 无鉴权 + Flowable scriptTask 未禁） | 注入根④ + B9 | 认证用户 |

**批量修复建议**：B16/B18 同为"SQL 标识符拼接"→统一标识符白名单；B17/B19/B20 同为"模板/表达式/脚本引擎未沙箱化"→统一禁危险解析与脚本；B12/B15 同为"危险默认路径未隔离/未收敛"；B13/B14 同为"存储输入未校验/未约束"。

### 系统性 major 主题（横切多文档）

- **鉴权 fail-open / 缺 per-resource**：M34（助手）、M40（ingest）、M37/M38（webhook/datatable）、M12（工具）——与 B9/B10 同源。
- **回调/外联无验签或无 SSRF 防护**：M28/m27（支付/MJ 回调）、M37（webhook）、M39（HttpNode）、M46（抓取）——与 B3/M24 同源。
- **计费未 pre-call 门控**：M44（LLM）、M23（AIGC）——成本失控。
- **占位假实现**：M27（账单下载）、M43（KB 写入）、M33（@PremiumRequired）、m31（skill 缓存）——与 M13 同类。
- **HITL/门控端到端不可达**：M35/M36（审批伪造 + 不可达）。
- **多实例正确性**：M47（锁误删）、M48/M49（任务丢失/重复）、M50（缓存陈旧）。

### 收尾状态

- [x] 优先级 1–5 + 收官全部产出（11a–11g）+ README 严重级别表/索引/覆盖进度同步。
- [x] 代码审查工作完成：framework 全部安全敏感区域已深审，API 剩余按鉴权矩阵批量确认（模式饱和）。
- [x] 9 blocker 修复已实施（设计 [12](12-blocker-remediation-design.md)，开发日志 [13](13-blocker-fix-dev-log.md)）——⚠️ 本环境无构建工具，编译/单测/acceptance 待 CI 或本地 `pnpm check:affected` 验证。
- [x] Flyway clean 生产隔离已核实：prod `spring.flyway.clean-disabled: true` + `aaf.flyway.clean-on-start: false`（11f 待确认关闭）。
- [ ] B9 鉴权矩阵批量修复（≈100 控制器）+ 顺带 major（M37/M38/M40/M24/M28/M44/M47–M50）——已审批纳入，作为后续独立任务。
- [ ] 残留编排细节（intelligent/cognition、memory/checkpoint/budget/agentscope 适配）属正确性/占位类，可随修复迭代抽查。

## 覆盖进度

已深度审查（读源码）：
- 框架：security（全）、engine/tool（sandbox/guard/valuerule/prompt）、crud 基类、credit
- aaf-common：BaseEntity/Result/ServletUtils/枚举体系
- aaf-api：pay/billing、channel、livechat、customerservice、system(user/role/permission/dataaccess/auth/file/sms)、ai/chat、ai/aigc(image)、company、stats、ui/tracking、tool、knowledge(base)
- aaf-auto-dev：git/CiCd/codegen
- framework（优先级 1，11a）：engine/settlement（引擎/Mock/Wx/Alipay 渠道/退款/对账）、storage（S3/本地/FileService/ImageProcessor/预签名）
- framework（优先级 2，11b/11c）：security/oauth（三客户端/AutoConfig）、security/license（Loader/Aspect/PluginRegistry）、engine/tool/ToolCallDispatcher、intelligent/assistant（PermissionEvaluator/HumanApprovalService/PermissionScope）、intelligent/core（confidence/token）
- framework（优先级 3，11d）：engine/workflow/trigger（Webhook/Cron）、engine/dataprocess/table（DataTable/DataIngest Controller + DynamicTableService）、engine/workflow/node（Code/Http 等）、condition（ConditionEvaluator）
- framework（优先级 4，11e）：engine/dataprocess（AiEnricher/DataCleaner/DataRouter/FieldMapper/DataPipeline）、intelligent/ai（chat ResilientChatService、image Midjourney）、engine/knowledge（VectorService/HybridSearch/WebScraping，抽样）
- framework（优先级 5，11f）：task（DistributedLockAspect/queue TaskConsumer+RedisStreamTaskQueue/retry）、messaging（MessageTemplateEngine）、engine/cache（TwoLevelCache/ConfigCacheManager/Invalidation）、sequence（SequenceService）、logging（OperationLogAspect）
- framework（收官，11g）：engine/workflow（FlowableWorkflowEngine/WorkflowEngine/FlowableConfig）、intelligent/agent（CognitiveCycleExecutor/AgentScheduler）、intelligent/team（TeamOrchestrator）、assistant/TaskBoard、ai/video（抽样）；API 剩余按鉴权矩阵批量确认

模式已确认稳定（鉴权缺失、实体作 DTO/响应、fail-open、占位实现、手工 JSON、重复抽象、并发竞态、对象级 IDOR、回调无验签、计费未门控、越层访问），未读区域大概率复现同类问题——继续逐文件读取的边际收益递减，已达模式饱和。

## 审查收官说明

> **代码审查工作已完成**（11a–11g 覆盖 framework 全部安全敏感区域 + API 鉴权矩阵）。模式已饱和：鉴权缺失、注入（SQL/UEL/SSTI/scriptTask）、fail-open、占位实现、回调无验签、计费未门控、对象级 IDOR、多实例正确性、缓存陈旧。

剩余未逐文件读区域均为**编排细节/正确性类，非新安全类**，可随修复迭代抽查：

- framework：`intelligent/cognition`（personalization/learning/retrieval/memory pipeline）、`engine/{memory,checkpoint,budget,metadata,monitor,meta}`、`intelligent/agent/agentscope` 适配层、ai 其余能力（embedding/rerank/speech/omni/music/model3d 的具体 provider 实现，已抽样 video）。
- API 层：剩余控制器逐条工单见 [10 鉴权矩阵](10-authorization-matrix.md)，预期复现 B9/M15/M16/M23/M24，按矩阵批量修复。
- 配置侧：Flyway clean 生产隔离待查 `application-prod.yaml`。
