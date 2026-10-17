---
level: Practice
layer: Model
purpose: AAF-120 养老业务模块（elder）的初步方案设计：分层与依赖规则、子域划分、概念登记表、域间交互、智能层、数据安全与错误码
status: active
version: 1.0.0
date: 2026-10-15
author: AaronZZH & Kiro
tags:
  - AAF-120
  - 养老
  - elder
  - 设计
related:
  - requirement.md
  - ../../../design/apps/service/module-structure.md
  - ../../../reference/dev/architecture-constraints.md
---

# AAF-120 方案设计

> 状态：设计已确认（2026-10-15 通过人类审核），可按 [tasks.md](tasks.md) 开工。涉及新模块、数据模型与权限，属于高风险，设计变更需重新审核；设计与任务冲突时先改设计。
> 字段级设计和接口签名在对应技术任务开工时补充到代码与本文。

## 参考文档

- 需求：[requirement.md](requirement.md)
- 后端模块结构：[module-structure.md](../../../design/apps/service/module-structure.md)
- 参考实现：`aaf-api/.../module/todo`（资源定义、Patch 更新、乐观锁、ReBAC、系统任务入口）
- 机器人终端工程：`modbot`（Orange Pi 5B / RK3588S，Qt + RKNN），本文引用其 `README.md`、`docs/architecture.md`、`docs/capability-registry.md` 与感知代码 `libs/modbot_perception`
- 养老机器人后台服务功能架构（PDF，业务服务层、核心 AI 能力层、基础支持层共 10 个模块）
- 视觉检查需求（图片，5 个摄像头场景）
- AAF 既有设计：[ModBot 设计索引](../../../design/apps/modbot/README.md)、[Qt 机器人通信设计](../../../design/apps/modbot/qt-robot-communication.md)（MQTT 设备通道，已采纳、未实现）、[视觉感知融合设计](../../../design/apps/modbot/visual-perception-fusion.md)

## 现状核对

| 事项 | 现状 | 对本设计的影响 |
|---|---|---|
| 业务模块位置 | `aaf-api/.../module/{模块}`，`system`、`company` 内部按子域分包，子域内含 `controller / domain / repository / service / vo` | `elder` 沿用同样的子域分包 |
| 模块根文件 | `todo` 有 `package-info.java`、`ErrorCodeConstants`、`XxxResource` | 每个子域各有一个 `ErrorCodeConstants` 分段与 `XxxResource` |
| 跨模块交互 | `pay`、`billing`、`document`、`approval` 有 `api/` 子包 | 子域对外只暴露 `api/` |
| 错误码段 | 已用 1、3（仅 `3_000_100`）、5 至 9 开头 | `elder` 使用 `10_000_000 ~ 10_999_999` |
| 结算 | `aaf-framework` 有 `engine/settlement`（`PayChannelAdapter`、微信、支付宝、佣金余额渠道）；`aaf-api` 有 `pay`、`billing`、`brokerage` | `settle` 的支付走现有渠道，不新建通道 |
| 组织与工作空间 | `system/org` 有 `Organization`、`Workspace` | 服务商、社区站点用组织表示，不新建机构表 |
| 数字人 | `module/digitalhuman` | 陪伴与语音提醒复用 |
| 医疗工具 | `module/tool/medical` 的 `MedicalAnswerTool`、`MedicalKnowTool`，含隐私拦截与免责声明 | 健康问答复用，新增工具沿用同样的拦截模式 |
| 待办 | `TodoService.createSystemTodo` 受控入口 | 护理员与家属待办复用，不另建 |
| 通知 | `InternalMessageSender` 事务提交后发送 | 预警与服务通知复用 |
| 统计 | `module/stats`、`v10__stats_views.sql` 视图 | `ops` 报表与大屏优先用只读视图 |
| Flyway | 迁移到 `v20`，seed 到 `v24`，EntityDef seed 到 `v108` | `elder` 的迁移编号在实现时取当时最新值 |
| 机器人工程 | `modbot` 由我们自己维护。第一版以本机 OpenClaw 为主大脑、阿里 AOQ 直连语音，**不启用 AAF**；后续改为对接 AAF 助理。配置里已有 `aaf.restEndpoint / sseEndpoint / mqttEnabled`，`RestClientAdapter`、`SseClientAdapter`、`MqttClientAdapter` 均为占位；感知已产出 `personEntered / personLeft / personPresenceStarted / personPresenceEnded / personStable / personIdentified / fallCandidate`；已有本地相册与自动拍照 | 机器人是主要终端，两边同时改造，不存在跨团队协调成本，见"机器人终端与视觉预警" |
| AAF 对机器人的既有设计 | `docs/design/apps/modbot/qt-robot-communication.md` 已采纳协议矩阵：MQTT over TLS 为设备通道（含主题、命令信封、签名、租约、Outbox/Inbox），REST/SSE 用于监控 WebUI，HTTPS 加对象存储用于大文件，**尚未实现**。仓库中没有任何 MQTT 依赖，技术选型文档写明 IoT 接入为"阿里云 IoT Platform 加 Spring Integration MQTT" | 一期按该设计实现设备通道；租约相关字段对非物理能力暂不实现，差异记入 ADR；MQTT 依赖与 broker 先做 PoC |
| 任务队列 | `framework/task` 已有 `AsyncQueueTaskService`（`sys_async_task` 持久化）与 `RedisStreamTaskQueue`（优先级 Stream、延迟 ZSet、重试、死信），设计见 `task-queue.md` | 不再为通知新建 Redis 消息队列，直接复用 |
| 短信 | `aaf-framework/messaging/sms` 已有 `AliyunSmsSender`、`TencentSmsSender`、`SmsSenderRouter`、`SmsChannelSender`，经 `MessageService.send / sendSync` 调用，需配置 `aaf.messaging.sms.provider` 并提供厂商模板编码；`aaf-api` 的 `system/sms` 只管理模板。上一版"没有发送服务"的判断有误 | 短信能力已具备，现在缺的是厂商审核通过的模板。`elder` 预留开关，默认关闭 |
| 微信渠道 | `module/channel` 有 `WechatMpChannelAdapter`（服务号，支持模板消息 `pushTemplate`）、`WechatMiniChannelAdapter`（小程序客服消息）；`MiniAppLoginService` 把 openid 存入 `UserOauth`，`sys_contact_identity` 也可存微信 openId | 家属通知可走服务号模板消息；需要家属账号已绑定 openid，且服务号模板已配置 |
| 站内信 | `InternalMessageSender` 存库加 WebSocket（`/ws/notifications`）推送；没有发现厂商手机推送（个推、极光、APNs） | 站内信只在应用在线时实时到达，离线时只能下次打开才看到，不能作为紧急预警的唯一渠道 |
| 语音与助理 | `/ws/asr`（PCM 二进制帧进、文字 JSON 出，JWT 握手，积分预检）、`/api/voice/stt`、`SpeechService`（含流式 `synthesizeStream`）、`/ws/omni-realtime`；助理对话走 `/agui/run`；TTS 没有对外直连接口（配音走 `/aigc/tasks/submit`） | 机器人对接助理的语音链路缺一个 TTS 流式出口，且现有端点用用户 JWT 鉴权，不接受设备凭据 |
| 文件存储 | `system/file` 的 `FileRecordApi`、`FileUploadService`、`FileStoragePort`，用途枚举 `MASTER / PUBLIC_ASSET`，支持引用计数（`retain / release`） | 相册照片复用，不另建存储 |
| 知识库 | `module/knowledge` 有 `KnowledgeBase`、`KnowledgeDocument`、`KnowledgeBaseService`；框架 `engine/knowledge` 有混合检索与 RAG，`KnowledgeAccessScopePort` 可限定检索范围 | 康复指导关联知识文档，问答走 RAG，不另建文档存储 |
| 异步任务 | `framework/task` 的 `AsyncQueueTaskService`，`TodoQueueService` 为示例；`submit` 没有延迟参数 | 通知发送用异步任务；确认窗口用状态驱动的定时扫描，见"队列与定时" |
| 钉钉通知 | `framework/messaging/provider/dingtalk` 的 `DingtalkChannelSender`（群机器人 Webhook，Markdown，可选加签），由 `aaf.messaging.dingtalk.api-key` 启用；`AuthService.notifyDingtalkNewUser` 已用 `MessageRequest.direct(MessageChannel.DINGTALK, …)` 发送新用户注册通知；另有 `DingtalkBotChannelAdapter`（企业内部机器人，用于入站对话） | 预警钉钉通知直接复用发送器，不需要新代码和模板 |
| 认证 | Spring Security OAuth2 Resource Server，无状态 JWT（HS256，含 `roles`、`permissionVersion`、`jti`，校验 issuer、audience、Redis 黑名单），刷新令牌为 Redis 中的不透明串；登录方式有密码、邮箱验证码、手机验证码、企业微信/钉钉/微信 OAuth、微信小程序登录与手机号登录，另有图形验证；并列的 API Key（`aaf_dk_` 前缀）认证；WebSocket 在握手阶段用 `JwtHandshakeInterceptor` 校验 JWT，`/ws/**` 在 HTTP 层是 `permitAll`；角色层级 `SUPER_ADMIN > ADMIN > MEMBER > GUEST`，CRUD 另有 L1 至 L4 授权与 ReBAC | 运营管理端沿用现有登录与角色；设备不是用户，需要独立的设备认证，见"认证方案与设备认证" |
| 阿里云物联网平台 | 官方帮助中心将该产品标为"文档停止维护"；计费概述页写明"物联网平台自 2025 年 2 月 1 日起不再支持新购"；服务端收设备消息用 AMQP 1.0 消费组（5671 端口），不是 MQTT；下发消息用 HTTPS `Pub` 接口，不能经 AMQP 下发 | 技术选型文档中"阿里云 IoT Platform 加 Spring Integration MQTT"已不适用，不能作为新建选型，见"Broker 与客户端库" |
| 管理端动作 | `EntityAction` 只有 `key / label / type / execution / endpoint / confirmMessage / visibleWhen / position`，没有输入参数 | 无参动作（确认、取消、发布、停用、解绑）用 `actions`；需要输入的（绑定设备、预警处理结论）走创建或更新表单，或 record 级自定义视图 |

## 分层与依赖规则

一期按两层落地：业务域层和智能层。

- 业务域层：一期有 `profile`、`guard`、`mall`、`album`、`rehab` 五个子域，平级。子域之间只能通过对方的 `api/` 接口或领域事件交互，禁止访问对方的 `domain` 与 `repository`。`provider`、`workorder` 二期加入。
- 智能层：`agent`，不拥有业务表，只调用业务域的 `api/`。任何业务域不得依赖 `agent`。

两层足够的原因：一期五个子域共 13 个实体，其中 `album`、`rehab` 各只有一个实体，子域间只有几条单向依赖（`guard` 依赖 `profile` 取联系人与用药计划；`mall`、`album` 依赖 `profile` 取长者信息；`mall` 依赖 `guard` 下发提醒；`rehab` 只依赖外部的 `knowledge` 模块），用"只走 `api/` 和事件"一条规则就能约束，不需要再为子域划层级。`profile` 不得反向依赖 `guard`，用药提醒的调度与下发由 `guard` 发起，`profile` 只提供数据与记录接口。

二期加入 `settle`、三期加入 `ops` 时，再启用下面的目标分层。

### 目标分层（二期之后）

七个子域按"谁、买、做、钱、管"分层，加一个不拥有业务表的横切智能层。依赖只能自上而下，下层不得依赖上层。

```text
                    ┌──────────────────────────────┐
  横切智能层         │  agent  工具 / 技能 / 角色 / 知识库种子   │  只读写各域 api，无人依赖它
                    └──────────────────────────────┘
  治理层             │  ops        质控、投诉、报表、大屏           │
  结算层             │  settle     费用、补贴、分账、对账           │
  履约层             │  workorder  派单、上门、工单    guard  呼叫、设备、预警   │
  交易层             │  mall       目录、套餐、订单                 │
  主体层             │  profile    长者        provider  服务商、护理员、资质   │
```

