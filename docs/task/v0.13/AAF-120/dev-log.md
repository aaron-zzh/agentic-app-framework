# 开发记录：AAF-120 养老业务模块

执行者：AI/developer-service

## #12003 模块骨架与数据模型（M1 范围）

✅ 2026-10-15 — developer-service

- 建 profile/guard 骨架与错误码
- 落 9 实体及 Crud 仓储
- 增 23 枚举与 DictType
- 建 v400/v401 表、字典与配置
- 按要求未运行构建与测试

## #12001 EMQX 部署与 Java MQTT 客户端

✅ 2026-10-15 — developer-service

- 选 HiveMQ Client 1.4.1；Paho 1.2.5 久未发布
- 配 EMQX 5.10.5 TLS 与 HTTP 回调
- EMQX 5.9+ 为 BSL，仅作外部依赖，不随 AAF 打包
- Keep Alive 60 秒；Clean Session=false，MQTT 5 会话保留 24 小时
- 未实测构建、容器及 QtMqtt 连通；需人工验证


## #12007 guard：设备认证

✅ 2026-10-15 — developer-service

- 建设备独立认证链与过滤器
- 凭据仅存 SHA-256 与前缀
- 主体仅授 ROLE_DEVICE
- 接入 owner 权限执行包装
- 按要求仅写单测未运行


## #12004 profile：长者与联系人

✅ 2026-10-15 — developer-service

- 交付长者、联系人标准 CRUD
- Patch 更新接入版本冲突 409
- ReBAC 同步 FAMILY 与授权范围
- 提供 ElderQueryApi 跨域只读契约
- 增 v109/v403 实体、权限与菜单种子


## #12008 guard：MQTT 适配

✅ 2026-10-15 — developer-service

- 封装 HiveMQ TLS、重连与六类上行
- 建可信主题路由、Outbox 与入站扩展
- 落 RFC8785、Ed25519 与启动序号校验
- 建 EMQX 认证、ACL、断连与独立安全链
- 按要求未编译、测试、lint/check；待联调



## #12006 guard：设备与配对绑定

✅ 2026-10-15 — developer-service

- 交付设备 CRUD 与 EntityDef
- 配对会话十分钟、单次取密
- 错码五次锁定，双维限流
- 解绑撤凭据并踢 MQTT
- 按要求未编译、测试、lint/check


## #12005 profile：健康条目与用药

✅ 2026-10-15 — developer-service

- 交付健康、计划、服药三套 CRUD
- 计划启停及起止日期生效
- MedicationApi 时区窗口与幂等结果
- 增 v109/v403 实体、权限与菜单
- 按要求未编译、测试、lint/check


## #12012 guard：机器人简报接口

✅ 2026-10-15 — developer-service

- 设备主体限定绑定长者
- 返回称呼、偏好、禁忌与今日提醒
- 读取 v401 当前阈值配置
- 下发 Ed25519 验签公钥
- 按要求未编译、测试、lint/check


## #12009 guard：提醒下发与用药闭环

✅ 2026-10-15 — developer-service

- 建 DeviceMessage 合法状态机
- 定时幂等下发到点用药提醒
- 闭环 ACK、结果、漏服与漏提醒
- 连续漏服达阈值通知联系人
- 停用计划事务后下发撤销


## #12018 rehab：康复指导

✅ 2026-10-15 — developer-service

- 交付指导 CRUD、发布/下线与文档校验
- 提供 RehabGuideApi 搜索、回答、引用与免责
- 复用 sourceFilters.documentIds 限定三路检索
- 草稿文档排除；无依据返回 unanswered
- 按要求未编译、测试、lint/check


## #12010 guard：预警事件与判定

✅ 2026-10-15 — developer-service

- 交付预警、处理记录 CRUD
- 落幂等入站、确认窗与状态机
- 判定无活动、离线及晚到事件
- 发布 AlertTriggeredEvent 供通知消费
- EntityDef 以排序/快捷筛选替代高亮；后续评估 customViews；按要求未跑检查


## #12014 album：相册

✅ 2026-10-15 — developer-service

- 交付照片 CRUD 与受控原图访问
- 设备上传幂等、限 10 MiB 并验魔数
- MASTER 存储 retain，软删 release
- 增照片枚举、字典、EntityDef 与权限菜单
- 按要求未编译、测试、lint/check


## #12011 guard：预警通知

✅ 2026-10-15 — developer-service

- 事务后入队 ELDER_ALERT_NOTIFY
- 运营站内信与钉钉隔离发送
- 联系人站内信逐渠道留痕
- 短信开关默认关闭，不接模板
- 建 v402 分段角色与值班账号



## #12015 mall：服务预约

✅ 2026-10-15 — developer-service

- 交付项目、预约两套 CRUD 与 EntityDef
- 落预约确认、取消、完成状态机
- 确认幂等下发，取消撤销设备提醒
- 提供目录查询与待确认预约 API
- 按要求未编译、测试、lint/check


## #12013 运营管理端（M1 范围）收口

✅ 2026-10-15 — developer-service

- 十资源权限前缀改为两段式
- 同步权限码、角色、菜单与范围
- 关闭设备删除，清理虚假字段
- 字典、动作、迁移顺序审计通过
- 按要求未运行编译、测试与检查

