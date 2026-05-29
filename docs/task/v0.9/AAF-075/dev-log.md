# 开发记录：AAF-075 渠道集成

执行者：AI/developer-service

## #7501 微信公众号/小程序渠道适配

✅ 2026-05-29 — developer-service

- 新建 module/channel（controller/service/domain/repository/vo 分层）
- ChannelAdapter 接口：channelType()/receive()/reply()/pushTemplate()，与 messaging.ChannelSender 分工明确
- WechatMpChannelAdapter：公众号消息接收（文本/图片/语音/事件）+ 客服接口回复 + 模板消息推送
- WechatMiniChannelAdapter：小程序客服消息收发
- MiniAppLoginService：jscode2session→openid→查/建 UserOauth(provider='wechat_mini')+User→签发 JWT

> **决策**：小程序登录复用现有 UserOauth 表（provider='wechat_mini'），不新建登录表

## #7504 统一消息路由

✅ 2026-05-29 — developer-service

- ChannelMessageRouter：按 channelType 路由到对应 ChannelAdapter，支持渠道降级
- MessageHandler 接口：supports()+handle()+order()，供 AAF-076 客服模块实现接入
- DefaultMessageHandler：兜底回复（order=MAX_VALUE），客服 handler 优先级更高时自动拦截
- MockChannelAdapter：@ConditionalOnMissingBean 兜底，无配置时 Mock 链路完整

> **沉淀**：ChannelAdapter（双向 IM）与 ChannelSender（单向通知）职责分离——前者收+发+推模板，后者只发

## 实现文件

