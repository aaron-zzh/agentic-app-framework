---
level: Practice
layer: Model
purpose: 消息引擎设计——多渠道消息通知、模板管理、发送策略
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
changelog:
  - 2026-05-20 v0.1.0 | 初稿
---

# 消息引擎设计

> 消息引擎是横切能力，为所有业务服务提供统一的多渠道消息通知。业务层只调用消息接口，不感知渠道细节。

## 支持渠道

| 渠道 | 场景 | 实现 |
|------|------|------|
| 站内消息 | 系统通知、任务提醒、审批通知 | Redis Pub/Sub + SSE 推送 |
| 邮件 | 注册验证、重要通知、报告推送 | Spring Mail（SMTP） |
| 短信 | 验证码、紧急告警 | 阿里云 / 腾讯云 SMS |
| 微信 | 模板消息、小程序通知 | 微信公众号 / 小程序 API |
| 钉钉/飞书 | 企业内部通知、机器人消息 | Webhook / 开放 API |
| WebSocket/SSE | 实时流式输出、进度推送 | Spring WebFlux SSE |

## 核心组件

| 组件 | 职责 |
|------|------|
| `MessageService` | 统一发送入口，屏蔽渠道差异 |
| `ChannelSender` | 各渠道发送实现（可插拔） |
| `MessageTemplateEngine` | 模板渲染（FreeMarker），支持变量替换 |
| `MessageTemplateProvider` | 模板存储与查询 |
| `SmsRateLimiter` | 短信频率限制，防刷 |

## 发送流程

```text
业务服务调用 MessageService.send(request)
  → 解析渠道类型
  → 加载消息模板，渲染内容
  → 路由到对应 ChannelSender
  → 异步发送（@Async）
  → 记录发送日志
```

## 消息模板

- 模板以代码（code）标识，存储在数据库
- 支持变量占位符（FreeMarker 语法）
- 按渠道分别维护模板内容（同一业务事件，不同渠道不同格式）

## 与调度引擎的关系

批量通知、定时推送通过调度引擎触发，消息引擎只负责发送，不管调度逻辑。