> **问题**：ListView 无默认筛选/行高亮，且仅有 knowledge-ops 自定义视图；预警详情 VO 无处理时间线，通用 action 不能选择确认联系人，长者详情 VO 也未聚合照片。未手写页面，待协调者决定扩展 EntityDef 或后端字段。

## #12017 agent：助理角色、技能与工具

✅ 2026-10-15 — developer-service

- 交付九项必需养老工具
- 补确认预约、预约状态、设备状态
- Redis 草稿十分钟，确认后单次消费
- 建陈伴、家属、运营三技能三角色
- 受信 owner/elder scope 供语音网关包裹

> **问题**：SkillMarkdownSourceSynchronizer 仅在技能表全空时导入；已有数据环境需统一清库重建或后续由协调者设计增量导入。本任务按要求未编译、测试、lint/check。



## #12016 guard：设备语音网关

✅ 2026-10-15 — developer-service

- 交付设备 ASR、聊天 SSE、TTS 三出口
- 助理可进程内调用，无需服务身份回调
- owner 计费，固定陈伴角色、简报及工具域
- 急症复用 HELP_REQUEST 一键求助链
- 按要求未编译、测试、lint/check

> **问题**：已执行过 v402 的环境不会自动重放本次角色挂载，且仍受 #12017 SkillMarkdownSourceSynchronizer 非增量导入限制；需清库重建或由协调者补增量导入。设备握手、流式计费和机器人回退需后续联调验证。


## #12021–#12027 多场景通用化

✅ 2026-10-16 — 协调者 + AI

- 场景声明指令能力与目标设备类型，去掉写死的能力白名单与 ROBOT
- 入站消息带 scenarioKey，处理器按场景分流
- 心跳与离线阈值统一走 `aaf.device.*` 配置
- elder 改走 DeviceQueryApi 与 DeviceMessageApi，不再引用 device 内部
- device 独占 v300–v399，elder 改 v400–v499；表、字典、权限、菜单、EntityDef 拆成独立文件
- 补 DeviceScenarioContractTest；按要求未编译、测试、lint/check

> **问题**：已执行过旧 v300–v303 的环境必须清库重建；`design.md` 的场景包一节曾因转义乱码，已修复。
> **仍保留（已决策不改）**：`Device` 的摄像头、隐私模式、居家区域枚举按"室内机器人"定位保留，非居家场景出现再改字典驱动。

## 设备二维码去掉领取凭据

✅ 2026-10-16 — 机器人端反馈，协调者修复

- `DevicePairingService.start` 的 `qrContent` 改为只含配对码：`aaf://device/pair?pairCode=…`
- `deviceCode`（轮询领取一次性设备密钥的凭据）不再进二维码，仍通过响应字段单独返回给机器人
- 微信扫码承接与家属自助绑定归入 [AAF-121](../AAF-121/requirement.md)
- 按要求未编译、测试、lint/check

## 设备对话分解策略修正

✅ 2026-10-16 — AI/developer-service

- `DeviceVoiceGatewayService.chatRequest` 的 decomposition 由 NONE 改为 null（缺省 AUTO）
- `ExecutionIntent` 禁止对话模式使用非 AUTO，原写法会使设备对话启动即失败
- 按要求未编译、测试、lint/check

> **待决**：对话轻量态仍带 `promote_to_task` 等控制工具且不受角色工具范围过滤，设备对话理论上可被升级为后台 Task；是否禁止升级需框架层开关，待协调者评估。


## 助理执行控制项重构

✅ 2026-10-16 — AI/developer-service（按人类指示执行，涉及 ≥5 文件与请求契约变更）

- 请求删除 `routeConstraint`，改由是否声明 role/skill 推导；`ExecutionIntent.RouteConstraint` 取值改为 `ROUTE_FIXED`/`ROUTE_AUTO`
- 分解策略取值改为 `DECOMPOSE_AUTO`/`DECOMPOSE_NONE`/`DECOMPOSE_REQUIRED`（请求与 `ExecutionIntent` 同步）
- 删除三个因推导而不可达的错误码（7_003_010、7_003_012、7_003_015）；`ActionAuthorizationPolicy` 仅补注释，说明对话下无效果
- 同步 `AssistantExecutionRequest`、`AssistantExecutionService`、`AssistantAguiController`、`DeviceVoiceGatewayService`、框架层引用、两个测试类、前端 7 个文件、`runtime.md` 与 ADR-008
- 事件载荷键 `routeConstraint` 保持不变，取值随枚举变为 `ROUTE_FIXED`/`ROUTE_AUTO`，前端徽标判断已同步
- 按要求未编译、测试、lint/check

> **未改动（历史记录，保留当时口径）**：AAF-107 tasks、AAF-117 dev-log/tasks、`v0.12/manual-test-guide.md`、`bak/` 设计稿中的旧取值。
> **未改动（模型可见提示词）**：`PromptAssembler` 中面向模型的"分解策略：NONE/REQUIRED"文案保持不变，避免无谓升级提示词合同版本。
> **未改动（有意保留）**：`TaskAnalysisPolicy` 中"产物策略非 RETURN_ONLY 即走 TASK"分支经请求入口不可达，但其他入口（工作流节点、测试）可能构造，未逐一核对前不删除。
