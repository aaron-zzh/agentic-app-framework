# 03 渠道 · 客服

> 覆盖：渠道路由/配置、Webhook 出入站、客服会话生命周期、坐席分配。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B7 | 🔴 | `module/channel ChannelConfigService`（create/update/getById/listAll）+ `ChannelConfig` 实体 | 直接返回实体，含 `appSecret/token/encodingAesKey` 敏感凭证且无 `@JsonIgnore`→经接口外泄；`WebhookConfig.secret` 同理 | 凭证字段加 `@JsonIgnore`；接口出参改 VO 并脱敏 |
| M6 | 🟠 | `WebhookService.listActive`、`ChannelConfigService.listAll` | service 返回 Entity 给上层，违反"service 不得返回 Entity" | 补 VO 转换 |
| m1 | 🟡 | 各 `*Repository` | `findByStatusAndDeletedFalse` 与 `BaseEntity` 全局 `@SQLRestriction("deleted=false")` 冗余 | 去掉冗余 `DeletedFalse` |

## 良好实践

- `ChannelMessageRouter` 适配器按 `channelType` 装配、handler 按 `order` 排序、出站支持降级（failover），端口/适配器抽象清晰。注：渠道降级属运行时容错，不违反"禁兼容层"。
- `ChatSession` 充血模型（transferToHuman/assignStaff/close）状态流转封装得当，枚举持久化用 `@Enumerated(STRING)` 正确。
- 客服会话关闭通过 `SessionClosedEvent` 解耦触发评价，符合领域事件实践。

## 对称性提示

- 资源申请 vs 释放（清单#6）：坐席 `incrementSessions`/`decrementSessions` 路径保持对称。

## 待确认

- `LivechatMessageHandler`/`BotReplyService` 与 `ChannelMessageRouter` 的入站去重（同一外部消息重复推送）未深读。
- `TicketService` 工单状态机与 SLA 未审。
