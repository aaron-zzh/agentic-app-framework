---
level: Practice
layer: Model
purpose: AAF-120 养老业务模块（elder）一期的技术任务：按 M0 至 M3 四个里程碑拆分，含任务依赖与验证标准
status: active
version: 1.0.0
date: 2026-10-15
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
```

#12006 依赖 #12007 与 #12004，列在 #12007 之后实施；任务编号不代表实施顺序。

## 建议的开工顺序

1. #12001、#12003、#12004、#12007 可并行起步；
2. 然后 #12006、#12008、#12005；
3. 再 #12009、#12010、#12011、#12012；
4. 最后 #12013 收口 M1，端到端联调后再进入 M2。

## 新会话开工提示

- 设计与任务均已通过人类审核，可直接开工；先读 [requirement.md](requirement.md)、[design.md](design.md) 和本文。
- 编码前按 `AGENTS.md` 加载 `.kiro/agents/developer-service.json`（后端）与 `developer-webui.json`（前端，做 #12013 时）列出的 `resources`。
- 开工前的待办（协调者）：在 `docs/reference/dev/architecture-constraints.md` 的号段表登记 `v300-v399 | 通用设备层（device）` 与 `v400-v499 | 养老业务模块（elder）`；#12001 改 `aaf-dependencies`、#12003 改 `aaf-common` 需协调者评估。
- 建议起步：#12001、#12003、#12004、#12007 可并行；每个任务完工跑 `pnpm check:affected`，并在 `dev-log.md` 记录一行。
- 设计结论摘要：EMQX 单节点同机独立容器；设备不登录，屏幕配对码加所有者用户；设备认证用独立过滤链；预警同时通知运营值班（站内信加钉钉群）和联系人站内信，不做逐级升级；一期只做运营管理端；迁移保持顺序执行，生产也不开乱序。

## 变更记录

| 日期 | 变更内容 | 原因 |
|---|---|---|
| 2026-10-15 | 初稿 | 设计通过审核后拆分技术任务 |
| 2026-10-15 | 一期后端代码实现完成（#12002、#12019 除外） | 未编译、未测试、未跑 check；权限前缀统一为两段式 `elder:{子域}-{资源}`；时序上 v402 值班种子在 v403 之前，v403 补值班权限；EMQX 与密钥需人工配置 |
| 2026-10-15 | 拆出通用设备层 module/device 与场景扩展点，养老作为首个场景包 | 设备能力需支持儿童教育、办公助理、情感陪伴等多场景；设备路径 /api/device/**，表 device_info、device_message，权限 device:device，错误码 11_000_000 段；场景组织级开关目前为全局配置 |
| 2026-10-16 | 追加多场景通用化任务 #12021 至 #12027；device 独占迁移号段 v300 至 v399，elder 改 v400 至 v499 | 静态核对发现通用设备层写死养老能力白名单与 ROBOT 类型、入站消息不带场景、心跳阈值写死、elder 越过 api 边界；未编译、未测试、未跑 check |
