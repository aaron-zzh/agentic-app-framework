---
level: Practice
layer: Product
purpose: AAF-040 消息基础设施的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 消息基础设施（AAF-040）

> 负责人：architect + developer-service | 创建：05-19

## 任务列表

1. [ ] #4001 消息服务抽象
   - 定义 MessageService 接口（send/batchSend/getStatus）
   - 消息渠道枚举（SMS/EMAIL/INTERNAL）
   - 消息模板引擎（变量替换）
   - verify: 接口定义完整，编译通过

2. [ ] #4002 短信服务
   - 阿里云短信 SDK 集成、腾讯云短信 SDK 集成
   - 短信模板管理、发送记录、频率限制
   - verify: 短信发送成功（测试号）

3. [ ] #4003 邮件服务
   - Spring Mail 集成、HTML 邮件模板
   - 附件支持、异步发送
   - verify: 邮件发送成功

4. [ ] #4004 站内信
   - 站内消息表设计、已读/未读状态
   - 消息推送（WebSocket/SSE）
   - verify: 站内信发送→接收→标记已读流程通过

5. [ ] #4005 消息模板管理
   - 模板 CRUD、变量定义、预览
   - 多渠道模板（同一事件不同渠道不同模板）
   - verify: 模板创建→渲染→发送流程通过
