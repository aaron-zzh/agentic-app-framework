---
level: Practice
layer: Model
purpose: AAF-120 养老业务模块（elder）一期的技术任务：按 M0 至 M3 四个里程碑拆分，含任务依赖与验证标准
status: active
version: 1.1.0
date: 2026-10-16
author: AaronZZH & Kiro
tags:
  - AAF-120
  - 养老
  - elder
  - 技术任务
related:
  - requirement.md
  - design.md
---

# 技术任务：养老业务模块 elder 一期（AAF-120）

> 需求：[requirement.md](requirement.md)　设计：[design.md](design.md)
> 负责人：architect 拆分 | 创建：10-15 | 设计于 10-15 通过人类审核，可按任务开工

## 任务约束

- 涉及权限、认证与数据模型，设计已确认；设计与任务冲突时先改设计，并重新审核。
- 项目未发布：直接替换，不保留旧路径，不加 fallback 或兼容层。设备预警与心跳只走 MQTT，不保留 REST 兜底。
- 一期以实现功能为主，不提前扩展：不做凭据轮换、批量导入序列号、排班、逐级升级、直传对象存储、家属端页面。
- 复用已有能力，不新建并行实现：任务队列用 `AsyncQueueTaskService`，钉钉用 `DingtalkChannelSender`，站内信用 `InternalMessageSender`，文件用 `FileRecordApi`，积分与语音用现有 `SpeechService` 与助理。
- 写法对照 `module/todo`：`package-info`、`ErrorCodeConstants`、`XxxResource`、`BaseCrudController`、`Patch` 更新、乐观锁、ReBAC。
- 子域之间只能走对方的 `api/` 或事件，禁止访问对方的 `domain` 与 `repository`；一期依赖方向：`guard → profile`，`mall → profile`、`mall → guard`、`album → profile`；`profile` 不得反向依赖 `guard`。
- 迁移文件编号：v200 至 v299 已登记给 Intelligent runtime，`device` 使用 v300 至 v399、`elder` 使用 v400 至 v499（均已登记）；schema 放 `db/migration/`、seed 放 `db/seed/`，各自单独成文件，按任务顺序取下一个编号；EntityDef 种子沿用 Entity Engine 的 v100 至 v199 号段，放 `db/seed/view/v109__elder_entity_def.sql`。号段规则只在所有环境可清库重建时成立：开发期按项目约定用 `aaf.flyway.clean-on-start` 重建，可原地修改 `elder` 自己尚未发布的迁移文件；生产也保持顺序执行，不开启乱序（已决定）：已部署环境之后的变更必须取比已部署最大版本更大的编号，号段内编号此后不再保证连续，已登记到改进意见。改 `aaf-common` 的枚举、`DictType`、`aaf-dependencies` 的 BOM，需协调者评估。
- 错误码使用 `10_000_000 ~ 10_999_999`，分段见设计文档。
- 管理端页面由 `sys_entity_def` 驱动，不手写页面；`EntityAction` 没有输入参数，需要输入的操作走创建或更新表单。
- 编码前需加载 `developer-service`（后端）与 `developer-webui`（前端）的 `resources`；完工前跑 `pnpm check:affected`。
- 测试：developer 写 `*Test.java` 单测，重点是状态机、幂等、鉴权边界；tester 写 `*AcceptanceTest.java` 覆盖需求文档的 Gherkin 场景；通信验收用无头设备模拟器。
- 跨仓任务：机器人端改造在 `modbot` 仓库完成，本文只登记，不在本仓库实现。

## 里程碑

| 里程碑 | 目标 | 任务 |
|---|---|---|
| M0 通信基础 | EMQX 可用，Java 与机器人都能连，有设备模拟器 | #12001 至 #12002 |
| M1 | 基础信息、设备配对与 MQTT 通道、预警、用药提醒、通知、管理端 | #12003 至 #12013 |
| M2 | 相册、服务预约 | #12014 至 #12015 |
| M3 | 助理与语音接入、对话创建用药、康复指导 | #12016 至 #12018 |
| 收尾 | 文档同步、机器人端改造登记 | #12019 至 #12020 |
| 多场景通用化 | 设备层去养老化：扩展契约、api 边界、迁移拆分、契约测试 | #12021 至 #12027 |
| Omni Realtime v1 | 共享受控实时会话、双认证入口、PCM 协议与 Web UI 迁移 | #12028 至 #12033 |
| HTTPS 自助绑定 | 登录用户扫码绑定自己的设备，管理端保留代配置能力 | #12034 |