规则：

- 上层可以调用下层的 `api/`，下层不得调用上层。
- 同层子域（`profile` 与 `provider`，`workorder` 与 `guard`）互不直接调用，需要协作时走领域事件。
- 下层通知上层用领域事件，例如订单已支付、工单已完成、预警已触发，由上层监听处理。
- `ops` 只读，通过各域 `api/` 或只读视图取数，不写其他域的表，也不被其他域依赖。
- `agent` 可以调用任何域的 `api/`；任何域不得依赖 `agent`。
- 禁止跨域访问 `domain` 与 `repository`，由 ArchUnit 规则约束（待规则激活后补充）。

### 为什么加智能层而不是第八个业务域

AAF 的定位是 AI 原生，养老场景里"理解长者、调度服务、研判预警、生成报告"都需要 AI。把 AI 能力放进某个业务域会让该域依赖所有其他域，破坏分层。放进横切层则它只依赖各域公开接口，业务域保持纯业务。

智能层包含：

- 工具：包装各域 `api/` 的 `@Tool` 方法，沿用 `module/tool/medical` 的风格。
- 技能与角色：长者陪伴、家属服务、护理员助手、运营助手，通过 seed 数据声明，工具授权按角色限定。
- 知识库与记忆：养老政策、补贴规则、护理规范、服务说明为知识库种子；长者偏好与情绪走记忆引擎。
- 工作流：预警研判、周报、回访，使用 AI 工作流，审批类场景用审批流。

### 考虑后未单列的内容

| 内容 | 处理 | 理由 |
|---|---|---|
| 家属协同、监护授权 | 归 `profile`，用联系人加 ReBAC 关系表达 | 家属本质是长者的关联方，不是独立业务 |
| 需求评估、照护计划 | 评估归 `profile`；服务推荐由 `mall` 读取评估结果 | 评估是对长者的事实描述，推荐是交易行为 |
| 服务评价 | 归 `workorder`，评分聚合由 `provider` 监听事件完成 | 评价产生于工单，被聚合到服务资源 |
| 投诉与质控 | 归 `ops` | 属于监管动作，与履约解耦 |
| 站点、社区、机构 | 用 `system/org` 的组织 | 已有多租户与组织能力 |
| 健康（体征、档案） | 归 `profile` | 健康是长者状况的一部分，`guard` 通过事件消费 |
| 机构养老 | 本期不做 | 范围外 |
| 终端（长者端、家属端、护理员端、大屏） | 前端或 UniApp，不属于后端模块 | 另立故事 |

## 包结构

```text
module/elder/
├── package-info.java              # 模块说明，参照 todo
├── profile/
│   ├── api/                       # 对外接口，如 ElderQueryApi
│   ├── controller/ domain/ repository/ service/ vo/ mapper/
│   ├── event/                     # 本域发布的领域事件
│   ├── ElderResource.java         # 资源定义，每个主资源一个
│   └── ErrorCodeConstants.java    # 本域错误码分段
├── provider/ …                    # 结构同上
├── mall/ …
├── album/ …
├── rehab/ …
├── workorder/ …
├── guard/ …
├── settle/ …
├── ops/ …
└── agent/
    ├── tool/                      # @Tool 工具，只依赖各域 api
    └── config/                    # 工具登记与技能角色种子装配
```

命名约定：

- 表前缀 `elder_`，例如 `elder_profile`、`elder_order`。
- 资源键 `elder.{子域}.{资源}`，例如 `elder.profile.elder`；权限前缀 `elder:{子域}:{资源}`。
- 字典与枚举：枚举放 `aaf-common` 的 `enums`，字典类型在 `DictType` 登记。
- 子域名为建议，和名称映射见需求文档，评审时可调整。

## 概念登记表

一个概念对应一个实体、一张主表。字段在设计确认后补充。

### profile 长者中心

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 长者 | Elder | `elder_profile` | 主数据，关联系统用户可为空（长者可无账号） |
| 联系人 | ElderContact | `elder_contact` | 紧急联系人与家属，可绑定账号并携带授权范围 |
| 健康条目 | ElderHealthItem | `elder_health_item` | 慢病、过敏、用药、既往史，按类型区分 |
| 体征记录 | VitalSign | `elder_vital_sign` | 血压、血糖、心率、体重，手工或设备写入 |
| 能力评估 | CapabilityAssessment | `elder_assessment` | 量表、得分、等级、有效期。二期 |
| 用药计划 | MedicationPlan | `elder_medication_plan` | 药名、剂量说明、服用时间点、起止日期、启停状态 |
| 服药记录 | MedicationRecord | `elder_medication_record` | 每个时间点的结果：已服、漏服、跳过，含来源（设备确认或家属代记），只增不改 |

### provider 服务资源

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 服务商 | ServiceProvider | `elder_provider` | 绑定组织，含服务范围与状态 |
| 护理员 | Caregiver | `elder_caregiver` | 所属服务商，可绑定系统用户，含技能 |
| 资质 | Qualification | `elder_qualification` | 证书、有效期、审核状态 |
| 排班 | CaregiverSchedule | `elder_schedule` | 可服务时段 |
| 服务评分 | ProviderRating | `elder_provider_rating` | 评价事件累计的聚合，不手工修改 |

### mall 服务商城

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 服务分类 | 字典 `elder_service_category` | 无 | 一期用字典，不建实体；服务项目多了需要树形再建 `ServiceCategory` |
| 服务项目 | ServiceItem | `elder_service_item` | 价格、单位、时长、适用评估等级 |
| 服务套餐 | ServicePackage | `elder_service_package` | 含项目与次数、有效期 |
| 套餐明细 | PackageItem | `elder_package_item` | 套餐与项目的关联及次数 |
| 服务订单 | ServiceOrder | `elder_order` | 下单主体，状态机见下 |
| 订单明细 | ServiceOrderItem | `elder_order_item` | 订单中的项目与数量 |
| 套餐权益 | PackageEntitlement | `elder_entitlement` | 购买套餐后的剩余次数与有效期 |

### workorder 服务执行

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 工单 | WorkOrder | `elder_work_order` | 由订单明细或应急事件生成 |
| 派单记录 | DispatchRecord | `elder_dispatch` | 每次指派、改派、拒单的记录 |
| 上门记录 | VisitRecord | `elder_visit` | 签到签退、定位、服务内容、照片 |
| 服务评价 | ServiceEvaluation | `elder_evaluation` | 评分与评语，来自长者或家属 |

### guard 安全守护

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 设备 | Device | `device_info` | 机器人与摄像头节点：序列号、`deviceKey`、类型、区域、所属长者、所有者用户（计费主体）、凭据哈希、状态、在线状态、`bootId`、最近心跳 |
| 下行消息 | DeviceMessage | `device_message` | 下发给设备的提醒、撤销与配置，即 outbox：含 `commandId`、能力、状态（`QUEUED / DISPATCHED / ACCEPTED / SUCCEEDED / FAILED / EXPIRED / CANCELLED / DELIVERY_UNKNOWN`）、有效期、结果 |
| 预警事件 | AlertEvent | `elder_alert` | 视觉事件、求助、无活动、设备离线等触发的预警，状态机见下 |
| 响应记录 | AlertHandling | `elder_alert_handling` | 各渠道通知结果、认领、应答、处理结论，只增不改 |
| 预警规则 | AlertRule | `elder_alert_rule` | 后置。一期阈值用全局配置，按长者覆盖时再建 |
| 联系链路 | ResponseChain | `elder_response_chain` | 后置。一期按 `ElderContact` 的优先级顺序通知，不单独建表 |

一键求助不再单独建呼叫记录，作为 `AlertEvent` 的一种类型。设备原始上报数据量大，不逐条入业务表，只保留触发预警的事件和最近一次心跳。

### album 相册

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 照片 | ElderPhoto | `elder_photo` | 长者的照片：文件引用（`system/file`）、拍摄时间、来源（机器人手动、机器人自动）、设备、说明；删除走软删除并释放文件引用 |

### rehab 康复指导

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 康复指导 | RehabGuide | `elder_rehab_guide` | 标题、分类（字典）、摘要、适用人群说明、关联的知识文档、发布状态；内容正文在知识库文档中，不在本表 |

### settle 结算中心

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 费用单 | FeeBill | `elder_fee_bill` | 订单应付、补贴抵扣、自付 |
| 补贴方案 | SubsidyPlan | `elder_subsidy_plan` | 规则、额度、适用对象、有效期 |
| 补贴账户 | SubsidyAccount | `elder_subsidy_account` | 长者的补贴额度与余额 |
| 补贴流水 | SubsidyLedger | `elder_subsidy_ledger` | 账户变动明细，只增不改 |
| 服务商结算单 | ProviderSettlement | `elder_provider_settlement` | 按完成工单生成 |
| 对账批次 | ReconcileBatch | `elder_reconcile_batch` | 一次对账 |
| 对账差异 | ReconcileDiff | `elder_reconcile_diff` | 差异项与处理状态 |

### ops 监管运营

| 产品概念 | 数据实体 | 主表 | 说明 |
|---|---|---|---|
| 质控抽检 | QualityCheck | `elder_quality_check` | 抽检对象、结论、整改 |
| 投诉 | Complaint | `elder_complaint` | 受理、处理、回访 |

报表与大屏使用数据库只读视图和查询服务，不新增实体。

## 一期实体范围

一期聚焦七个核心场景：基础信息、跌倒等预警、用药提醒（含对话创建）、陪伴聊天（对接助理与语音）、服务预约、康复指导（管理、查看、问答）、相册管理。家属关怀作为贯穿能力（联系人授权、预警与漏服通知、相册查看）融入其中。共 13 个实体，其余实体后置，不在一期迁移文件中创建。

| 子域 | 一期实体 | 后置实体 |
|---|---|---|
| profile | Elder、ElderContact、ElderHealthItem、MedicationPlan、MedicationRecord | CapabilityAssessment、VitalSign |
| guard | Device、DeviceMessage、AlertEvent、AlertHandling | AlertRule、ResponseChain |
| mall | ServiceItem、ServiceOrder | ServicePackage、PackageItem、ServiceOrderItem、PackageEntitlement |
| album | ElderPhoto | 相册分组、人物标签 |
| rehab | RehabGuide | 个性化康复计划与打卡 |
| provider | 无 | 全部 |
| workorder | 无 | 全部 |
| settle | 无 | 全部 |
| ops | 无 | 全部 |

一期取舍说明：

- `ServiceOrder` 一期即预约单：一个预约对应一个服务项目和数量，由运营在管理端确认并线下安排服务人员，状态记录到完成。没有 `provider` 与 `workorder`，因此没有派单、上门签到、服务评价；这些随二期加入，届时在已确认的预约单上生成工单。
- 一期预警只做跌倒、一键求助、长时间无活动、设备离线四类（见"视觉预警场景"），阈值用全局配置，不建规则表和通知链路表。
- 能力评估后置：一期预约不按评估等级过滤服务项目。
- 服务分类用字典，不建实体。
- 一键求助并入 `AlertEvent`，不单独建呼叫记录。
- 康复指导只做"关联文档加问答"，不做个性化康复计划与打卡；文档本身在知识库管理，`RehabGuide` 只保存索引信息与关联。
- 相册一期做上传、查看、删除和家属可见性，不做分组、人物标签与人脸聚类。

## 核心状态机

### 订单

一期（预约，无支付，无工单）：

```text
CREATED → CONFIRMED → COMPLETED
   ↓          ↓
CANCELLED  CANCELLED
```

