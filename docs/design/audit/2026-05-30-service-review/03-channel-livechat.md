# 03 渠道 · 客服

> 覆盖：渠道路由/配置、Webhook 出入站、客服会话生命周期、坐席分配。

## 问题清单（2026-08-01 复核）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| B7 | 🔴 | FIXED | `module/channel ChannelConfigService`（create/update/getById/listAll）+ `ChannelConfig` 实体 | 核实：`appSecret`/`token`/`encodingAesKey` 已全部 `@JsonIgnore`；`ChannelController` 出入参已全部 DTO/VO 化（`ChannelConfigSaveDTO`/`ChannelConfigVO`/`WebhookConfigSaveDTO`/`WebhookConfigVO`），无直接返回实体的端点 |
| M6 | 🟠 | FIXED | `WebhookService.listActive`、`ChannelConfigService.listAll` | 核实：`WebhookService.listActive()` 已返回 `List<WebhookConfigVO>`；`ChannelConfigService.listAll` 已重命名为 `listEnabled()` 并返回 `List<ChannelConfigVO>`，两处均无 Entity 出参 |
| m1 | 🟡 | OPEN | 各 `*Repository` | `findByStatusAndDeletedFalse` 与 `BaseEntity` 全局 `@SQLRestriction("deleted=false")` 冗余，未在本轮处理 |

## 良好实践

- `ChannelMessageRouter` 适配器按 `channelType` 装配、handler 按 `order` 排序、出站支持降级（failover），端口/适配器抽象清晰。注：渠道降级属运行时容错，不违反"禁兼容层"。
- `ChatSession` 充血模型（transferToHuman/assignStaff/close）状态流转封装得当，枚举持久化用 `@Enumerated(STRING)` 正确。
- 客服会话关闭通过 `SessionClosedEvent` 解耦触发评价，符合领域事件实践。

## 对称性提示

- 资源申请 vs 释放（清单#6）：坐席 `incrementSessions`/`decrementSessions` 路径保持对称。

## 待确认

- `LivechatMessageHandler`/`BotReplyService` 与 `ChannelMessageRouter` 的入站去重（同一外部消息重复推送）未深读。
- `TicketService` 工单状态机与 SLA 未审。