## 任务列表

### M0 通信基础

1. [x] #12001 EMQX 部署与 Java MQTT 客户端
   - 在开发环境用容器部署 EMQX 单节点（compose 文件放仓库约定的部署目录），开 8883 TLS，配置 HTTP 认证与授权回调到 AAF 的内部端点（回调端点在 #12008 实现）；选定 Java MQTT 客户端库并验证与 Spring Boot 4 兼容，版本写入 `aaf-dependencies`（需协调者评估）；确认机器人用的 QtMqtt 能连上并稳定保持，记录 Keep Alive 与 Clean Session 取值
   - 注意：EMQX 是 BSL 许可，作为外部依赖单独部署，不随 AAF 打包，在部署文档里写明
   - verify: 容器启动后 Java 测试客户端与机器人各自能连接、订阅、发布 QoS 1 消息；EMQX 重启后客户端自动重连
2. [ ] #12002 无头设备模拟器
   - 状态：暂缓（属非必要测试代码，按要求本期不实现）
   - 测试用的设备模拟器（测试源码，不进生产包）：能完成配对、连接 MQTT、按信封收发消息、持久化指令收件箱、模拟 `bootId` 与 `statusSequence`、可注入重复投递、延迟、断线
   - 依赖：#12001
   - verify: 模拟器可脚本化地驱动后续验收场景

### M1 基础信息、设备、预警、用药

3. [x] #12003 模块骨架与数据模型（M1 范围）
   - `module/elder` 的 `package-info`；各子域 `ErrorCodeConstants` 分段；`db/migration/v400__elder_schema.sql` 建 M1 的表：`elder_profile`、`elder_contact`、`elder_health_item`、`elder_medication_plan`、`elder_medication_record`、`device_info`、`device_message`、`elder_alert`、`elder_alert_handling`；`db/seed/v401__elder_dict_seed.sql` 写字典与全局配置默认值（预警类型与状态、设备类型与区域、消息状态、用药记录结果、联系人关系，以及阈值）；表前缀 `elder_`，主数据软删除。号段 v400 至 v499 已登记
   - 需协调者评估：改 `aaf-common` 枚举与 `DictType`
   - verify: 空库启动 Flyway 成功；实体能被 JPA 校验通过
4. [x] #12004 profile：长者与联系人
   - `Elder`、`ElderContact` 的 CRUD 资源（`ElderResource`、Controller、Service、Patch 更新与乐观锁）；联系人含关系、优先级（仅作电话联系顺序展示）、可选的 AAF 账号、授权范围；家属对长者数据的访问用 ReBAC 关系表达；`ElderQueryApi`（概况、联系人、机器人简报）
   - 依赖：#12003
   - verify: 联系人账号被授权后才能读取长者；未授权被拒；乐观锁冲突返回 409；字段集分级，敏感字段只在 detail 输出
5. [x] #12005 profile：健康条目与用药
   - `ElderHealthItem`、`MedicationPlan`、`MedicationRecord` 的 CRUD；`MedicationApi`（查询到点计划、写入服药结果）；服药记录只增不改
   - 依赖：#12004
   - verify: 计划的启停、起止日期生效；服药记录不可修改与删除
6. [x] #12006 guard：设备与配对绑定
   - `Device` 实体与 `Device` 资源；`POST /api/device/pair/start` 与 `pair/poll`（配对会话存 Redis，带过期，失败 5 次锁定，按序列号与来源限流）；创建 `Device` 即绑定（填配对码、长者、所有者用户；校验管理权限、码有效且一次性、序列号未被绑定）；解绑动作撤销凭据并踢掉连接
   - 依赖：#12004、#12007
   - verify: 对应需求文档配对与绑定的全部场景；密钥只返回一次；换绑必须先解绑