- `CREATED` 表示已提交待运营确认；运营确认后进入 `CONFIRMED`，并向长者的机器人下发预约提醒；服务完成后由运营标记 `COMPLETED`。
- 二期加入 `workorder` 后，`CONFIRMED` 之后增加 `FULFILLING`，完成状态由工单驱动。

二期加入支付后：
```text
CREATED → PAID → FULFILLING → COMPLETED
   ↓        ↓         ↓
CANCELLED  REFUNDING → REFUNDED
```

- 支付成功由 `pay` 回调驱动，`mall` 监听后进入 `PAID` 并发布订单已支付事件。
- 全部工单完成后进入 `COMPLETED`，套餐订单同时核销权益。

### 工单（二期）
```text
PENDING_DISPATCH → DISPATCHED → ACCEPTED → IN_SERVICE → PENDING_CONFIRM → COMPLETED
        ↑              │            │
        └──────────────┴── 改派 ────┘          任一服务前状态 → CANCELLED
```

- 派单前校验护理员资质有效、排班可用、区域匹配。
- 状态流转规则沿用 `TodoService` 的 `Map<状态, Set<允许目标>>` 写法，非法流转返回独立错误码。

### 预警

```text
TRIGGERED ─ 设备确认无恙 ─→ CANCELED
    │
    └─ 确认窗口超时 ─→ NOTIFYING ─ 运营认领并代记已知晓 ─→ ACKED ─→ RESOLVED
                                                          └──→ FALSE_ALARM
```

- 一键求助没有确认窗口，直接从 `TRIGGERED` 进入 `NOTIFYING`。
- 进入 `NOTIFYING` 时同时通知运营值班（站内信、钉钉）和联系人（站内信），不做逐级升级，也不自动关闭；未认领的预警在管理端列表置顶高亮。
- `ACKED` 表示运营已认领并联系上人；`RESOLVED` 与 `FALSE_ALARM` 需要处理人填写结论，备注写明处理人。
- 系统不自动拨打急救电话，由联系人或运营判断并处理。

## 域间交互

### 对外接口

| 接口 | 提供方 | 使用方 | 用途 |
|---|---|---|---|
| `ElderQueryApi` | profile | mall、guard、workorder、settle、agent | 查询长者概况、联系人（含优先级与授权）、机器人简报 |
| `MedicationApi` | profile | guard、agent | 查询到点的用药计划，写入服药记录（已服、漏服、跳过） |
| `DeviceMessageApi` | guard | mall、agent | 向长者的设备下发提醒类消息 |
| `ElderPhotoApi` | album | agent | 查询长者最近的照片（家属助手"看看最近的照片"） |
| `RehabGuideApi` | rehab | agent | 查询已发布的康复指导及其关联文档，限定检索范围 |
| `ServiceCatalogApi` | mall | workorder、agent | 查询服务项目、套餐 |
| `CaregiverQueryApi` | provider | workorder、agent | 查询可派护理员、资质有效性 |
| `WorkOrderApi` | workorder | guard、agent、ops | 创建应急工单、查询进度 |
| `PayOrderQueryApi` | pay（已有） | mall、settle | 查询支付状态 |

### 领域事件

| 事件 | 发布方 | 监听方 | 处理 |
|---|---|---|---|
| `AssessmentCompleted` | profile | mall | 刷新推荐 |
| `VitalSignRecorded` | profile | guard | 判断是否触发预警 |
| `OrderPaid` | mall | workorder、settle | 生成工单；生成费用单 |
| `WorkOrderCompleted` | workorder | mall、settle、provider | 核销权益；生成结算单；累计评分 |
| `EvaluationSubmitted` | workorder | provider、ops | 聚合评分；质控线索 |
| `AlertTriggered` | guard | workorder、agent | 需要上门时创建应急工单；预警研判 |
| `QualificationExpired` | provider | workorder | 取消该护理员未开始的派单 |

一期没有跨域事件：`guard` 单向调用 `profile` 的 `api/`，预约确认后的提醒由 `mall` 调用 `guard` 的 `DeviceMessageApi` 下发（`mall` 依赖 `guard`，`guard` 不依赖 `mall`）。上表中的事件在二期加入 `provider`、`workorder`、`settle` 时启用；`AlertTriggered`、`VitalSignRecorded` 在二期需要创建应急工单和体征预警时发布。

实现约束：事件使用 Spring `ApplicationEvent`，业务相关监听放在事务提交后执行，沿用 `TodoService` 的提交后动作写法。事件载荷只放标识与必要快照，不放实体。具体机制在实现前对照 `module/document/event`、`module/system/org/event` 的现有写法确认，不新建第二套。

## 与现有模块的复用和边界

| 能力 | 复用 | 边界 |
|---|---|---|
| 支付 | `pay` 模块与 `engine/settlement` 渠道适配器 | `settle` 不接触渠道细节，只创建支付请求并监听结果 |
| 订阅与积分 | `billing`、`credit` | 与养老订单无关，不混用 |
| 佣金 | `brokerage` | 本期不涉及推广分佣，若后续需要再评估 |
| 组织与工作空间 | `system/org` | 服务商和站点绑定组织，不另建机构表 |
| 通知 | `InternalMessageSender` | 预警默认走站内通知；紧急预警是否同时发短信，待确认短信发送能力后再定 |
| 待办 | `TodoService.createSystemTodo` | 护理员待办、家属事项复用，来源指向工单 |
| 审批 | 审批流 | 资质审核、补贴方案发布等需要人工审批的环节使用 |
| 数字人 | `digitalhuman` | 作为陪伴形象，不在 `elder` 内重造 |
| 知识库、记忆、工作流 | `engine/knowledge`、`engine/memory`、AI 工作流 | 只提供种子数据与配置 |
| 统计 | `module/stats` 与统计视图 | `ops` 报表优先复用 |

## 智能层设计

### 工具

| 工具 | 所属域 | 说明 | 风险 |
|---|---|---|---|
| `queryElderOverview` | profile | 长者概况与评估等级，字段已脱敏 | 只读 |
| `queryVitalSigns` | profile | 最近体征与趋势 | 只读 |
| `listAvailableServices` | mall | 按评估等级和区域查可购服务 | 只读 |
| `draftServiceOrder` | mall | 创建订单草稿，需用户确认才提交 | 写，需确认 |
| `queryWorkOrderProgress` | workorder | 工单进度与护理员信息 | 只读 |
| `reportAlert` | guard | 上报预警，仅限已授权角色 | 写，高风险 |
| `queryCaregiverAvailability` | provider | 查询可派护理员 | 只读 |

一期工具：

| 工具 | 说明 | 风险 |
|---|---|---|
| `queryElderOverview` | 长者概况，字段已脱敏 | 只读 |
| `listAvailableServices` | 可预约服务 | 只读 |
| `draftServiceOrder` | 起草预约，用户确认才提交 | 写，需确认 |
| `queryAlertStatus` | 家属或运营查询预警进展 | 只读 |
| `listMedicationPlans` | 查询长者的用药计划与今日服药情况 | 只读 |
| `draftMedicationPlan` | 对话中起草用药计划，返回草稿摘要与草稿号；草稿存缓存，10 分钟过期，不建表 | 草稿，不生效 |
| `confirmMedicationPlan` | 用户明确确认后按草稿号创建计划，来源记为对话，并通知联系人核对 | 写，高风险 |
| `searchRehabGuide` | 在已发布康复指导所关联的文档范围内检索并回答，带引用与免责声明 | 只读 |
| `listRecentPhotos` | 家属查询长者最近的照片 | 只读 |

用药计划只通过对话创建和查询；修改、停用只在管理端由家属或运营操作，避免长者口误或语音识别错误导致提醒被误停。`queryVitalSigns`、`queryWorkOrderProgress`、`queryCaregiverAvailability`、`reportAlert` 随体征、工单、排班后置到二期。

一期不设"发起求助"工具：求助由机器人端本地识别后作为设备事件上报，不经过 AAF 工具调用，避免依赖大模型决策紧急动作。

### 角色

| 角色 | 使用者 | 可用工具 |
|---|---|---|
| 长者陪伴 | 长者（经机器人） | `listMedicationPlans`、`draftMedicationPlan`、`confirmMedicationPlan`、`listAvailableServices`、`draftServiceOrder`、`searchRehabGuide`；健康问答复用 `tool/medical`；不含任何预警写入工具 |
| 家属服务 | 家属（需授权） | `queryElderOverview`、`queryAlertStatus`、`listMedicationPlans`、`listRecentPhotos`、`listAvailableServices`、`draftServiceOrder`、`searchRehabGuide` |
| 运营助手 | 运营 | 查询类工具 |

角色、技能与工具关系通过 seed 声明，遵循 AAF-118 的 Role 可见性规则。该规则尚未落地，本设计不依赖它，工具授权先走现有 `ToolPolicy`。

### 约束

- 工具参数不得包含姓名、证件号、手机号、具体病史；对第三方服务的调用沿用 `ToolQueryGuard` 的拦截。
- 涉及健康的回复带免责声明；出现胸痛、呼吸困难、意识不清等词时直接建议就医，不等待检索。
- 写类工具必须有人确认；预警处理由 `guard` 内部流程驱动，不由大模型决定是否通知家属。
- 家属助手调用任何长者数据，都先经 ReBAC 校验授权。

## 机器人终端与视觉预警

### 一个后端模块能否管理

可以。机器人工程、后台功能架构图、视觉需求描述的是同一个系统的三个侧面，都归 `elder` 一个业务模块：

| 来源 | 内容 | 在 AAF 中的位置 |
|---|---|---|
| 机器人工程 `modbot` | 终端：RKNN 人体检测与姿态、跟踪、人脸、语音、3D 数字人、本机大脑 OpenClaw、本地提醒调度 | 终端侧，不进后端；后端只提供设备接入 API 并保存业务数据 |
| 后台功能架构 PDF | 业务服务层、核心 AI 能力层、基础支持层 | 业务服务层对应 `elder` 的子域；核心 AI 能力层与基础支持层的大部分已由 `aaf-framework` 提供，不在模块内重做 |
| 视觉检查需求 | 5 个摄像头场景 | `guard` 的预警事件类型，设备是机器人或固定摄像头节点 |

PDF 的 10 个模块逐项落位：

| PDF 模块 | 落位 | 期 |
|---|---|---|
| 多模态智能交互 | 机器人端采集与播放，AAF 提供语音识别、语音合成和助理对话；机器人原有本机大脑保留为断网兜底 | 一期（见"语音与助理接入"） |
| 用户画像 | `profile`（档案、偏好、禁忌）加框架记忆引擎；行为与健康画像后置 | 一期仅档案与偏好 |
| 知识库管理 | 框架 `engine/knowledge` 与 `module/knowledge`；一期用于康复指导 | 一期 |
| 数据集管理 | 训练与标注数据治理属于模型工程，不进业务模块 | 不在本故事 |
| 多智能体协同决策 | 框架 Team 与 Assistant 层 | 后置 |
| 健康监测 | `profile.VitalSign` 加 `guard`，依赖可穿戴与医疗设备 | 二期 |
| 情感计算 | 一期由机器人本地大脑的提示词承担，多模态情感后置 | 后置 |
| 设备管理 | `guard.Device`，覆盖机器人与摄像头节点；智能家居后置 | 一期仅机器人与摄像头 |
| 紧急响应 | `guard` | 一期 |
| 家属关怀 | `profile` 联系人与授权加 `guard` 通知；周报、视频通话、节日提醒后置 | 一期仅授权与通知 |

### 与 ModBot 通用设备服务的关系

AAF 已采纳的 [Qt 机器人通信设计](../../../design/apps/modbot/qt-robot-communication.md)（协议矩阵、MQTT 主题与信封、状态语义、安全）是设备通信的基线，尚未实现。本故事按该设计实现 `elder` 需要的部分，不另起一套协议。

