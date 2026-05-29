# 开发记录：AAF-076 客服系统

执行者：AI/developer-service

## #7601 智能客服 Agent

✅ 2026-05-29 — developer-service

- 新建 module/livechat（domain/repository/service/controller/vo）
- ChatSession/ChatMessage 实体 + LivechatMessageHandler 实现 channel.MessageHandler（order=100）
- BotReplyService 复用 ProblemService（FAQ 匹配）+ KnowledgeSegmentService（语义检索 RAG）
- 意图识别路由：转人工关键词检测 + 敏感话题检测 → 自动触发 transferToHuman
- 多轮上下文：通过 session 维度查询最近 10 条消息供 Bot 参考

## #7602 人工接入坐席分配

✅ 2026-05-29 — developer-service

- LivechatSeat 实体 + SeatService：按技能组/空闲度分配，容量管理（incrementSessions/decrementSessions）
- 状态流转：BOT→WAITING（transferToHuman）→ACTIVE（assignStaff）→CLOSED
- 坐席工作台 API：待接入列表/当前会话/发送消息/知识库搜索/上下线

## #7603 会话转接与协作

✅ 2026-05-29 — developer-service

- SessionTransfer 实体记录转接历史（from/to/reason/note）
- 多人协作：inviteCollaborate 发送 internal=true 消息，不对用户可见
- 超时处理：closeInactiveSessions/reassignWaitingSessions 方法供定时任务调用
- v7__livechat_schema.sql 建表 + 字典 seed

## #7604 满意度评价

✅ 2026-05-29 — developer-service

- SessionRating 实体 + SessionRatingRepository（含统计查询）
- RatingService：提交评价（防重复）、统计（平均分/分布）、坐席平均分、差评列表
- 差评预警：score≤2 时 log.warn 记录，预留 messaging 通知扩展点
- 会话关闭触发评价：ChatSessionService.closeSession() 发布 SessionClosedEvent → RatingEventListener 监听触发邀请
- TicketController 中暴露评价 API（/ratings、/ratings/statistics、/ratings/staff/{staffId}）

## #7605 工单管理

✅ 2026-05-29 — developer-service

- Ticket 实体（充血模型：startProcessing/submitConfirm/close/reopen 状态机方法）
- TicketRecord 实体记录每次流转
- TicketService：创建（自动生成编号+SLA计算）、分配、确认、关闭、重开、转派、SLA超时扫描、统计
- SLA 计算：按 TicketPriorityEnum.slaHours 自动设置截止时间（LOW=72h/MEDIUM=48h/HIGH=24h/URGENT=4h）
- 工单统计：总量/已关闭/超时/解决率/超时率/按状态分布/按类型分布
- TicketController：完整 CRUD + 流转 + 统计 API
- 枚举：TicketStatusEnum/TicketPriorityEnum/TicketTypeEnum/TicketOperationEnum

## 实现文件

