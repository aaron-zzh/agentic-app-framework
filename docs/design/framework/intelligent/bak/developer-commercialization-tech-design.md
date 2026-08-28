---
level: Tech Design
layer: Framework
purpose: AAF 开发者商业授权、托管模型额度与子代理管理技术设计
status: draft
version: 0.1.0
date: 2026-05-31
author: Codex
---

# 开发者商业授权与托管模型额度技术方案

## 背景

AAF 面向两类计费主体：

- 框架开发者：使用 AAF 搭建自己的产品，可能购买 AAF 托管模型额度，也可能自带第三方模型 Key。
- 产品最终用户：使用框架开发者交付的业务产品，消耗产品内积分、权益或套餐额度。

因此计费与授权需要分层。开发者额度不等于最终用户积分，开发者子代理资格也不等于产品用户权限。

## 模块边界

开发者商业化能力放在 `module/developer`：

- `license`：开发者授权、功能开关、托管网关资格。
- `subscription`：开发者订阅套餐。
- `quota`：开发者总 Token 池和流水。
- `redeem`：开发者 Token 兑换码。
- `apikey`：开发者调用未来 AAF Model Gateway 的 Key。
- `proxy`：子代理、转售层级、分销资格。

AI 模型代理执行层预留在 `module/ai/gateway`，当前不启用。`ops` 只做运营视图和后台管理入口，不拥有核心业务事实。

```text
module/developer        开发者商业授权与额度事实
module/ai/gateway       模型代理执行层，合规确认后启用
module/billing/pay      产品最终用户订阅、积分和支付
module/ops              运营后台视图、审计、统计
```

## 合规边界

当前版本仅实现开发者管理，不实现公网模型代理服务。

原因是面向第三方提供模型代理/转售时，可能涉及：

- 域名、网站或应用的 ICP 备案或经营性许可要求。
- 面向境内公众提供生成式 AI 服务时的生成式人工智能服务备案或登记。
- 调用已备案模型能力的应用或功能，也可能需要属地网信办登记，并在产品显著位置公示模型名称、备案号或上线编号。

因此 `developer_api_key` 当前只作为未来 Gateway 的身份凭证管理，不代表已开放模型代理调用。

## 两层额度模型

```text
AAF 托管模型资源
  -> developer_token_account     开发者总 Token 池
    -> credit_account            产品最终用户积分账户
```

开发者总池用于控制框架开发者可消耗的托管模型资源；最终用户积分用于控制业务产品内用户消费。

当未来 Gateway 启用时，一次托管模型调用应同时满足：

- 开发者授权允许 `allow_managed_gateway`。
- 子代理链未超过 `max_proxy_depth`。
- 开发者 Token 池余额充足。
- 最终用户积分或权益余额充足。
- 模型、能力、地域和内容安全策略允许。

## BYOK 与托管模式

| 模式 | 上游 Key | 本模块控制 | 说明 |
|------|----------|------------|------|
| BYOK | 开发者自有 | 不控制上游成本 | AAF 只提供配置、路由、审计能力 |
| AAF 托管自用 | AAF 官方 Gateway | 控制开发者授权和总池 | 不允许再分销 |
| AAF 托管子代理 | AAF 官方 Gateway | 控制授权、层级和总池 | 仅授权开发者可开通 |

源码交付无法绝对阻止开发者改代码直连其他供应商。可控边界是 AAF 官方托管资源：只有通过官方 Gateway 验证的 developer key、license、proxy chain 和额度才能消耗官方资源。

## 数据模型

核心表：

- `developer_account`：开发者账户与授权状态。
- `developer_subscription_plan`：开发者订阅套餐。
- `developer_subscription`：开发者订阅实例。
- `developer_token_account`：开发者总 Token 池。
- `developer_token_transaction`：开发者 Token 流水。
- `developer_redeem_code`：开发者兑换码。
- `developer_api_key`：未来 Gateway 调用 Key。
- `developer_proxy`：子代理关系。

开发者管理表放在 `v2__ai_schema.sql`，因为它服务于 AI 托管能力和模型网关资格。字典放 `v8__init_dict_data.sql`，演示套餐放 `v9__init_seed_data.sql`。

## 接口

开发者自助接口：

```text
GET  /api/developer/account/current
GET  /api/developer/subscription/plans
POST /api/developer/subscription/subscribe
GET  /api/developer/subscription/current
GET  /api/developer/tokens/account
GET  /api/developer/tokens/transactions
POST /api/developer/tokens/redeem
POST /api/developer/api-keys
GET  /api/developer/api-keys
POST /api/developer/proxies
GET  /api/developer/proxies
```

运营接口：

```text
POST /api/developer/admin/redeem-codes
```

## Gateway 预留流程

未来启用模型代理时，应按以下流程接入：

```text
验证 developer_api_key
-> 校验 developer_account 授权
-> 校验 proxy chain 和 max_proxy_depth
-> 开发者 Token 池预检
-> 最终用户积分/权益预检
-> 调用模型
-> 写 ai_token_usage / gateway audit
-> 扣开发者 Token 池
-> 扣最终用户积分
```

Gateway 的真实扣费证明应由官方服务返回，避免本地源码被修改后伪造用量或绕过扣费。

## 与分散式 License 耦合

本地 License 可用于控制功能入口：

- 是否显示 AAF 托管模型配置。
- 是否显示子代理管理。
- 是否允许创建 `developer_api_key`。
- 是否允许选择 AAF 托管模型来源。

但 License 不是最终安全边界。最终边界应由官方 Gateway 服务端校验 developer key、license fingerprint、proxy chain、nonce、timestamp、签名和额度。

## 后续任务

- 接入开发者订阅支付，替换当前直接开通演示流程。
- 增加开发者管理后台和运营审计视图。
- 引入 License Runtime，把 `allow_managed_gateway` 和 `allow_sub_proxy` 与签名 License 绑定。
- 合规确认后再启用 `module/ai/gateway` 执行入口。