`Device`、`DeviceMessage` 放在 `elder.guard` 内，与养老场景一起演进。将来需要通用机器人控制（移动、机械臂）时，把这两个实体和设备接入代码整体迁出为独立的 `modbot` 模块，`elder` 改为依赖它；迁移前不存在第二套设备登记，不构成并行抽象。

### 通信方案（一期实现范围）

下表对应通信设计里的协议矩阵，列出一期哪些链路实现、哪些不做。

| 链路 | 协议 | 一期 | 内容 |
|---|---|---|---|
| 设备 ↔ AAF 控制与遥测 | MQTT over TLS | 实现 | 在线状态、心跳、预警事件、提醒下发、配置下发、确认与结果 |
| 设备配对、简报 | HTTPS REST | 实现 | 配对、绑定、凭据轮换、机器人简报 |
| 照片等大文件 | HTTPS 上传到 AAF 文件存储 | 实现 | MQTT 不传文件正文 |
| 语音识别 | WebSocket | 实现 | 设备语音网关，PCM 入，文字出 |
| 助理对话 | SSE（与 `/agui/run` 同类的流） | 实现 | 文字流 |
| 语音合成 | HTTPS 流式响应 | 实现 | 音频流 |
| 家属端、运营端 ↔ AAF | REST，加站内信 WebSocket；预警进展可用 AAF-119 的全局 SSE | 实现 | 管理、查询、通知 |
| 实时音视频 | WebRTC | 不做 | 视频通话后置 |
| AAF ↔ Robot Edge Agent | A2A | 不做 | 没有机器人自治任务 |
| 模块总线、实体急停 | Cyphal/CAN-FD、硬件线路 | 不做 | 一期没有模块与执行器 |
| 本机进程间 | `QLocalSocket`、signal/slot | 机器人内部 | 沿用 |

语音识别和对话属于 AAF-119 保留的 WebSocket 与 AG-UI SSE 两类通道，不新增通道类型。

### 按场景划分：哪些走 MQTT，哪些走 REST

划分原则：小而频繁、需要可靠投递和在线状态的语义消息走 MQTT；需要请求应答、携带用户身份与计费上下文、或体积大、或是流式的走 HTTPS、WebSocket、SSE。文件和音频不进 MQTT，MQTT 只传摘要和引用。

| 场景 | 通道 | 说明 |
|---|---|---|
| 设备上线、离线（遗嘱） | MQTT `up/presence` | retained |
| 心跳：摄像头状态、隐私模式、`lastPersonSeenAt` | MQTT `up/status` | 30 秒一次 |
| 跌倒疑似、一键求助、确认无恙 | MQTT `up/alert` | QoS 1，设备本地排队重试 |
| 用药提醒、预约提醒下发 | MQTT `down/command` | 带有效期，签名 |
| 撤销提醒 | MQTT `down/cancel` | |
| 阈值与开关变更 | MQTT `down/config` | 设备启动时的当前配置从 `brief` 取，MQTT 只用于运行期变更 |
| 设备收到、执行结果（已服、跳过、稍后） | MQTT `up/ack`、`up/result` | |
| 设备能力与固件版本 | MQTT `up/capabilities` | retained |
| 设备配对：申请配对码、轮询绑定结果 | REST | 绑定成功时一次性返回凭据与 MQTT 接入参数 |
| 机器人简报、启动时的当前配置 | REST | 请求应答式 |
| **拍照上传** | **REST，multipart 上传** | 见下 |
| 语音识别 | WebSocket | PCM 流 |
| 助理对话 | SSE | 文字流 |
| 语音合成 | HTTPS 流式响应 | 音频流 |
| 运营管理端的一切操作 | REST | 管理端 JWT |
| 向家属、运营推送通知 | 站内信 WebSocket、服务号、短信 | 不经过设备通道 |

拍照上传：机器人拍照后，把图片通过 `POST /api/device/photos`（multipart，设备凭据鉴权，带 `clientPhotoId` 幂等）上传到 AAF，服务端校验后存入文件存储，写一条 `ElderPhoto`。上传结果由 HTTP 响应告知，不需要再发 MQTT 消息。设备端把照片先放进本地持久化队列，断网时排队，恢复后重试。一期不做直传对象存储和断点续传，等出现大文件或弱网问题再加。

### MQTT 设备通道

沿用通信设计的主题、信封和状态语义，取 `elder` 需要的子集。

主题（`tenantKey` 与 `deviceKey` 是系统生成的不透明键，只能由可信路由器生成，业务代码、工具、大模型都不能构造主题）：

| 方向 | 主题后缀 | QoS | retained | 一期用途 |
|---|---|---|---|---|
| 下行 | `down/command` | 1 | 否 | 提醒下发（用药、预约） |
| 下行 | `down/cancel` | 1 | 否 | 撤销尚未执行的提醒（计划被停用、预约被取消） |
| 下行 | `down/config` | 1 | 否 | 阈值与开关下发 |
| 上行 | `up/presence` | 1 | 是 | 上线与离线（遗嘱消息），含 `bootId` |
| 上行 | `up/status` | 1 | 否 | 心跳：摄像头状态、隐私模式、`lastPersonSeenAt` |
| 上行 | `up/alert` | 1 | 否 | 语义预警事件：`FALL_SUSPECTED`、`HELP_REQUEST`、`CONFIRMED_OK` |
| 上行 | `up/ack` | 1 | 否 | 设备已持久化并接受指令 |
| 上行 | `up/result` | 1 | 否 | 提醒结果：已服、跳过、稍后、无响应 |
| 上行 | `up/capabilities` | 1 | 是 | 设备能力与固件版本 |
| 上行 | `up/telemetry` | 0 | 否 | 一期不用，保留给体征 |

下行指令信封沿用通信设计，字段为 `specVersion`、`messageId`、`messageType`、`commandId`、`correlationId`、`traceId`、`targetDeviceKey`、`issuedAt`、`expiresAt`、`sequence`、`command{capability, action, schemaVersion, arguments}`、`governance`、`integrity{keyId, algorithm, signature}`。一期的能力只有 `reminder.show` 与 `config.update`，参数严格校验，未知能力、版本、字段一律拒绝；用 RFC 8785 规范化 JSON 后以 Ed25519 签名，设备校验签名、目标、有效期与序号。上行信封增加 `bootId`、`statusSequence`、`deviceOccurredAt`、`payloadHash`。

与通信设计的差异：`controlMode`、`leaseEpoch`、`fenceToken` 一期不实现。它们保护的是移动与机械臂这类互斥物理资源，提醒和配置不涉及，也没有 A2A 与 `DIRECT_MQTT` 两种模式并存。任何物理能力启用前必须补齐，并重新做安全审核。

语义规则（取自通信设计）：

- 只收到 PUBACK 时消息只能是 `DISPATCHED`，设备持久化并确认后才是 `ACCEPTED`。
- 下行消息状态：`QUEUED → DISPATCHED → ACCEPTED → SUCCEEDED / FAILED`，另有 `EXPIRED`、`CANCELLED`、`DELIVERY_UNKNOWN`。没有收到设备确认时是"送达未知"，不能显示为已提醒。
- 提醒属于"可延迟、可幂等、低风险"，离线策略为 `QUEUE_UNTIL_EXPIRES`：只在有效期内等待，过期不再下发，并记录为漏提醒。
- 同一 `commandId` 绑定同一内容，不同内容返回幂等冲突；设备重复收到只执行一次。
- 旧 `bootId` 或旧 `statusSequence` 的上行消息不更新当前状态。
- 单条消息上限 64 KiB，媒体和文件不进 MQTT。
- 在线状态：连接时发布 retained 在线，遗嘱预设离线；心跳 30 秒，连续两个周期缺失为 `SUSPECT`，三个周期为 `OFFLINE`，后者触发设备离线预警。

可靠性实现：

- 出站：`DeviceMessage` 就是 outbox，与业务变更同事务写入；`MqttOutboxRelay` 负责发布，状态由 PUBACK、`up/ack`、`up/result` 推进。
- 入站：靠唯一约束和单调序号做幂等（预警 `(deviceId, eventId)` 唯一，确认与结果按 `messageId` 幂等更新），不单独建 inbox 表。

认证与权限：

- 每台设备在配对时获得唯一的 MQTT 凭据，禁止共享密钥；TLS 1.3，最低 1.2；broker 支持时优先 mTLS。
- 设备只能订阅自己的 `down/*`，只能发布自己的 `up/*`，由 broker 的认证与 ACL 回调到 AAF 判定；吊销绑定时同时撤销凭据并踢掉连接。
- 签名私钥保存在 AAF 的密钥管理中，设备预置对应公钥并支持轮换。

Broker 与客户端库：

- 技术选型文档写明 IoT 设备接入为"阿里云 IoT Platform 加 Spring Integration MQTT"，这个选型已不能用：阿里云物联网平台自 2025 年 2 月 1 日起不再支持新购，官方文档标为"文档停止维护"；而且它的服务端接收走 AMQP 消费组、下发走 HTTPS `Pub` 接口，并不是 MQTT，也与通信设计里"PUBACK 对应已下发"的语义和自管主题、ACL 回调不一致。若团队账号下已有存量实例，是否可用要向阿里云确认，但不建议作为新建方案的基础。技术选型文档该行需要更新。
- 仓库目前没有任何 MQTT 依赖。通信设计要求开发前先做 PoC，固定 MQTT 版本、客户端库、X.509、离线队列、retained、消息大小和共享订阅能力。因此把通信基础作为一期的 M0：只有一台测试设备，不做压测。
- broker 的高可用要求与 Java 服务同级：预警是 7x24 的安全功能，但设备端有本地持久化队列，broker 或服务端短暂不可用时事件会排队重试。如果 Java 服务本身是单实例，同机单节点 broker 不会增加新的单点；将来 Java 服务多实例部署时，再重新评估 broker 的高可用。
- 一期部署形态：EMQX 单节点，与 Java 服务同一台服务器，但作为独立的容器或进程运行，不嵌入 Java 进程。理由：设备规模预计在数百到数千台，负载很小（见下），阿里云托管版按预留量收费，不用也要付费；独立运行可以单独设内存上限、单独重启，部署 Java 服务时不会断掉所有设备的连接。
- 资源估算（工作量算术，不是实测）：每台机器人约每 30 秒一条心跳，预警和提醒每天寥寥数条。1000 台约 33 条消息每秒，10000 台约 330 条每秒，连接数等于机器人数。这个量级对任何 MQTT broker 的 CPU 都不是问题，EMQX 官方宣称的是单节点百万级每秒。真正需要关注的是内存基线、每个持久连接的会话状态和 TLS 缓冲、以及 broker 重启后所有设备同时重连造成的 TLS 握手尖峰。EMQX（Erlang 运行时）空载内存我预期在几百 MB 量级，Mosquitto（C）在几十 MB 量级，Java 实现的 broker 通常更耗内存；这是经验判断，官方没有给出这个规模的数字，一期只有一台测试设备，不做压测；以下估算仅供将来扩容参考，扩容前再用目标设备数实测常驻内存、重连尖峰的 CPU 与耗时。
- 同机部署的注意事项：给 broker 容器和 JVM 堆分别设内存上限，避免互相拖垮；broker 数据目录放在有空间的盘；8883 端口对公网开放时配安全组与连接限速；TLS 证书的续期纳入运维。
- 自管 EMQX：5.9 起改为 Business Source License 1.1（源码可见，不是开源协议）。官方的附加使用授权是：可以对 EMQX 的单个节点做生产使用，前提是不把它以托管或嵌入方式提供给第三方；每个版本发布四年后自动转为 Apache 2.0。影响有三点：一是单节点生产可用，单节点是否构成新的单点取决于 Java 服务本身是否多实例；二是做集群高可用需要商业许可；三是不能把 EMQX 随 AAF 一起打包交付给客户，否则可能构成"嵌入方式提供给第三方"，只能把它作为客户自行部署的外部依赖。这是我根据官方许可 FAQ 摘要的理解，不是法律意见，使用前需要法务确认。
- 自管其他候选：HiveMQ CE、Mosquitto、NanoMQ 许可更宽松，但我印象中社区版没有集群，认证扩展方式也不同，需要 PoC 核实。
- 云上托管：阿里云"云消息队列 MQTT 版"是官方文档在维护的产品（物联网平台的页面带"文档停止维护"标记，它的搜索结果标题里没有）。我从官方文档确认的信息：全托管，不支持私有化部署；实例创建后同时生成公网和内网接入点；兼容 MQTT 3.1.1，MQTT 5.0 下支持遗嘱消息和共享订阅（存量专业版需提工单开通）；支持设备级权限控制、临时 Token 服务和 TLS 1.2；可与云消息队列 RocketMQ 版互通。文档同时警告：用 paho-mqtt v2、mqtt.js 等第三方开源库连接 Serverless 版实例时，可能因 Keep Alive、Clean Session 等参数配置差异被断开，建议使用官方推荐 SDK 或按终端开发指南调整参数。还有一个坑：接入点域名解析依赖底层 RocketMQ 服务的开通状态，未开通时即使实例显示正常也无法解析。
- 托管服务（当前不采用，仅留档）对本设计的适配度未核实，若将来重新评估，需逐项确认：每台设备如何拿到独立凭据（临时 Token 服务意味着 AAF 要代设备申请 Token）、主题授权能否限制到单设备、设备上下线事件如何送到后端、后端如何发布和订阅（直接做 MQTT 客户端，还是经 RocketMQ 互通）、机器人用的 QtMqtt 能否稳定连接、实例规格与计费（以阿里云官方价格页为准）。
- 托管与自管的取舍：托管不用装机、不用自建高可用，但不支持私有化——如果 AAF 要交付给客户私有部署，客户环境需要自己的阿里云账号，或者换成自管 broker；据业务方反馈，托管版按预留量收费，不用也要付费（计费我没有核实，以阿里云官方价格页为准）。在设备规模小的一期，固定的预留费用不划算，所以降为备选。
- 其他厂商的托管 MQTT 服务是否可选，PoC 时一并调研；无论选哪个，生产只启用一个适配器，不做双写，本地与 CI 使用测试 broker。
- 一期的 broker 为 EMQX 单节点，与 Java 服务同机独立运行；一期只有一台测试设备，不做压测。需要的工作是：部署 EMQX、引入客户端依赖并加入 `aaf-dependencies` 统一管理版本（需验证与 Spring Boot 4 兼容）、让机器人用的 QtMqtt 能稳定连接、提供无头设备模拟器。
- 业务代码只依赖 `DeviceMessagePublisher` 与入站处理接口，MQTT 细节封装在 `guard` 的适配包内。
- 无头设备模拟器用于验收：重复投递 100 次只执行一次、过期、PUBACK 不等于已接收、旧 `bootId` 不覆盖新状态、跨设备主题被拒绝。