7. [x] #12007 guard：设备认证
   - `DeviceAuthFilter` 与独立 `SecurityFilterChain`，只匹配 `/api/device/**`，优先级高于主链；凭据存 SHA-256 哈希与展示前缀；主体只授 `ROLE_DEVICE`，所有者 ID 作为数据上下文，经 `PermissionExecutionService.runAsOwner` 使用；不修改 `aaf-framework` 的 `SecurityConfig`；安全边界（越权、吊销、跨设备）补最小单测
   - 依赖：#12003
   - verify: 设备凭据访问用户接口被拒、用户 JWT 访问设备接口被拒；吊销后立即失效；设备凭据不继承任何用户角色
8. [x] #12008 guard：MQTT 适配
   - `DeviceMessagePublisher` 与入站处理接口，适配包封装 MQTT 细节；`MqttOutboxRelay` 发布 `DeviceMessage`；订阅 `up/presence`、`up/status`、`up/alert`、`up/ack`、`up/result`、`up/capabilities`；下行指令信封与 Ed25519 签名（私钥走密钥管理）；入站幂等与 `bootId`、`statusSequence` 校验；EMQX 认证与授权回调端点（只接受 broker 来源并校验共享密钥，设备只能订阅自己的 `down/*`、发布自己的 `up/*`）；在线状态 `ONLINE / SUSPECT / OFFLINE`（30 秒心跳，两个周期缺失为 `SUSPECT`，三个周期为 `OFFLINE`）
   - 依赖：#12001、#12003、#12007
   - verify: 对应通信验收场景（PUBACK 不等于已接受、重复投递只执行一次、旧 `bootId` 不覆盖新状态、过期不再下发、跨设备主题被拒绝）
9. [x] #12009 guard：提醒下发与用药闭环
   - `DeviceMessage` 状态机（`QUEUED / DISPATCHED / ACCEPTED / SUCCEEDED / FAILED / EXPIRED / CANCELLED / DELIVERY_UNKNOWN`）；定时扫描到点的用药计划，生成 `reminder.show` 指令；`up/result` 回写 `MedicationRecord`；超过确认窗口记漏服；连续漏服达到阈值通知联系人（站内信）；计划被停用时下发 `down/cancel`
   - 依赖：#12005、#12008
   - verify: 对应用药提醒的验收场景；没有设备确认的消息标记送达未知，不记已提醒；离线期间过期的提醒记漏提醒
10. [x] #12010 guard：预警事件与判定
   - `AlertEvent`、`AlertHandling`；入站 `FALL_SUSPECTED`、`HELP_REQUEST`、`CONFIRMED_OK`；`nextActionAt` 加定时扫描（`@Scheduled` 加分布式锁）推进 60 秒确认窗口；后端判定 `NO_ACTIVITY`（设备在线、非隐私模式、清醒时段）与 `DEVICE_OFFLINE`；晚到事件标记 `LATE`；状态机 `TRIGGERED → NOTIFYING → ACKED → RESOLVED / FALSE_ALARM`，`CANCELED`
   - 依赖：#12008
   - verify: 需求文档预警相关场景，包括确认窗口内取消、设备离线与隐私模式不误报、晚到仍通知、事件重复上报只处理一次
11. [x] #12011 guard：预警通知
   - 异步任务 `ELDER_ALERT_NOTIFY`（`AsyncQueueTaskService`）：向运营值班发站内信与钉钉群消息，向联系人发站内信，各渠道结果写 `AlertHandling`；钉钉用 `MessageRequest.direct(MessageChannel.DINGTALK, …)`，文案只含预警编号、类型、级别、时间、长者称呼；短信只预留开关 `aaf.elder.alert.sms.enabled` 默认关闭，不接模板；种子：默认值班服务账号（如 `elder-duty`，密码不入种子）、"运营值班"与"养老运营"角色，写入下一个编号的 `db/seed/` 文件（如 `v402__elder_duty_seed.sql`）
   - 依赖：#12010
   - verify: 对应需求文档通知场景；钉钉失败不影响站内信；开关关闭时不发短信；种子账号可登录管理端并收到站内信
