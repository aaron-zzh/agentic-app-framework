# 企业微信智能客服设计

## 概述

基于企业微信「微信客服」API，将企微客服作为**渠道接入层**，背后对接 AAF 的 Assistant 实例。每个客服账号可绑定不同的 Assistant，复用框架的知识库、技能、记忆、Agent 编排等全部能力。

## 架构

```text
微信用户 → 企微服务器 → [回调通知] → AAF 后端（渠道层）
                                         ↓
                                    sync_msg 拉取消息
                                         ↓
                                    路由：openKfId → assistantId
                                         ↓
                                    AssistantExecutor.chat()
                                    ├─ 记忆管道（短期/长期/知识库）
                                    ├─ Skill 匹配
                                    ├─ Agent 调度（工具调用/工作流）
                                    └─ 响应生成
                                         ↓
                                    send_msg 发送回复
                                         ↓
                              企微服务器 → 微信用户
```

## 核心设计：渠道 → Assistant 映射

企微客服模块**不包含任何 AI 逻辑**，仅负责：
1. 接收企微回调、验签解密
2. 拉取消息
3. 根据配置将消息路由到对应 Assistant
4. 将 Assistant 响应通过企微 API 发回

```yaml
aaf:
  wecom:
    kf:
      enabled: true
      corp-id: ${WECOM_CORP_ID}
      app-secret: ${WECOM_APP_SECRET}
      token: ${WECOM_KF_TOKEN}
      encoding-aes-key: ${WECOM_KF_AES_KEY}
      # 默认 Assistant（所有客服账号共用）
      default-assistant-id: "cs-general"
      # 按客服账号绑定不同 Assistant（可编排不同能力）
      account-assistant-mapping:
        wkABC123: "cs-sales"       # 售前客服 → 销售助理
        wkDEF456: "cs-support"     # 售后客服 → 技术支持助理
        wkGHI789: "cs-order"       # 订单客服 → 订单查询助理
      fallback-reply: "感谢您的咨询，已为您转接人工客服。"
```

## 可编排能力

通过 Assistant 体系，每个客服账号可独立配置：

| 能力 | 来源 | 说明 |
|------|------|------|
| 人格/话术风格 | Actor | 不同客服账号可有不同语气 |
| 知识库 | AssistantDefinition.knowledgeBaseId | 绑定不同产品知识库 |
| 技能 | Skill 匹配 | 查订单、查物流、预约等 |
| 工具调用 | Agent + Tool | 调用外部API |
| 工作流 | Flowable + AssistantNode | 复杂多步骤流程 |
| 记忆 | MemoryStrategy | 记住客户历史对话 |
| 转人工 | Skill/工作流 | 识别无法处理时升级 |

## 核心 API

| 接口 | 方法 | 地址 | 说明 |
|------|------|------|------|
| 接收回调 | POST | /api/wecom/kf/callback | 接收企微事件通知(kf_msg_or_event) |
| 拉取消息 | POST | qyapi.weixin.qq.com/cgi-bin/kf/sync_msg | 获取最近3天消息 |
| 发送消息 | POST | qyapi.weixin.qq.com/cgi-bin/kf/send_msg | 回复客户(48h内≤5条) |
| 获取token | GET | qyapi.weixin.qq.com/cgi-bin/gettoken | 获取access_token |

## 消息处理流程

```text
1. 企微推送 kf_msg_or_event 回调
2. 解密回调 XML，提取 Token 和 OpenKfId
3. 调用 sync_msg 拉取新消息（带 cursor 增量拉取）
4. 过滤：只处理 origin=3（客户发送）且 msgtype=text 的消息
5. 消息路由：
   a. 关键词规则匹配 → 命中则直接回复预设内容
   b. 未命中 → 调用 AI（知识库 RAG + LLM）生成回复
6. 调用 send_msg 发送回复
7. 更新 cursor 持久化
```

## 数据模型