机器人工程已有 `MqttClientAdapter`（QtMqtt，构建开关 `MODBOT_ENABLE_MQTT` 默认开、连接开关默认关）和 `aaf.mqttHost / mqttPort` 配置，可直接接入。通信设计属于高风险设计，涉及设备身份、证书与签名，进入开发前需要完成人类架构与安全审核。

### 认证方案与设备认证

运营管理端沿用现有认证：运营用自己的账号登录（密码、验证码或企业微信等），带 JWT 访问管理端，授权走角色与 CRUD 的 L1 至 L4。需要新增养老模块的权限码、"养老运营"角色种子和"运营值班"角色，用于接收预警。

运营 7x24 值班用一个默认的统一服务账号：种子创建一个用户（如 `elder-duty`），挂"运营值班"角色，密码不写入种子，由部署管理员首次设置。预警的"通知运营值班"就是给拥有该角色的用户发站内信，当前只有这一个账号；值班人员轮流使用该账号登录管理端盯预警列表。因为是共用账号，系统分不清是谁处理的，处理预警时要求在结论备注里写明处理人。排班、多人值班、升级轮转不在一期做，将来增加账号即可，通知逻辑按角色发送，不需要改。

设备不是用户，需要独立的设备认证：

- 新增 `DeviceAuthFilter` 和一条独立的 `SecurityFilterChain`，只匹配 `/api/device/**`，优先级高于主链，放在 `aaf-api` 的 `guard` 内，不修改 `aaf-framework` 的 `SecurityConfig`。
- 请求带设备标识与密钥，服务端按 SHA-256 哈希比对；沿用 `ApiKey` 的做法：只存哈希、保存展示前缀、记录最近使用时间。
- 认证通过后的主体是设备，只授予 `ROLE_DEVICE`，不继承任何用户角色；设备所有者 ID 只作为数据上下文，用于计费，通过 `PermissionExecutionService.runAsOwner` 调用积分与助理。
- 不复用 `ApiKey`：`ApiKeyAuthFilter` 会授予绑定用户的全部角色，`ApiKeyScopeFilter` 只按路径第一段业务名收窄资源，不能阻止同一个 Key 调用所有者在 `/api/elder/**` 下的管理类接口。设备一旦被盗，等于盗用了所有者的权限。
- 设备的 WebSocket（语音识别）也走这条链，不经过 `/ws/**` 与 `JwtHandshakeInterceptor`。
- MQTT 的认证与主题授权由 broker 回调到 AAF 的内部端点判定，端点只接受 broker 来源并校验共享密钥，具体方式在 PoC 中随 broker 选型确定。

### 设备配对与绑定

设备不需要登录。机器人和长者都不是 AAF 用户账号：机器人用设备凭据，长者通过机器人使用，运营用自己的账号登录并负责绑定。家属端一期不做，所以一期绑定由运营在管理端完成，家属端上线后再开放家属自助绑定。绑定采用"屏幕配对码"流程：

1. 机器人未绑定时，调用 `POST /api/device/pair/start`，提交硬件序列号、型号、固件版本（该接口无需登录，按序列号和来源限流）。AAF 返回 `pairCode`（8 位易读字符）、不可猜的 `deviceCode`、有效期 10 分钟，机器人在屏幕上显示二维码和配对码。
2. 已登录的运营在管理端"设备"页新建设备，填写配对码、选择长者和设备所有者，提交。这就是创建 `Device` 资源，绑定是创建的副作用，不需要自定义动作。后端校验调用人有权管理该长者，配对码有效且一次性使用，序列号未被绑定。设备所有者必须由运营明确指定为一个用户账号（通常是出钱的家属账号），不默认取运营本人；所有者是计费主体。
3. 机器人轮询 `POST /api/device/pair/poll`（带 `deviceCode`），绑定成功后一次性收到 `deviceKey`、设备密钥、MQTT 接入参数、API 基地址。密钥只返回一次，机器人存入安全存储。
4. 机器人用凭据连接 MQTT，发布在线状态和能力，进入工作状态。
5. 解绑：运营在管理端对设备执行"解绑"动作（无参动作，需确认），撤销凭据并踢掉连接；机器人收到认证失败后回到配对界面，重新配对即可换新凭据。一期不做单独的凭据轮换。

规则：

- 配对码单次使用，失败 5 次锁定，配对会话存 Redis 并设过期，不建表。
- 设备能被绑定，靠的是"在设备屏幕上看到配对码"这一物理接近证明，序列号本身不作为凭据。
- 一个设备同时只绑定一位长者；换绑必须先解绑。
- 设备密钥库中只存哈希。
- 设备所有者记录在 `Device`，也是计费主体，见"语音与助理接入"。

### 设备 REST 与流式接口

这些接口用设备凭据鉴权，不使用用户 JWT。

| 方向 | 接口（建议） | 内容 |
|---|---|---|
| 下行 | `GET /api/device/brief` | 机器人简报：称呼、偏好、禁忌、今日提醒；不含证件号、详细病史 |
| 上行 | `POST /api/device/photos` | 照片上传（multipart），见"相册管理" |
| 双向 | `WS /api/device/voice/session?proto=3` | 实时语音会话（ASR → 助理 → TTS 同一条连接），协议见 modbot 仓库 `docs/aaf-device-integration.md`；取代原 `WS /api/device/voice/asr` |
| 下行 | `POST /api/device/voice/tts` | 语音合成，返回流式音频 |
| 双向 | `POST /api/device/chat` | 与助理对话，流式返回文字 |

预警与心跳不再走 REST，只走 MQTT 的 `up/alert` 与 `up/status`；一期不保留 REST 兜底，避免双路径。

预警事件载荷：

```json
{
  "eventId": "uuid，设备生成，用于幂等",
  "type": "FALL_SUSPECTED | HELP_REQUEST | CONFIRMED_OK",
  "occurredAt": "设备时间，ISO 8601",
  "zone": "LIVING_ROOM | BEDROOM | BATHROOM_DOOR | ENTRANCE | OTHER",
  "confidence": 0.91,
  "payload": {}
}
```

规则：

- 离线补报：设备断网时本地排队，恢复后带原 `occurredAt` 补报。后端对晚到超过阈值的预警标记 `LATE`，仍然通知，不因晚到而丢弃。
- 校验：事件大小、字段枚举、频率限制；未知类型拒绝并记录。
- 不接收监控视频、预警现场画面、人脸特征；照片只接收长者主动或经授权的自动拍照产生的单张图片，见"相册管理"。

### 队列与定时

AAF 已有任务队列，不需要为通知再用 Redis 实现一套消息队列：框架的 `AsyncQueueTaskService` 在 PostgreSQL（`sys_async_task`）保存任务主状态，`RedisStreamTaskQueue` 提供按优先级的 Redis Stream、延迟 ZSet、重试和死信，设计见 [轻量任务队列设计](../../../design/apps/service/task-queue.md)，`TodoQueueService` 是使用示例。

`elder` 的用法：

| 场景 | 做法 | 原因 |
|---|---|---|
| 发送预警通知 | 每条预警提交一个异步任务（如 `ELDER_ALERT_NOTIFY`），处理器向运营值班（站内信、钉钉）和联系人（站内信）逐渠道发送并写响应记录 | 隔离外部渠道的慢与失败，利用现有重试与死信 |
| 确认窗口 | `AlertEvent.nextActionAt` 加定时扫描（`@Scheduled` 加分布式锁），状态驱动、幂等 | `AsyncQueueTaskService.submit` 没有延迟参数；状态驱动更容易恢复和测试，重启不丢 |
| 用药提醒到点 | 同样按 `MedicationPlan` 的下一次时间点扫描，生成 `DeviceMessage` | 同上 |
| 设备下行消息 | `DeviceMessage` outbox 加 `MqttOutboxRelay` | MQTT 是设备传输通道，不是通用消息队列 |

异步任务需要 `ownerId`，系统触发（MQTT 入站、定时扫描）时使用设备所有者作为任务所有者，并通过 `PermissionExecutionService.runAsOwner` 执行，与 `TodoQueueService` 一致。

Redis 的其他用途限于：配对会话、用药计划草稿（均带过期）、设备在线状态投影、限流。事实源始终是 PostgreSQL，Redis 丢失不影响正确性。

### 视觉预警场景

视觉原始画面不出设备。边缘设备把检测结果判定为语义事件后上报，后端只处理事件。