12. [x] #12012 guard：机器人简报接口
   - `GET /api/device/brief`：称呼、偏好、禁忌、今日提醒、当前配置（阈值），不含证件号与详细病史；设备凭据鉴权
   - 依赖：#12004、#12005、#12007
   - verify: 设备只能取自己绑定长者的简报；无敏感字段
13. [x] #12013 运营管理端（M1 范围）
   - `view/v109__elder_entity_def.sql`（沿用 Entity Engine 的 v100 至 v199 号段）：长者（下挂联系人、健康条目、用药计划、服药记录）、设备、预警 的 EntityDef，分组"养老"；动作：设备解绑、预警认领、代记已知晓、用药计划停用启用；设备创建表单含配对码与所有者用户；预警列表默认筛选未结案、按级别与时间排序、未认领置顶高亮（通用列表不够时再加列表级只读自定义视图）；权限码与角色权限写入下一个编号的 `db/seed/` 文件（如 `v403__elder_permission_seed.sql`）
   - 依赖：#12004 至 #12011
   - verify: 运营能完成建档、绑定设备、处理预警的整条链路；前端无手写页面；`pnpm check:affected` 全绿

### M2 相册与服务预约

14. [x] #12014 album：相册
   - `ElderPhoto`、`POST /api/device/photos`（multipart、设备凭据、`clientPhotoId` 幂等、格式与大小上限、文件头校验）、经 `FileRecordApi` 存 `MASTER` 存储并 `retain`、删除软删除并 `release`；原图受控访问；管理端相册页
   - 依赖：#12007、#12004
   - verify: 对应相册场景；设备凭据不能读取相册；删除后文件引用被释放
15. [x] #12015 mall：服务预约
   - `ServiceItem`、`ServiceOrder`（待确认、已确认、已完成、已取消）；运营确认、取消、标记完成；确认后经 `DeviceMessageApi` 下发服务当天的预约提醒；管理端服务项目与预约页；服务分类用字典
   - 依赖：#12009、#12013
   - verify: 对应预约场景；状态非法流转被拒
   
### M3 助理与语音、康复指导

16. [x] #12016 guard：设备语音网关
   - `WS /api/device/voice/asr`（PCM 入、文字出）、`POST /api/device/chat`（SSE 文字流）、`POST /api/device/voice/tts`（流式音频）；设备凭据鉴权；以所有者身份调用现有 `SpeechService` 与助理，积分扣在所有者名下，积分不足返回明确错误码；对话固定使用"长者陪伴"角色，注入简报，只开放该角色的工具集；健康免责与紧急词规则；开工时先核对助理能否进程内调用，不行则用服务身份调用同一入口
   - 依赖：#12007、#12012、#12017
   - verify: 对应语音场景；现有 `/ws/asr`、`/agui/run` 不被改动
17. [x] #12017 agent：角色与工具
   - "长者陪伴""家属服务""运营助手"三个角色种子；工具：`queryElderOverview`、`listAvailableServices`、`draftServiceOrder`、`queryAlertStatus`、`listMedicationPlans`、`draftMedicationPlan`、`confirmMedicationPlan`（草稿存缓存，10 分钟过期，确认后创建并通知联系人核对，只能创建与查询）、`listRecentPhotos`；写类工具需用户确认；工具授权按角色限定
   - 依赖：#12005、#12014、#12015、#12018
   - verify: 对话创建用药的全部场景；运营助手不能起草预约；对话不能停用用药计划
18. [x] #12018 rehab：康复指导
   - `RehabGuide`（关联一个 `KnowledgeDocument`、草稿与已发布）、保存时校验文档存在、管理端页面、`RehabGuideApi`；`searchRehabGuide` 在已发布指导所关联的文档范围内检索并回答，带引用和免责声明；开工时核对检索范围限定的实现方式
   - verify: 对应康复场景；草稿指导的文档不参与回答；范围外问题不编造