| 文件 | 说明 |
|------|------|
| `aaf-common/.../enums/livechat/SessionStatusEnum.java` | 会话状态枚举 |
| `aaf-common/.../enums/livechat/SenderTypeEnum.java` | 发送者类型枚举 |
| `aaf-common/.../enums/livechat/SeatStatusEnum.java` | 坐席状态枚举 |
| `aaf-common/.../enums/livechat/TransferReasonEnum.java` | 转接原因枚举 |
| `aaf-common/.../enums/livechat/TicketStatusEnum.java` | 工单状态枚举（#7605 新增） |
| `aaf-common/.../enums/livechat/TicketPriorityEnum.java` | 工单优先级枚举（含 SLA 时效，#7605 新增） |
| `aaf-common/.../enums/livechat/TicketTypeEnum.java` | 工单类型枚举（#7605 新增） |
| `aaf-common/.../enums/livechat/TicketOperationEnum.java` | 工单操作类型枚举（#7605 新增） |
| `aaf-api/.../module/livechat/domain/ChatSession.java` | 会话实体 |
| `aaf-api/.../module/livechat/domain/ChatMessage.java` | 消息实体 |
| `aaf-api/.../module/livechat/domain/LivechatSeat.java` | 坐席实体 |
| `aaf-api/.../module/livechat/domain/SessionTransfer.java` | 转接记录实体 |
| `aaf-api/.../module/livechat/domain/SessionRating.java` | 满意度评价实体（#7604 新增） |
| `aaf-api/.../module/livechat/domain/Ticket.java` | 工单实体（充血模型，#7605 新增） |
| `aaf-api/.../module/livechat/domain/TicketRecord.java` | 工单流转记录实体（#7605 新增） |
| `aaf-api/.../module/livechat/repository/ChatSessionRepository.java` | 会话仓储 |
| `aaf-api/.../module/livechat/repository/ChatMessageRepository.java` | 消息仓储 |
| `aaf-api/.../module/livechat/repository/LivechatSeatRepository.java` | 坐席仓储 |
| `aaf-api/.../module/livechat/repository/SessionTransferRepository.java` | 转接仓储 |
| `aaf-api/.../module/livechat/repository/SessionRatingRepository.java` | 评价仓储（#7604 新增） |
| `aaf-api/.../module/livechat/repository/TicketRepository.java` | 工单仓储（#7605 新增） |
| `aaf-api/.../module/livechat/repository/TicketRecordRepository.java` | 工单记录仓储（#7605 新增） |
| `aaf-api/.../module/livechat/service/LivechatMessageHandler.java` | 渠道消息处理器（核心接入点） |
| `aaf-api/.../module/livechat/service/ChatSessionService.java` | 会话核心服务（#7604 修改：关闭时发布事件） |
| `aaf-api/.../module/livechat/service/BotReplyService.java` | 智能客服回复服务 |
| `aaf-api/.../module/livechat/service/SeatService.java` | 坐席管理服务 |
| `aaf-api/.../module/livechat/service/RatingService.java` | 满意度评价服务（#7604 新增） |
| `aaf-api/.../module/livechat/service/RatingEventListener.java` | 评价事件监听器（#7604 新增） |
| `aaf-api/.../module/livechat/service/SessionClosedEvent.java` | 会话关闭事件（#7604 新增） |
| `aaf-api/.../module/livechat/service/TicketService.java` | 工单管理服务（#7605 新增） |
| `aaf-api/.../module/livechat/controller/LivechatController.java` | 坐席工作台 API |
| `aaf-api/.../module/livechat/controller/TicketController.java` | 工单与评价 API（#7604/#7605 新增） |
| `aaf-api/.../module/livechat/vo/ChatSessionVO.java` | 会话 VO |
| `aaf-api/.../module/livechat/vo/ChatMessageVO.java` | 消息 VO |
| `aaf-api/.../module/livechat/vo/StaffSendMessageDTO.java` | 发送消息 DTO |
| `aaf-api/.../module/livechat/vo/SessionTransferDTO.java` | 转接请求 DTO |
| `aaf-api/.../module/livechat/vo/RatingSubmitDTO.java` | 提交评价 DTO（#7604 新增） |
| `aaf-api/.../module/livechat/vo/RatingStatVO.java` | 评价统计 VO（#7604 新增） |
| `aaf-api/.../module/livechat/vo/TicketCreateDTO.java` | 创建工单 DTO（#7605 新增） |
| `aaf-api/.../module/livechat/vo/TicketVO.java` | 工单 VO（#7605 新增） |
| `aaf-api/.../module/livechat/vo/TicketStatVO.java` | 工单统计 VO（#7605 新增） |
| `aaf-api/.../resources/db/migration/v7__livechat_schema.sql` | 数据库迁移脚本（追加 3 张表 + 字典） |

## 实现决策

- LivechatMessageHandler order=100，确保优先于 DefaultMessageHandler(MAX_VALUE) 拦截文本消息
- BotReplyService 以桩方式对接 LLM（generateMockReply），后续接入真实 Assistant 时替换此方法
- 坐席发送消息通过 ChannelMessageRouter.routeOutbound() 推送给用户，复用渠道路由能力
- 知识库 ID 暂硬编码为 1L（DEFAULT_KNOWLEDGE_BASE_ID），后续可配置化
- 评价触发采用 Spring ApplicationEvent 解耦：ChatSessionService.closeSession() 发布 SessionClosedEvent，RatingEventListener 监听后触发评价邀请，不侵入原有关闭逻辑
- 工单状态机用充血模型实现（Ticket 实体内 startProcessing/submitConfirm/close/reopen 方法），状态校验内聚
- SLA 时效按 TicketPriorityEnum.slaHours 字段计算（LOW=72h/MEDIUM=48h/HIGH=24h/URGENT=4h），创建时自动设置 slaDueTime
- 工单编号格式 TK{yyyyMMdd}{5位序号}，AtomicLong 保证单实例唯一
- TicketRecord.recordRemark 字段名避免与 BaseEntity.remark 冲突

## 注意事项

- 超时处理方法（closeInactiveSessions/reassignWaitingSessions）需外部定时任务调用，本批未配置 @Scheduled
- 坐席发消息推送依赖 ChannelAdapter.reply() 实现，需确保对应渠道 adapter 已注册
- FAQ 匹配依赖 ProblemService.search() 的关键词搜索，语义检索依赖 KnowledgeSegmentService.semanticSearch()（当前为 TODO 桩）
- SLA 超时扫描方法 TicketService.scanOverdue() 需外部定时任务调用（或通过 TicketController POST /tickets/scan-overdue 手动触发）
- 差评预警当前仅 log.warn，后续可注入 messaging.MessageService 推送通知给主管
- 评价邀请当前仅日志记录，后续可通过 ChannelMessageRouter 推送评价卡片给用户
- 工单编号在分布式部署时需改为分布式 ID 生成（当前 AtomicLong 仅适用单实例）