| 文件 | 说明 |
|------|------|
| `aaf-common/.../enums/channel/ChannelTypeEnum.java` | 渠道类型枚举 |
| `aaf-common/.../enums/channel/MessageTypeEnum.java` | 消息类型枚举 |
| `aaf-common/.../enums/channel/MessageDirectionEnum.java` | 消息方向枚举 |
| `aaf-api/.../module/channel/domain/UnifiedMessage.java` | 统一消息模型（record） |
| `aaf-api/.../module/channel/domain/ChannelConfig.java` | 渠道配置实体 |
| `aaf-api/.../module/channel/domain/ChannelMessage.java` | 消息记录实体 |
| `aaf-api/.../module/channel/repository/ChannelConfigRepository.java` | 渠道配置 Repository |
| `aaf-api/.../module/channel/repository/ChannelMessageRepository.java` | 消息记录 Repository |
| `aaf-api/.../module/channel/service/ChannelAdapter.java` | 渠道适配器接口 |
| `aaf-api/.../module/channel/service/MessageHandler.java` | 消息处理器接口 |
| `aaf-api/.../module/channel/service/ChannelMessageRouter.java` | 统一消息路由器 |
| `aaf-api/.../module/channel/service/MiniAppLoginService.java` | 小程序登录服务 |
| `aaf-api/.../module/channel/service/adapter/WechatMpChannelAdapter.java` | 微信公众号适配器 |
| `aaf-api/.../module/channel/service/adapter/WechatMiniChannelAdapter.java` | 微信小程序适配器 |
| `aaf-api/.../module/channel/service/adapter/MockChannelAdapter.java` | Mock 适配器 |
| `aaf-api/.../module/channel/service/handler/DefaultMessageHandler.java` | 默认消息处理器 |
| `aaf-api/.../module/channel/controller/ChannelController.java` | 渠道接入控制器 |
| `aaf-api/.../module/channel/config/WechatChannelProperties.java` | 微信渠道配置属性 |
| `aaf-api/.../module/channel/config/WechatChannelAutoConfiguration.java` | 微信 SDK 自动配置 |
| `aaf-api/.../module/channel/vo/MiniAppLoginDTO.java` | 小程序登录请求 DTO |
| `aaf-api/.../module/channel/vo/MiniAppSessionVO.java` | 小程序登录响应 VO |
| `aaf-api/src/main/resources/db/migration/v6__channel_schema.sql` | 渠道 Schema + 字典 seed |
| `aaf-dependencies/pom.xml` | 新增 weixin-java-mp/miniapp 版本声明 |
| `aaf-api/pom.xml` | 引入 weixin-java-mp/miniapp（optional） |
| `aaf-framework/.../security/SecurityConfig.java` | 公开 /api/channel/wx/** 路径 |

## 注意事项

- 真实适配器需配置 `aaf.channel.wx.mp.enabled=true` / `aaf.channel.wx.mini.enabled=true` 才激活
- 无配置时 MockChannelAdapter 自动生效，Mock 链路完整
- 微信公众号回调采用客服接口异步回复（非被动回复 XML），避免 5 秒超时
- MessageHandler 接口是 AAF-076 客服模块的接入点，实现 `supports()` + `handle()` 即可

## #7502 钉钉/飞书机器人

✅ 2026-05-29 — developer-service

- DingtalkBotChannelAdapter：企业内部机器人消息接收/回复（文本/Markdown/卡片）、加签验证、sessionWebhook 群聊回复
- FeishuBotChannelAdapter：自建应用机器人、事件订阅（含 challenge 验证）、tenant_access_token 获取、消息发送
- BotCommandParser：/命令 参数 格式解析，结果填充到 UnifiedMessage.extra（isCommand/command/args）
- BotChannelProperties：钉钉/飞书配置属性（webhookUrl/secret/appId/appSecret/verificationToken/encryptKey）
- 两个 adapter 均 @ConditionalOnProperty 控制，默认不注册

> **决策**：HTTP 调用用 RestClient.Builder 注入（与 security/oauth 一致），不引钉钉/飞书 SDK

## #7503 Webhook

✅ 2026-05-29 — developer-service

- WebhookConfig entity（webhook_config 表）：url/事件类型/密钥/状态/方向/失败计数/最大重试
- WebhookLog entity（webhook_log 表）：推送记录/响应状态/失败原因/重试次数/下次重试时间
- WebhookService：出站推送（HMAC-SHA256 签名 + 指数退避重试）+ 入站接收（转 UnifiedMessage 走 router）
- WebhookChannelAdapter：入站 Webhook 的 ChannelAdapter 实现，始终注册（通用能力）
- 重试方法 retryFailed() 供定时任务调用，连续失败 10 次自动停用 Webhook

> **沉淀**：Webhook 出站推送与入站接收分离——出站通过 WebhookService.triggerEvent()，入站通过 WebhookChannelAdapter 走 router

## #7505 渠道管理后台

✅ 2026-05-29 — developer-service

- ChannelConfigService：渠道配置 CRUD + 状态监控（消息量/错误率/适配器可用性）+ 连通性测试 + 消息统计
- ChannelController 补充：配置管理接口（CRUD）+ Webhook 配置接口 + 监控统计接口 + 连通性测试接口
- ChannelStatsVO：渠道状态统计响应模型
- ChannelMessageRepository 补充统计查询方法（countByChannelType/countByTimeBetween）

## 第二批新增/修改文件

| 文件 | 说明 |
|------|------|
| `aaf-common/.../enums/channel/WebhookStatusEnum.java` | Webhook 状态枚举（新增） |
| `aaf-common/.../enums/channel/ChannelTypeEnum.java` | 补充 WEBHOOK 枚举值（修改） |
| `aaf-common/.../enums/channel/MessageTypeEnum.java` | 补充 MARKDOWN/CARD 枚举值（修改） |
| `aaf-api/.../channel/service/adapter/DingtalkBotChannelAdapter.java` | 钉钉机器人适配器（新增） |
| `aaf-api/.../channel/service/adapter/FeishuBotChannelAdapter.java` | 飞书机器人适配器（新增） |
| `aaf-api/.../channel/service/adapter/WebhookChannelAdapter.java` | Webhook 入站适配器（新增） |
| `aaf-api/.../channel/service/adapter/BotCommandParser.java` | 机器人指令解析器（新增） |
| `aaf-api/.../channel/service/WebhookService.java` | Webhook 服务（新增） |
| `aaf-api/.../channel/service/ChannelConfigService.java` | 渠道配置管理服务（新增） |
| `aaf-api/.../channel/domain/WebhookConfig.java` | Webhook 配置实体（新增） |
| `aaf-api/.../channel/domain/WebhookLog.java` | Webhook 推送日志实体（新增） |
| `aaf-api/.../channel/repository/WebhookConfigRepository.java` | Webhook 配置 Repository（新增） |
| `aaf-api/.../channel/repository/WebhookLogRepository.java` | Webhook 日志 Repository（新增） |
| `aaf-api/.../channel/repository/ChannelMessageRepository.java` | 补充统计查询方法（修改） |
| `aaf-api/.../channel/controller/ChannelController.java` | 补充钉钉/飞书/Webhook 回调 + 管理接口（修改） |
| `aaf-api/.../channel/config/BotChannelProperties.java` | 钉钉/飞书配置属性（新增） |
| `aaf-api/.../channel/config/BotChannelAutoConfiguration.java` | 机器人渠道自动配置（新增） |
| `aaf-api/.../channel/vo/ChannelStatsVO.java` | 渠道状态统计 VO（新增） |
| `aaf-api/src/main/resources/db/migration/v6__channel_schema.sql` | 追加 webhook_config/webhook_log 表 + 字典 seed（修改） |

## 机器人 adapter 接入 router 说明

钉钉/飞书 adapter 均 `implements ChannelAdapter`，通过 `@Component` + `@ConditionalOnProperty` 注册为 Spring Bean。
`ChannelMessageRouter` 构造时自动收集所有 `ChannelAdapter` Bean 并按 `channelType()` 建立路由表。
因此只要配置 `aaf.channel.dingtalk.enabled=true` 或 `aaf.channel.feishu.enabled=true`，对应 adapter 自动注册进 router，
入站消息通过 `router.routeInbound(ChannelTypeEnum.DINGTALK/FEISHU, payload)` 即可走完整 handler 链。