| 场景 | 事件类型 | 边缘侧判定 | 后端处理 | 级别 | 所需设备 | 期 |
|---|---|---|---|---|---|---|
| 跌倒检测与报警 | `FALL_SUSPECTED` | 机器人已有 `fallCandidate`（姿态得分达阈值并连续多帧）；需增加"倒地后停留时长"，并先本地语音询问 | 创建预警，等待确认窗口，超时通知联系人 | 紧急 | 机器人 | 一期 |
| 一键求助 | `HELP_REQUEST` | 语音或按键触发 | 创建预警，直接通知 | 紧急 | 机器人 | 一期 |
| 长时间无活动 | `NO_ACTIVITY` | 无事件，边缘在心跳里带 `lastPersonSeenAt` | 后端定时检查，仅在设备在线、非隐私模式、清醒时段评估 | 高 | 机器人 | 一期 |
| 设备离线 | `DEVICE_OFFLINE` | 无，由后端根据 MQTT 在线状态判定（遗嘱消息或心跳缺失） | 创建预警并暂停该设备相关的无活动判定 | 高 | 机器人 | 一期 |
| 夜间离床与徘徊 | `NIGHT_OUT_OF_BED` | 卧室区域摄像头：夜间离开床区或出现在非卧室区域 | 事件转预警 | 中，持续则升高 | 卧室、走廊摄像头节点 | 二期 |
| 卫生间滞留超时 | `BATHROOM_OVERSTAY` | 门口摄像头：人体存在计时超过阈值，摄像头装在门外，不拍内部 | 事件转预警 | 高 | 卫生间门口摄像头节点 | 二期 |
| 入户人员识别 | `STRANGER_AT_DOOR` | 人脸或人体识别，区分已知与陌生 | 通知家属 | 中 | 入户门摄像头节点 | 三期，需单独隐私评估 |

说明：

- 夜间离床、卫生间、入户三项依赖固定摄像头，机器人本体不能覆盖。同一套边缘感知软件可部署为"摄像头节点"，以 `Device.kind` 与 `Device.zone` 区分，不需要新的后端实体。
- 机器人的人脸识别目前是实验性能力，准确率与隐私保护未完成评估，入户识别因此放最后。
- 无活动判定依赖心跳可信：设备离线、摄像头被遮挡或进入隐私模式时必须暂停判定，否则会误报。
- 阈值默认值（待业务方确认）：本地询问窗口 30 秒；后端确认窗口 60 秒；无活动 4 小时，清醒时段 07:00 至 22:00；卫生间滞留 30 分钟。一期用全局配置，不按长者覆盖。

### 语音与助理接入

机器人把语音识别、对话、语音合成都交给 AAF 助理，原有本机大脑保留为断网兜底。链路：

```text
机器人麦克风 → VAD 切话轮 → WS 流式 ASR → 文字
  → POST chat（长者陪伴助理，注入机器人简报，限定工具集）→ 文字流
  → POST tts → 流式音频 → 机器人播放
```

- 现有 `/ws/asr`、`/agui/run` 用用户 JWT 鉴权，不接受设备凭据；TTS 没有对外直连接口。设备不是用户，直接让现有端点接受设备凭据会扩大全局认证面。因此在 `guard` 内建一个设备语音网关：用设备凭据鉴权，内部委托给 `SpeechService` 和助理执行入口，现有端点保持不变。
- 网关不写死角色：由设备所属场景的 `DeviceScenario` 提供默认角色，实际角色按"设备级角色覆盖 → 所有者默认助理的默认角色（AAF-118）→ 场景默认角色"依次解析，每一级都要通过可见性、绑定与场景允许集合校验；注入机器人简报与场景变量，只开放该角色的工具集；预警写入不在其中。养老场景的默认角色为"长者陪伴"。
- 回复文本的前后处理由场景包提供：养老场景在对话前拦截紧急词（胸痛、呼吸困难、意识不清等）并触发一键求助，直接建议就医，不等待检索；健康相关回复追加免责声明。其他场景不套用这些规则。
- 积分计费：计费主体是设备所有者（绑定设备的用户），语音识别、对话、语音合成都扣所有者的积分，通过 `PermissionExecutionService.runAsOwner` 以所有者身份调用现有的积分预检与结算；家庭共享、机构账户后续再考虑。所有者积分不足时网关返回明确的错误码，机器人回退到本机大脑，并通知所有者充值。
- 助理的程序化调用入口：现有对话执行统一走 `/agui/run`，网关能否在进程内直接调用其执行服务，实现前需要核对；若不能，退一步由网关内部以服务身份调用同一入口。
- 与 backlog 中尚未完成的 AAF-046（语音交互）有重叠：一期只交付设备侧的识别、对话、合成三个出口，不做 AAF-046 的 Whisper、实时对话模式等范围。
- 机器人端仍保留 VAD、唤醒、回声消除和播放控制，语音编解码协议沿用 16 kHz 单声道 PCM。

### 通知渠道

预警和漏服通知分运营侧和联系人侧，一期同时发出，不做逐级升级，每个渠道的结果都写入响应记录。

| 接收方 | 渠道 | 一期 | 说明 |
|---|---|---|---|
| 运营值班 | 站内信 | 开 | `InternalMessageSender`，发给默认值班服务账号（拥有"运营值班"角色的用户） |
| 运营值班 | 钉钉群机器人 | 开 | 复用现有 `DingtalkChannelSender`：`messageService.send(MessageRequest.direct(MessageChannel.DINGTALK, 标题, Markdown 正文, List.of("all")))`，与新用户注册通知的做法一致，不需要模板 |
| 联系人 | 站内信 | 开 | 联系人有 AAF 账号时才有；离线只有下次打开才可见 |
| 联系人 | 微信服务号模板消息、小程序订阅消息 | 后置 | 绑定微信的入口属于家属端 |
| 联系人 | 短信 | 预留，默认关闭 | 框架已有发送能力，缺厂商审核通过的短信模板。开关 `aaf.elder.alert.sms.enabled`，开启前必须先申请并审核模板 |

规则：

- 预警触达以运营值班为准：站内信和钉钉同时发，值班人员在管理端认领，并电话联系联系人。
- 钉钉用现有发送器，配置 `aaf.messaging.dingtalk.api-key`（群机器人 access_token）和可选的加签密钥 `secret`。它是单个群机器人，忽略收件人，适合做值班群；与新用户注册通知共用同一个机器人和群，需要分开时再扩展，一期不做。
- 钉钉自定义机器人如果配置了关键词、加签或 IP 白名单，消息正文必须满足；有发送频率限制，具体以钉钉官方为准。
- 钉钉发送是异步的，不返回送达确认，失败只记日志，所以它不是唯一渠道，站内信同时发。
- 钉钉文案只含预警编号、类型、级别、时间和长者称呼，不含住址、电话、病史，详情到管理端查看。
- 渠道失败、联系人无账号都写入响应记录，便于运营排查；一个渠道失败不影响其他渠道。
- 短信走 `MessageService`，使用异步 `send`；开关关闭时不发送。
- 家属端一期不做：联系人只能被动收到站内信，需要登录现有 AAF 网页才看得到，离线不可见。所以一期的预警实际依赖"运营值班加电话联系"，短信对可用性仍然重要，建议尽快申请短信模板。

### 相册管理

- 机器人已有本地相册、手动拍照和受控自动拍照（需管理员允许、用户开启、可见倒计时）。这些照片上传到 AAF，由家属查看和管理。
- 上传：`POST /api/device/photos`，设备凭据鉴权，带 `clientPhotoId` 做幂等。支持格式与单张大小设上限，服务端校验文件头，不信任扩展名。文件通过 `FileRecordApi` 存入 `MASTER` 存储，`ElderPhoto` 保存文件引用，并 `retain` 引用计数。
- 管理：一期由运营在管理端查看、添加说明、删除；删除走软删除，同时 `release` 文件引用并请求删除原文件。长者本人通过机器人只能看，不能在 AAF 里删除。家属查看随家属端后置。
- 家属可见性：家属端上线时再加字段，一期不预留。
- 原图只允许授权人通过受控 URL 访问，不生成永久公开链接，不使用 `PUBLIC_ASSET` 存储。
- 上传范围：只接收用户主动拍摄或经授权的自动拍照。预警现场画面、持续监控帧不上传，也不进相册。
- 同步回机器人（家属分享照片到机器人待机相册）一期不做，后续可复用 `DeviceMessage`。
- 留存与容量：一期不限制总容量与保留期限，成本增长后再定。仍保留单张图片的格式与大小上限，这是技术安全限制，不是容量策略。

### 康复指导

- 内容在知识库管理：运营把康复文档上传到知识库（`module/knowledge`），`RehabGuide` 保存标题、分类（字典）、摘要、适用人群说明，并关联一个 `KnowledgeDocument`，发布状态为草稿或已发布。关联文档的存在性在保存时校验。
- 查看：家属和长者可查看已发布的康复指导列表和详情，详情展示摘要与关联文档预览。
- 问答：`searchRehabGuide` 在已发布指导所关联的文档范围内检索（限定范围使用 `KnowledgeAccessScopePort` 或检索请求的范围参数，实现前核对），回答带引用，并固定附"仅供康复知识参考，不替代医生或治疗师的个体化指导"。禁止给出诊断、处方、具体剂量与个体化训练强度。
- 机器人上长者问康复问题，也走助理加此工具；出现急症词时按健康问答的规则直接建议就医。
- 内容责任：一期不设审核流程，运营发布即生效；系统不生成康复方案。

### 预警处理流程（以跌倒为例）

1. 机器人检测到 `fallCandidate`，本地语音询问"您还好吗"，同时通过 `up/alert` 上报 `FALL_SUSPECTED`。
2. 后端创建 `AlertEvent`（`TRIGGERED`），把 `nextActionAt` 设为当前时间加 60 秒；定时扫描到期的预警并推进状态，状态存库，重启不丢。
3. 长者回答没事：机器人上报 `CONFIRMED_OK`，预警进入 `CANCELED`。
4. 窗口超时（确认窗口 60 秒，已确认）：进入 `NOTIFYING`，同时向运营值班（站内信加钉钉群）和联系人（站内信）发出通知，渠道按"通知渠道"一节执行，每个渠道的结果写入响应记录。
5. 运营值班收到通知后，在管理端认领，电话联系联系人确认，并代记"已知晓"，进入 `ACKED`（家属端上线后，联系人可自助确认）。一期没有自动逐级升级。
6. 处理人填写结论，进入 `RESOLVED` 或 `FALSE_ALARM`。

系统不自动拨打急救电话。通知文案提示联系人必要时拨打 120。预警是辅助手段，不能替代人工看护，产品文案和协议需明确这一点。

### 用药提醒流程

1. 家属或运营在管理端维护 `MedicationPlan`（药名、剂量说明、时间点），长者也可以通过对话创建。药品名称与剂量由人提供，系统不提供用药建议。
   - 对话创建分两步：长者说"每天早上八点提醒我吃降压药"，助理调用 `draftMedicationPlan` 得到草稿摘要，向长者复述确认；长者明确说"对"，助理才调用 `confirmMedicationPlan`。草稿 10 分钟过期，未确认不生效。
   - 对话创建的计划来源记为"对话"，创建后立即生效，同时通知联系人核对药名与剂量。语音识别可能听错药名，核对是对这一风险的缓解，是否需要家属确认后才生效待业务确认。
   - 对话只能创建和查询，不能修改或停用。
2. `guard` 定时扫描到点的计划，通过 `MedicationApi` 读取，生成 `DeviceMessage`（能力 `reminder.show`）并经 MQTT `down/command` 下发给长者的机器人，有效期为到点后的确认窗口。
3. 机器人本地播报，长者确认后通过 `up/result` 回传"已服"；稍后提醒或超时未确认，回传"跳过""稍后"或"无响应"。没有收到设备确认的消息标记为"送达未知"，不记为已提醒。
4. 超过确认窗口仍无结果，`guard` 通过 `MedicationApi` 写入"漏服"记录；同一计划连续漏服达到阈值（默认 2 次，待确认）时，通知联系人。

### 机器人端改造项

机器人工程由我们自己维护，两边同步改造，改造范围如下：