### 收尾

19. [ ] #12019 机器人端改造登记（`modbot` 仓库，不在本仓库实现）
   - 状态：待办（modbot 仓库工作，不在本仓库）
   - 配对界面与凭据存储；MQTT 订阅、信封校验与验签、指令收件箱、`bootId` 与 `statusSequence`、遗嘱消息；`fallCandidate` 与 `personPresence*` 映射为 `up/alert`，补充倒地停留时长与本地询问；心跳增加 `lastPersonSeenAt` 与隐私模式；事件与照片本地持久化队列；`AafAgentBackend` 与语音提供方；执行 `reminder.show` 并回传结果；照片上传
   - verify: 与 #12002 的模拟器行为一致，端到端跑通配对、预警、用药提醒
20. [x] #12020 文档同步
   - 更新技术选型文档中 IoT 接入一行（阿里云物联网平台已停止新购，改为 EMQX）；通信设计文档补充 `elder` 实现范围与租约字段差异；部署文档补 EMQX 的 BSL 许可说明与部署方式；在 `docs/prd/improvements.md` 登记发现的文档不一致
   - 规范文档（`docs/reference/`）只能由协调者修改，发现问题写入 `dev-log.md`

### 多场景通用化

> 2026-10-16 静态核对后追加；决策见设计文档「多场景通用化决策」。以下任务均未编译、未测试、未跑 check。

21. [x] #12021 device：指令能力由场景声明、目标设备类型由场景选择
   - `DeviceScenario` 增加 `commandCapabilities()`、`commandDeviceType()`；`DefaultDeviceMessageApi` 去掉写死的 `reminder.show` 白名单与 `ROBOT`，改按设备所属场景校验；`config.update` 作为通用能力只允许 CONFIG 方向；`ElderDeviceScenario` 声明 `reminder.show` 与 `ROBOT`
   - verify: 假场景声明的能力可入队、其他场景的能力被拒、CONFIG 只认 `config.update`
22. [x] #12022 device：入站消息按场景分流
   - `DeviceInboundMessage` 增加 `scenarioKey`；`DeviceInboundHandler.supportsScenario` 默认处理全部场景；分发器同时匹配类型与场景；养老的预警与用药处理器覆盖 `supportsScenario`，去掉处理器内的设备回查过滤
   - 依赖：#12021
   - verify: 其他场景的设备消息不会进入养老处理器
23. [x] #12023 device：心跳与离线阈值走配置
   - 新增 `DeviceHeartbeatSettings`，简报下发与 `scanHeartbeatTimeouts` 共用，去掉写死的 60 与 90 秒
   - verify: 修改 `aaf.device.*` 后简报与离线判定同步变化
24. [x] #12024 device：公开 api 与 elder 改走 api
   - 新增 `DeviceQueryApi`（`requireLocked`、`listBySubject`、`listBoundOnline`）与 `DeviceView`；`DeviceMessageApi` 增加消息查询（`findByCommandId`、`findByMessageId`、`listByCorrelationPrefix`、`listExpiredWithoutResult`）与收尾（`markCancelled`、`markExpired`）和 `DeviceMessageView`；`AlertEventService`、`MedicationReminderService`、`DefaultGuardQueryApi`、`ElderMedicationInboundHandler` 改走 api；顺手把 `AlertHandlingService` 误用的设备错误码改回预警错误码，删除两处未使用的 `Device` 导入
   - 依赖：#12022
   - verify: `elder` 不再引用 `device` 的 `domain`、`repository`、`service`、`mqtt`、`vo`、`mapper`
25. [x] #12025 迁移与种子拆分：device 独占 v300 至 v399，elder 改 v400 至 v499
   - `v300__device_schema.sql`（含最终形态的序列号唯一索引）、`v301__device_dict_seed.sql`（字典与心跳配置，`device.scenarios.enabled` 默认空）、`v302__device_permission_seed.sql`（管理员授权与数据范围、「设备管理」菜单组）、`view/v110__device_entity_def.sql`；elder 改名为 `v400` 至 `v403`，追加场景字典项、启用 `elder`、养老角色对设备的授权
   - 风险：已执行过旧 v300 至 v303 的环境必须清库重建（项目约定的 `aaf.flyway.clean-on-start`）
   - verify: 空库 Flyway 顺序执行成功；设备菜单与权限独立于养老存在