### 配置表 wecom_kf_config

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| corp_id | varchar(64) | 企业ID |
| app_secret | varchar(128) | 应用密钥(加密存储) |
| token | varchar(64) | 回调Token |
| encoding_aes_key | varchar(64) | 回调加密Key |
| open_kf_id | varchar(64) | 客服账号ID |
| enabled | boolean | 是否启用 |

### 会话记录表 wecom_kf_message

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| msg_id | varchar(64) | 企微消息ID |
| open_kf_id | varchar(64) | 客服账号ID |
| external_user_id | varchar(64) | 客户UserID |
| origin | int | 来源(3客户/5接待人员) |
| msg_type | varchar(32) | 消息类型 |
| content | text | 消息内容 |
| reply_content | text | AI回复内容 |
| send_time | timestamp | 发送时间 |
| created_at | timestamp | 创建时间 |

### 关键词规则表 wecom_kf_keyword_rule

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| open_kf_id | varchar(64) | 客服账号ID |
| keyword | varchar(128) | 关键词(支持正则) |
| match_type | varchar(16) | 匹配方式(exact/contains/regex) |
| reply_content | text | 回复内容 |
| priority | int | 优先级 |
| enabled | boolean | 是否启用 |

## 模块结构

```text
com.xuejiai.aaf.module.customerservice
├── controller
│   └── WecomKfCallbackController    // 回调接收端点
├── service
│   ├── WecomKfService               // 核心业务逻辑
│   ├── WecomKfMessageHandler        // 消息处理管道
│   └── WecomKfApiClient             // 企微API调用
├── model
│   ├── entity
│   │   ├── WecomKfConfig
│   │   ├── WecomKfMessage
│   │   └── WecomKfKeywordRule
│   └── dto
│       ├── WecomCallbackEvent       // 回调事件
│       ├── SyncMsgRequest/Response  // 拉取消息
│       └── SendMsgRequest/Response  // 发送消息
├── repository
│   ├── WecomKfConfigRepository
│   ├── WecomKfMessageRepository
│   └── WecomKfKeywordRuleRepository
└── config
    └── WecomKfProperties            // 配置属性
```

## 与 AAF 框架集成

企微客服模块是纯粹的**渠道适配器**，通过 `AssistantExecutor.chat()` 接口与框架对接：

```java
// sessionId 格式：wecom:{openKfId}:{externalUserId}
// 保证同一客户同一客服账号的对话连续
assistantExecutor.chat(sessionId, assistantId, userId, userMessage);
```

Assistant 内部自动完成：
- **记忆拉取**：按 MemoryStrategy 加载历史对话和知识库上下文
- **Skill 匹配**：识别用户意图，路由到对应技能
- **Agent 调度**：执行工具调用、API 请求等
- **工作流触发**：复杂场景走 Flowable 流程（如转人工审批）

## 扩展：多渠道复用

同一个 Assistant 可同时服务多个渠道：

```text
企微客服 ──┐
公众号   ──┼── AssistantExecutor ── Assistant 实例
网页聊天 ──┘
```

新增渠道只需实现渠道适配器（回调接收 + 消息发送），无需修改 AI 逻辑。

## 配置项

```yaml
aaf:
  wecom:
    kf:
      enabled: true
      corp-id: ${WECOM_CORP_ID}
      app-secret: ${WECOM_APP_SECRET}
      token: ${WECOM_KF_TOKEN}
      encoding-aes-key: ${WECOM_KF_AES_KEY}
      default-assistant-id: "cs-general"
      account-assistant-mapping:
        wkABC123: "cs-sales"
        wkDEF456: "cs-support"
      fallback-reply: "感谢您的咨询，已为您转接人工客服。"
```

## 约束与限制

- 用户发消息后 48 小时内可回复，最多 5 条
- 回调 Token 有效期 10 分钟，需及时拉取消息
- cursor 必须持久化，避免重启后重复处理
- 需在企微管理后台配置「可调用接口的应用」和「通过API管理的客服账号」
- access_token 有效期 2 小时，需缓存并自动刷新