- 启用 MQTT：`MqttClientAdapter` 已有，打开连接开关，实现主题订阅、信封校验与 Ed25519 验签、`bootId` 与 `statusSequence`、本地持久化的指令收件箱（先持久化再发 `up/ack`）、遗嘱消息与在线状态；同时启用 REST（配对、简报、照片）、WebSocket（语音识别）和 SSE（对话），`aaf.restEndpoint`、`mqttHost`、`mqttPort` 配置已有。
- 配对界面：未绑定时显示二维码与配对码，轮询绑定结果，收到凭据后存入安全存储；认证失败时回到配对界面。
- 把 `personPresenceStarted / Ended`、`fallCandidate` 映射为 `up/alert` 事件上报；补充"倒地后停留时长"判定与本地询问流程。
- 心跳（`up/status`）增加 `lastPersonSeenAt`、摄像头状态和隐私模式；隐私模式开启时上报，以便后端暂停无活动判定。
- 事件与照片都走本地持久化队列，带 `eventId` 或 `clientPhotoId` 重试。
- 新增 AAF 语音与对话后端：实现 `AafAgentBackend`（占位已有）和 AAF 语音提供方，经设备语音网关调用识别、对话、合成；保留本机 OpenClaw 与 AOQ 作为断网兜底，切换条件和降级提示由机器人端定义。
- 执行 `reminder.show` 指令，并通过 `up/result` 回传结果；执行 `config.update`。
- 使用 `brief` 作为对话的个性化输入。
- 照片上传：把现有相册的手动与自动拍照结果接入上传队列。

### 隐私与合规

- 监控视频、预警现场画面、人脸特征不上传；预警上行只有语义事件、置信度和区域。用户主动或经授权自动拍摄的照片可以上传到相册，且需要长者或监护人同意并留痕。
- 卫生间摄像头只装门外，不拍摄内部。
- 人脸识别默认关闭，启用前需独立隐私评估、长者或监护人书面同意、可删除。
- 每次摄像头与预警功能的启用记录同意人和时间。
- 事件与响应记录的保留期限待业务与合规确认。

## 运营管理端（一期前端范围）

一期只做运营管理端，家属端和长者端页面不做。管理端遵循仓库规则：`(workspace)/admin/**` 下的页面由 `sys_entity_def` 声明驱动，不手写页面。需要交付：后端 CRUD 资源定义与权限码、`db/seed/view/v{序号}__elder_entity_def.sql`、必要的少量 `customViews` 组件。

分组"养老"下的实体页面：

| 页面 | 内容 | 动作与说明 |
|---|---|---|
| 长者 | 档案列表与表单；记录详情下挂联系人、健康条目、用药计划、服药记录、照片 | 创建、编辑、删除；联系人优先级与授权范围在联系人表单维护 |
| 设备 | 设备列表与状态（在线、最近心跳、隐私模式、固件） | 创建即绑定（填配对码、长者、所有者用户）；动作：解绑（无参，需确认） |
| 预警 | 预警列表，默认筛选未结案并按级别、时间排序；详情展示响应记录时间线 | 动作：认领；代联系人记录已知晓；填写结论（已处理、误报）走更新表单；预警创建后向"运营值班"角色发站内信，值班人员能第一时间看到 |
| 用药计划 | 计划列表与表单；服药记录只读 | 动作：停用、启用 |
| 服务项目 | 项目列表与表单 | 动作：上架、下架 |
| 服务预约 | 预约单列表与详情 | 动作：确认、取消、标记完成 |
| 相册 | 照片缩略图列表 | 删除；添加说明 |
| 康复指导 | 指导列表与表单，关联知识文档 | 动作：发布、下线 |

说明：

- `EntityAction` 没有输入参数，所以需要输入的操作用创建或更新表单承载（绑定设备、预警结论、预约备注），不新增专用按钮或手写页面。
- 预警处置对时效最敏感，如果通用列表不够，再用列表级 `customViews` 做一个只读的"预警看板"，写操作仍走 `actions` 与表单。
- 管理端的权限以后端为准；新增权限码和角色种子，不依赖前端隐藏按钮。
- 家属端后置带来的影响：家属不能自助绑定设备、绑定微信、查看照片与预警、代为预约。一期这些由运营代办；家属只被动收到站内信。

## 数据与安全

- 敏感字段：证件号、手机号、地址、病史、体征。字段集分 `list`、`picker`、`detail`、`export`，敏感字段只在 `detail` 且有权限时输出。
- 访问控制：`profile` 使用 `PersonalScope`，按"长者本人、被授权家属、被派单护理员、运营"分层；家属与护理员的访问用 `elder:{id}#family` 与 `elder:{id}#caregiver` 关系表达，参考 `TodoService` 的 `share` 与 `relationRequirement`。
- 护理员的数据可见性随工单生命周期变化：派单后可见必要信息，工单完成后收回（二期）。
- 相册访问：原图经受控 URL，按联系人授权校验；下载与分享不生成永久公开链接。设备凭据只能上传照片，不能读取或列出。
- 导出与大屏：大屏数据脱敏聚合，不展示个人明细。
- 审计：预警处理、补贴变动、资质审核写审计。

## 数据库与权限

- 迁移文件编号：v200 至 v299 已登记给 Intelligent runtime，`device` 使用 v300 至 v399、`elder` 使用 v400 至 v499（均已登记）；schema 放 `db/migration/`、seed 放 `db/seed/`，各自单独成文件，按任务顺序取下一个编号；EntityDef 种子沿用 Entity Engine 的 v100 至 v199 号段，放 `db/seed/view/v109__elder_entity_def.sql`。号段规则只在所有环境可清库重建时成立（见 [改进意见](../../../prd/improvements.md) 2026-10-15 条）：开发期按项目约定用 `aaf.flyway.clean-on-start` 重建，可原地修改 `elder` 自己尚未发布的迁移文件；生产也保持顺序执行，不开启乱序（已决定）：已部署环境之后的变更必须取比已部署最大版本更大的编号，号段内编号此后不再保证连续。表前缀 `elder_`，分期追加，M2、M3 的表随对应任务新增文件。
- seed：权限码、字典、角色与技能种子分文件，EntityDef 按 `view/` 目录追加。
- 乐观锁：可编辑主实体使用 `version` 与 `expectedVersion`，沿用 `TodoService.requireExpectedVersion`。
- 软删除：主数据使用 `@SQLDelete`；流水类（补贴流水、响应记录）只增不改不删。

## 通用设备层与场景包

设备相关能力拆为独立业务模块 module/device，与具体业务场景无关；养老是第一个场景包，以后可以接入儿童教育、办公助理、情感陪伴等。