26. [x] #12026 文档与号段同步
   - 号段表登记 `v300-v399 | 通用设备层（device）`、`v400-v499 | 养老业务模块（elder）`；设计文档 ADR 与扩展契约；本文迁移编号描述
   - 规范文档（`docs/reference/`）只能由协调者修改，本次由协调者直接修改
27. [x] #12027 契约测试
   - `DeviceScenarioContractTest`：用与养老无关的假场景验证能力校验、CONFIG 通用能力、按主体解析目标设备
   - 状态：已编写，按要求未运行

### Omni Realtime v1

> 本组是已登记的高风险技术任务。实现以 [design.md 的 Omni Realtime v1 章节](design.md#omni-realtime-v112028-起) 为唯一协议规格；不建 SQL、不改 `cascade v3`、不在本仓库改 ModBot。每项完成前由 developer 按规范运行 `pnpm check:affected`，本次设计工作不运行构建、测试、lint 或 check。

28. [x] #12028 Omni Realtime v1 协议与共享会话核心
   - 新增 `RealtimeConversationSession`、`RealtimeConversationSessionFactory`、严格 `hello` 解析和唯一 `ready` 事件；将现有 `/ws/realtime-conversation` 的会话状态、引擎事件映射、服务端检索回调和资源回收收拢为共享核心
   - 直接删除该 endpoint 旧的 query 配置、JSON Base64 音频、`video`、客户端 tools、`function_output`、`context_ready` 与 `response_latency` 协议处理；不保留 fallback、shim 或双协议分支；`cascade v3` 完全不触碰
   - 依赖：#12027
   - verify: `hello`/`ready` 精确字段、未知字段拒绝、未 ready 的输入拒绝、现有引擎事件映射为 v1 标准事件
29. [x] #12029 Omni Realtime 受控浏览器上下文与服务端知识检索
   - 实现 `WebRealtimeConversationContextResolver`：只使用 JWT 可信 `userId`、个人默认组织工作区和默认 Assistant；严格校验 `hello.roleKey` 已绑定、启用、可见，缺失时仅取该 Assistant 默认角色；浏览器 `hello.knowledge` 仅接受严格布尔值，省略默认启用
   - 将 `RealtimeKnowledgeSearchService` 接入共享会话的内部工具路径；仅在浏览器已启用且冻结知识范围非空时注册 `knowledge_search`；客户端不得声明工具或提交 function output；冻结知识范围、以所有者上下文检索，并仅发送脱敏的检索状态和耗时事件
   - 依赖：#12028
   - verify: 未绑定角色、Assistant/指令/知识字段注入、越权知识范围均被拒；检索只使用会话冻结范围
30. [x] #12030 设备实时上下文客户端与角色解析
   - 在 `module/device` 的公开 API/实现边界提供 `DeviceRealtimeContextClient`，重新核验 `DevicePrincipal` 对应的绑定、场景启用、主体与场景变量；`aigc` 不得访问 device 的 entity、repository 或 service
   - 实现 `DeviceRealtimeConversationContextResolver`：按设备角色覆盖、所有者默认 Assistant 角色、场景默认角色顺序解析，并逐项校验 Assistant 绑定、可见性和场景允许集合；`subjectId` 只能作为场景主体变量，不能成为用户、角色或计费主体
   - 依赖：#12029
   - verify: 撤销/禁用设备或场景不能建连；无有效候选角色拒绝；`subjectId` 无法提升设备权限
31. [x] #12031 v1 transport、PCM、事件与生命周期
   - 实现 `RealtimeWebSocketTransport` 及共享 session 串行状态机：上行 raw `pcm_s16le` 16 kHz 单声道 binary、下行 raw `pcm_s16le` 24 kHz 单声道 binary；JSON 图片仅用 `input.image`，不添加 binary frame header
   - 按入口强制 endpointing：浏览器 server VAD，设备 client commit 后 `response.create`；实现标准事件、一个活跃响应/检索、`response.cancel`、`session.close`、断链与启动失败的幂等资源回收
   - 添加结构化耗时日志（上下文、引擎打开、首响应、检索、响应、会话）和安全日志脱敏；`ready` 增加不敏感的 `contextResolveMs`、`knowledgeEnabled`，首个有效输出只发一次 `response.latency`；确保 JSON 事件与二进制音频的发送顺序
   - 依赖：#12030
   - verify: PCM/图片通道不混用；设备 commit-create 顺序受控；并发 cancel/close 不二次关闭、不泄漏 session 或检索任务，日志不含音频、图片、JWT、credential、提示词
32. [x] #12032 浏览器 JWT handler（后端部分；Web UI 不在本次范围）
   - 用 `JwtRealtimeConversationWebSocketHandler` 复用 `JwtHandshakeInterceptor` 注册 `/ws/realtime-conversation`，仅把可信用户身份交给共享 session；设备 credential、匿名和 AI 配置 query 参数均拒绝
   - 迁移 `apps/webui/src/app/(dev)/examples/realtime-conversation/page.tsx`：连接后发浏览器 `hello`，以 WebSocket binary 发送 PCM，按 `ready` 与标准事件播放无帧头 PCM；移除 Base64 音频、视频与客户端 Function Calling/知识开关 UI
   - 依赖：#12031
   - verify: 有效 JWT 加已绑定角色可对话；未绑定角色和设备凭据失败；浏览器不发送 commit/create，页面关闭会释放录音、播放和 WebSocket 资源
33. [x] #12033 设备 credential handler 与独立设备入口
   - 新增 `DeviceCredentialHandshakeInterceptor`、`DeviceRealtimeWebSocketConfig` 和 `DeviceRealtimeConversationWebSocketHandler`，注册 `/api/device/realtime-conversation`；复用 `DeviceSecurityConfig` 和 `DeviceAuthFilter` 的认证结果，只将已认证 `DevicePrincipal` 交给共享 session
   - 禁止 JWT、ApiKey、cookie、query 或配对匿名例外进入该端点；不修改 `/api/device/voice/session?proto=3` 或其他 `cascade v3` 入口，不在本仓库改 ModBot
   - 依赖：#12032
   - verify: 仅有效设备 headers 可以完成 device `hello`；JWT 用设备入口、设备凭据用浏览器入口均被拒；凭据吊销后握手拒绝；v3 路由与协议回归基线不变
34. [x] #12034 HTTPS 用户自助绑定
   - `pair/start` 返回 `https://<frontend>/device/pair?pairCode=...`；新增 JWT 接口 `POST /api/device-bindings`，请求只接受配对码，所有者强制取当前登录用户，固定 `general / ROBOT / OTHER`
   - 新增 `/device/pair` 页面，未登录先登录并保留完整回跳查询；管理员 `/api/admin/devices` 继续承担代配置、复杂场景和解绑
   - ModBot 始终把十分钟临时 HTTPS 配对地址输出到日志/命令行，并继续在有界面时显示二维码与文本
   - 依赖：#12006
   - verify: 匿名绑定被拒；用户不能指定其他所有者；配对码只能消费一次；已绑定设备必须先解绑；无界面设备可复制日志地址完成绑定

## 依赖概览

```text
#12001 → #12002
#12003 → #12004 → #12005
#12003 → #12007 → #12006
#12001 + #12003 + #12007 → #12008 → #12009 (+#12005)
#12008 → #12010 → #12011
#12004 + #12005 + #12007 → #12012
#12004 … #12011 → #12013
#12007 + #12004 → #12014
#12009 + #12013 → #12015
#12005 + #12014 + #12015 + #12018 → #12017
#12007 + #12012 + #12017 → #12016
#12027 → #12028 → #12029 → #12030 → #12031 → #12032 → #12033
#12006 → #12034
```

#12006 依赖 #12007 与 #12004，列在 #12007 之后实施；任务编号不代表实施顺序。

## 建议的开工顺序

1. #12001、#12003、#12004、#12007 可并行起步；
2. 然后 #12006、#12008、#12005；
3. 再 #12009、#12010、#12011、#12012；
4. 最后 #12013 收口 M1，端到端联调后再进入 M2。
5. Omni Realtime v1 严格按 #12028 → #12029 → #12030 → #12031 → #12032 → #12033 顺序实施；#12032 与 #12033 都完成后才开始跨入口联调。

## 新会话开工提示

- 设计与任务均已通过人类审核，可直接开工；先读 [requirement.md](requirement.md)、[design.md](design.md) 和本文。
- 编码前按 `AGENTS.md` 加载 `.kiro/agents/developer-service.json`（后端）与 `developer-webui.json`（前端，做 #12013 时）列出的 `resources`。
- 开工前的待办（协调者）：在 `docs/reference/dev/architecture-constraints.md` 的号段表登记 `v300-v399 | 通用设备层（device）` 与 `v400-v499 | 养老业务模块（elder）`；#12001 改 `aaf-dependencies`、#12003 改 `aaf-common` 需协调者评估。
- 建议起步：#12001、#12003、#12004、#12007 可并行；每个任务完工跑 `pnpm check:affected`，并在 `dev-log.md` 记录一行。
- Omni Realtime v1 开工顺序固定为 #12028 至 #12033：先读设计中的精确 `hello`/`ready`、事件和 PCM 合同；不改 `cascade v3`，不建 SQL，不在本仓库改 ModBot。#12032 需要同时加载 developer-service 与 developer-webui 的 resources。
- 设计结论摘要：EMQX 单节点同机独立容器；设备不登录，使用十分钟 HTTPS 配对链接；自助绑定归属当前登录用户，复杂场景由管理端代配置；设备认证用独立过滤链；预警同时通知运营值班（站内信加钉钉群）和联系人站内信，不做逐级升级；迁移保持顺序执行，生产也不开乱序。

## 变更记录

| 日期 | 变更内容 | 原因 |
|---|---|---|
| 2026-10-15 | 初稿 | 设计通过审核后拆分技术任务 |
| 2026-10-15 | 一期后端代码实现完成（#12002、#12019 除外） | 未编译、未测试、未跑 check；权限前缀统一为两段式 `elder:{子域}-{资源}`；时序上 v402 值班种子在 v403 之前，v403 补值班权限；EMQX 与密钥需人工配置 |
| 2026-10-15 | 拆出通用设备层 module/device 与场景扩展点，养老作为首个场景包 | 设备能力需支持儿童教育、办公助理、情感陪伴等多场景；设备路径 /api/device/**，表 device_info、device_message，权限 device:device，错误码 11_000_000 段；场景组织级开关目前为全局配置 |
| 2026-10-16 | 追加多场景通用化任务 #12021 至 #12027；device 独占迁移号段 v300 至 v399，elder 改 v400 至 v499 | 静态核对发现通用设备层写死养老能力白名单与 ROBOT 类型、入站消息不带场景、心跳阈值写死、elder 越过 api 边界；未编译、未测试、未跑 check |
| 2026-10-16 | 正式登记 Omni Realtime v1 任务 #12028 至 #12033 | 已确认共享会话、双入口认证、受控上下文、服务端检索、raw PCM、生命周期与 Web UI 迁移范围；不建 SQL、不改 cascade v3、不改 ModBot |
| 2026-10-17 | 补回 Omni v1 知识、图片与观测合同 | 浏览器 hello 支持严格布尔 `knowledge`；冻结范围非空才注册服务端检索；ready 与首输出延迟仅返回不敏感指标；按指示未运行检查 |
| 2026-10-31 | 增加 HTTPS 用户自助绑定 #12034 | 谁确认绑定谁成为设备所有者；管理端保留复杂配置；ModBot 无界面也输出临时配对地址 |