通用设备层（module/device）负责：设备配对与绑定、设备凭据认证（独立过滤链，路径 /api/device/**）、MQTT 设备通道（主题、信封、签名、Outbox、在线状态）、机器人简报、语音网关（ASR、对话、TTS）、设备消息下发接口。表为 device_info、device_message，资源键 device.device，权限前缀 device:device，错误码 11_000_000 ~ 11_099_999，管理端菜单组「设备管理」。

场景包通过 DeviceScenario 扩展点接入，由 DeviceScenarioRegistry 收集：

- 标识与角色：key、name、defaultRoleKey、allowedRoleKeys（空表示不限制，用户可自设默认角色）。
- 绑定主体：设备记录 scenarioKey、subjectType、subjectId，场景在 validateBinding 中校验主体与权限（养老场景的主体是长者）。
- 简报与对话：briefExtras 补充简报，chatVariables 注入助理变量，preChat 可拦截（养老的紧急词），postChat 可追加内容（养老的免责声明），openToolScope 打开工具上下文。
- 事件处理：场景自行实现 DeviceInboundHandler 处理自己的入站事件（养老的预警、用药确认），并通过设备消息接口下发自己的指令。

依赖方向只有 elder → device，device 不依赖任何场景模块。场景由配置 device.scenarios.enabled 控制启用，设备绑定与语音网关入口会校验；该配置目前读取的是全局系统配置，还不能按组织覆盖（需要组织级配置能力，待后续评估）。设备级可以覆盖角色（roleKey），当前场景记录在设备上，切换场景通过管理端修改并经配置下发通知设备。

### 多场景通用化决策

2026-10-16 核对时发现通用设备层仍有养老专用假设，以下决策使 `device` 对第二个场景可用，对应任务 #12021 至 #12027。

- 指令能力由场景声明：`DeviceScenario.commandCapabilities()` 返回该场景可下发的 COMMAND 与 CANCEL 能力名，设备消息入口按设备所属场景校验，未声明即拒绝；配置下发的 `config.update` 是通用能力，只允许 CONFIG 方向。养老场景声明 `reminder.show`。
- 按主体下发的目标设备类型由场景选择：`DeviceScenario.commandDeviceType()`，返回 null 表示不限类型，取主体名下最早绑定的设备；养老场景返回 `ROBOT`。通用层不再写死设备类型。
- 入站消息按场景分流：`DeviceInboundMessage` 携带 `scenarioKey`，`DeviceInboundHandler.supportsScenario` 默认处理全部场景，场景包的处理器必须覆盖并只处理自己的场景；分发器同时匹配消息类型与场景。处理器内部不再回查设备做场景过滤。
- 心跳与离线阈值走配置：`DeviceHeartbeatSettings` 统一读取 `aaf.device.heartbeat-seconds`、`suspect-after-seconds`、`offline-after-seconds`，简报下发与离线扫描共用，不在代码里写死 60 与 90 秒。
- 跨模块只走 `api` 与扩展契约：场景包只能引用 `device.api`（`DeviceQueryApi`、`DeviceMessageApi`、`DeviceInboundHandler`、`DevicePresenceChangedEvent`）、`device.scenario`（扩展点及其参数类型）、`device.security.DevicePrincipal`（扩展点方法的参数）、`device.ErrorCodeConstants` 与 `aaf-common` 的 `enums.device`。禁止引用 `device` 的 `domain`、`repository`、`service`、`mqtt`、`vo`、`mapper`。设备与设备消息通过 `DeviceView`、`DeviceMessageView` 只读视图暴露，消息收尾通过 `markCancelled`、`markExpired` 完成，状态机留在 `device` 内部。
- 设备模型保持当前形态：`Device` 保留摄像头状态、隐私模式、最近发现人员时间与区域，`DeviceZoneEnum` 保留居家区域。通用层定位为带麦克风与摄像头的室内机器人，覆盖养老、儿童教育、情感陪伴；出现办公等非居家场景时，再把区域改为字典驱动，不提前扩展。
- 迁移号段拆分：`device` 独占 v300 至 v399，`elder` 改用 v400 至 v499（项目未发布，原地改号）。`device` 的表、字典、配置、权限、菜单、EntityDef 各自成文件（`v300__device_schema.sql`、`v301__device_dict_seed.sql`、`v302__device_permission_seed.sql`、`view/v110__device_entity_def.sql`）。场景字典项与场景启用由场景包的种子追加：`device.scenarios.enabled` 默认空，养老种子追加 `elder`；养老角色对设备的授权与数据范围写在养老种子里，`device` 种子只授权管理员角色。因为 `device` 号段小于 `elder`，场景包的种子总是后于设备层执行。
- 契约测试：用与养老无关的假场景验证能力校验与目标设备解析，防止通用层再引入场景假设。
## 错误码分段

| 子域 | 范围 |
|---|---|
| profile | `10_000_000 ~ 10_099_999` |
| provider | `10_100_000 ~ 10_199_999` |
| mall | `10_200_000 ~ 10_299_999` |
| workorder | `10_300_000 ~ 10_399_999` |
| guard（预警与用药提醒） | `10_400_000 ~ 10_499_999` |
| settle | `10_500_000 ~ 10_599_999` |
| ops | `10_600_000 ~ 10_699_999` |
| agent | `10_700_000 ~ 10_799_999` |
| album | `10_800_000 ~ 10_899_999` |
| rehab | `10_900_000 ~ 10_999_999` |

已核对现有代码，未使用 `10_` 开头的段。`3_` 开头仅有 `3_000_100`，与本设计无冲突。

## 测试要点

- 单元：每个子域 `Service` 的状态流转、校验、Patch 三态；`Resource` 的字段集与能力。
- 验收：`*AcceptanceTest.java` 覆盖需求文档中的 Gherkin 场景，由 tester 编写。
- 关键场景：MQTT 重复投递只执行一次、PUBACK 不等于已接收、消息过期、旧 `bootId` 不覆盖新状态、跨设备主题被拒绝、配对码过期与重复使用、凭据吊销后连接被踢、设备事件重复上报的幂等、设备离线与隐私模式下不误报无活动、确认窗口内取消预警、联系人逐级升级、漏服连续计数、家属授权撤销后的访问。
- 通信验收使用无头设备模拟器，不依赖真机。

## 设计决策（ADR）

| 决策 | 选择 | 原因 |
|---|---|---|
| 模块名 | `elder` | 简短，且包路径每层单词不重复 |
| 子域层数 | 一期两层（业务域层加智能层），二期之后启用五层加智能层 | 一期只有五个小子域，单一规则即可约束；七个子域齐备后再用分层表达依赖方向 |
| 一期支付 | 不做在线支付，预约后线下结算 | 业务方简化一期范围；支付与补贴一起在二期设计，避免拆两次实现 |
| 一期场景 | 基础信息、预警、用药提醒、陪伴聊天、服务预约、康复指导、相册管理；家属关怀融入 | 业务方先定 5 个核心场景，后追加康复指导与相册；康复指导与相册都做最小实现，各只有一个实体 |
| 一期预约 | 只做预约单，由运营线下安排服务，不含 `provider` 与 `workorder` | 控制一期规模；派单与上门闭环随二期加入 |
| 设备层与场景拆分 | 设备配对、认证、MQTT 通道、简报、语音网关归通用模块 `device`；养老业务（预警、用药、相册、预约、康复）归 `elder`，作为首个场景包通过 `DeviceScenario` 接入；依赖只有 `elder → device` | 设备能力要支持儿童教育、办公助理、情感陪伴等多场景；设备层不含任何养老语义，新增场景只需实现扩展点 |
| 设备通道 | 一期实现通信设计的 MQTT over TLS 子集：主题、信封、签名、状态语义、outbox；租约字段对非物理能力暂不实现；预警与心跳只走 MQTT，不保留 REST 兜底 | 业务方要求实现既有通信方案；单一路径，不做兼容层；机器人已有 QtMqtt 适配器 |
| 设备身份 | 设备不登录，屏幕配对码加所有者用户绑定，绑定后颁发每台设备唯一的凭据；一期由运营在管理端绑定 | 机器人和长者都不是用户账号；配对码证明物理接近；所有者也是计费主体；家属端后置 |
| 设备认证 | 独立的 `DeviceAuthFilter` 与 `SecurityFilterChain`，只授 `ROLE_DEVICE`，不复用 `ApiKey` | `ApiKey` 会继承所有者的全部角色，设备被盗等于盗用所有者权限；独立过滤链不改动共享的 `SecurityConfig` |
| Broker | EMQX 单节点，与 Java 服务同机、独立容器运行；阿里云云消息队列 MQTT 版（按预留量收费、不支持私有化）与阿里云物联网平台（已停止新购）不采用 | 一期一台测试设备，负载极轻；EMQX 的认证与授权可回调 AAF，改动最少；单节点生产使用符合 BSL 附加授权，作为外部依赖单独部署，不随 AAF 打包；Java 服务多实例后再评估 broker 高可用 |
| 前端范围 | 一期只做运营管理端（EntityDef 驱动），家属端和长者端页面后置 | 业务方定；管理端能覆盖建档、绑定、预警处置、预约确认、内容管理 |
| 通知与定时队列 | 复用 `AsyncQueueTaskService` 发送通知，用 `nextActionAt` 加定时扫描推进确认窗口与升级，不新建 Redis 消息队列 | 框架已有 PostgreSQL 加 Redis Stream 队列；状态驱动更易恢复与测试 |
| 视觉事件分工 | 边缘判定语义事件，后端不收预警画面；长时间无活动与设备离线由后端判定 | 视频不出设备；无活动依赖多设备与时段，放后端更易配置 |
| 陪伴聊天 | 一期对接 AAF 助理，经设备语音网关调用识别、对话、合成；本机大脑作断网兜底 | 机器人团队就是我们自己，后续本来要对接助理；网关避免让现有端点接受设备凭据 |
| 通知渠道 | 预警同时通知运营值班（站内信加钉钉群，复用现有发送器）和联系人（站内信），不做逐级升级；服务号、小程序后置；短信预留开关默认关闭 | 业务方要求预警触达后台账号并发钉钉；有运营电话跟进，逐级升级的计时与状态可以省掉，功能优先 |
| 相册 | 照片存 `system/file` 的 `MASTER` 存储，`album` 子域只保存引用与元数据 | 复用文件存储与引用计数，不新建存储，不使用公开存储 |
| 康复指导 | `rehab` 子域只保存索引，内容与检索复用知识库 | 不另建文档存储；问答复用框架 RAG |
| 紧急处置 | 不自动拨打急救电话 | 责任与误报风险，由联系人或运营判断 |
| 智能层位置 | 业务模块内的 `agent` 子包，不放 `aaf-framework` | 与养老强绑定，换业务系统无法复用，符合"强绑定放 module"的判断标准 |
| 同层通信 | 领域事件 | 避免同层循环依赖 |
| 服务商表示 | 复用组织加扩展表 | 复用多租户与成员体系 |
| 报表大屏 | 只读视图，不建实体 | 避免数据冗余与双真理源 |
| 支付 | 复用现有渠道，`settle` 不自建 | 遵循"优先已有模式" |

### 开发前的人类审核范围

协作规范要求：🔴 高风险设计必须由人类审核通过后才能进入开发。这里说的"安全审核"不是外部渗透测试，而是人类（架构负责人加熟悉安全的人）对设计文档的审阅，需要重点看这几项：

- 设备认证：独立的 `DeviceAuthFilter` 与过滤链、凭据只存哈希、配对码流程（有效期、一次性、失败锁定）、解绑后立即失效。
- 设备语音网关：用设备凭据而不是用户 JWT 调用语音识别、助理和语音合成，计费走所有者，是否会越权。
- MQTT 通道：每台设备独立凭据、主题授权限制到单设备、下行指令签名与私钥保管、设备端验签、重放与过期。
- 隐私：照片上传范围与同意留痕、钉钉与站内信文案不含敏感信息、预警画面不出设备。
- 对话创建用药计划：语音识别听错药名的缓解措施（复述确认、通知联系人核对）。

审核通过前不进入编码；审核产出是对本设计的批注或确认记录，放在 `docs/task/v0.13/AAF-120/` 下。

## 未决问题

设计已确认（2026-10-15），无阻塞开发的未决问题。以下是实现中需要核对的事项，在对应技术任务里处理：

- 助理能否在进程内被设备语音网关直接调用，实现前核对（#12016）。
- MQTT 客户端库与 Spring Boot 4 的兼容、机器人 QtMqtt 连接 EMQX 的参数（#12001）。
- 领域事件的具体实现机制，需要对照现有写法确认后再定（二期启用）。
- 子域英文名按 `profile`、`guard`、`mall`、`album`、`rehab`、`provider`、`workorder`、`settle`、`ops` 执行。
- 迁移文件编号：v200 至 v299 已登记给 Intelligent runtime，`device` 使用 v300 至 v399、`elder` 使用 v400 至 v499（均已登记）；schema 与 seed 各自单独成文件，EntityDef 种子沿用 v100 至 v199 号段的 `view/v109`，细节见 [tasks.md](tasks.md)。

已确认：

- 设计整体于 2026-10-15 通过人类审核，包括设备认证、设备语音网关、MQTT 通道与签名、隐私范围、对话创建用药的缓解措施。
- 运营管理端先做，家属端不做；代绑定时设备所有者必须指定为一个用户账号；运营 7x24 值班用一个默认统一的服务账号，一期不做排班。
- 预警同时通知运营值班（站内信加钉钉群，共用现有的机器人和群）和联系人站内信，不做逐级升级；确认窗口 60 秒；短信预留开关默认关闭。
- 计费主体为设备所有者用户；对话创建的用药计划立即生效并通知联系人核对；康复指导不设审核；相册不限总容量与保留期限；预警事件与响应记录一期不清理；阈值默认值（无活动 4 小时、漏服连续 2 次）按默认实现，全局配置可调。
- broker 使用 EMQX 单节点，与 Java 服务同机、独立容器运行；一期只有一台测试设备，不做压测与容量规划，不考虑私有化交付；EMQX 作为外部依赖单独部署，不随 AAF 打包。
- 下行指令的租约字段（`controlMode`、`leaseEpoch`、`fenceToken`）对提醒和配置这类非物理能力暂不实现，物理能力启用前补齐。
- 一期以实现功能为主，不提前扩展（不做凭据轮换、批量导入序列号、排班、逐级升级、直传对象存储）。
- 建议一期内部按四个里程碑交付：M0 通信基础；M1 基础信息、设备配对与 MQTT 通道、预警、用药提醒（管理端创建）和通知；M2 相册、服务预约；M3 助理与语音接入、对话创建用药、康复指导。任务拆分见 [tasks.md](tasks.md)。

## 变更记录

| 日期 | 变更内容 | 原因 |
|---|---|---|
| 2026-10-15 | 初稿 | 按七个子域加智能层整理初步设计 |
| 2026-10-15 | 一期简化为居家养老：两层落地、预约不支付 | 业务方确定居家养老为主，用在线预约和家庭陪伴机器人解决需求 |
| 2026-10-15 | 对照机器人工程、后台架构 PDF、视觉需求，新增"机器人终端与视觉预警"；一期收敛为 5 个核心场景、11 个实体，`provider`、`workorder` 后置 | 业务方要求第一期先实现基础信息、预警、用药提醒、陪伴聊天、服务预约等核心场景，并保持简洁 |
| 2026-10-15 | 更正短信结论；确认窗口 60 秒；机器人团队即本团队，陪伴聊天改为对接助理并新增设备语音网关；新增通知渠道（短信预留开关）、相册管理、康复指导、对话创建用药；一期扩为 7 个场景、13 个实体，新增 `album`、`rehab` 子域 | 业务方回复了上轮待确认问题并追加相册与康复指导 |
| 2026-10-15 | 设备通道由长轮询改为按既有通信设计实现 MQTT 子集；新增设备配对与绑定、通信方案总览、队列与定时；计费主体定为设备所有者；去掉康复审核与相册容量限制；增加 M0 通信 PoC | 业务方要求实现既有通信方案与各类接口，并回复了上轮待确认问题 |
| 2026-10-15 | 前端范围收敛为运营管理端，家属端后置，绑定、预警确认、相册管理改由运营代办；新增认证方案与设备认证；broker 不再选阿里云物联网平台（已停止新购） | 业务方定前端先做管理端；核实现有认证方案与阿里云物联网平台状态 |
| 2026-10-15 | 预警通知改为同时发运营值班（站内信加钉钉群，复用现有发送器）和联系人站内信，去掉逐级升级与单级超时；服务号、小程序后置；补充云消息队列 MQTT 版的已核实信息；新增开发前的人类审核范围；设备所有者必须指定用户；去掉凭据轮换与批量导入；增加按场景划分 MQTT 与 REST | 业务方要求预警触达后台账号并发钉钉，功能优先、不提前扩展 |
| 2026-10-15 | 设计通过人类审核；broker 定为 EMQX 单节点，与 Java 服务同机独立容器，一期一台测试设备不压测；M0 由"通信 PoC"改为"通信基础"；租约字段暂不实现获确认；保留期限与阈值按默认；新增 tasks.md | 业务方回复并确认审核通过 |
| device（通用设备层） | 11_000_000 ~ 11_099_999 |
