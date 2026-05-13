---
level: Practice
layer: Model
purpose: 开源框架授权控制需求规格
status: active
version: 1.0.0
date: 2026-05-03
author: AaronZZH
---

<!-- ⚠️ 早期需求，未经过六问分析。进入开发前由 product agent 补充需求分析章节 -->
<!-- scope_mode: hold -->

# 开源框架授权控制

任务编号：AAF-018

## 背景

AAF 框架开源，高级模块和插件源码直接提供。为在开源透明的前提下区分免费用户与 Premium 用户，需要一套零运行时开销、完全离线、无服务端依赖的授权控制机制。

设计目标：
- 合法用户：放置 JWT 文件 → 开箱即用
- 破解者：需多处修改代码并保持同步，承担功能异常风险
- 企业用户：因合规/协作/可维护性，主动选择授权

## 用户故事

### US-1：框架启动时加载并验证授权

**作为** 框架使用者，**我希望** 将 JWT 授权文件放置到指定目录后框架自动识别并解锁 Premium 功能，**以便** 无需联网、无需在线激活即可使用高级能力。

#### 验收标准

```gherkin
Feature: 框架启动授权加载

  Scenario: 有效 JWT 文件 - 解锁 Premium
    Given 用户在配置目录放置了有效的 JWT 授权文件（RS256 签名，未过期）
    When 框架启动
    Then 启动时完成 JWT 签名验证（耗时 < 1ms）
    And 全局 LICENSE 对象中 is_premium=true，user_id 已填充
    And 高级模块正常加载，高级插件完成注册
    And 启动日志输出 "License loaded: premium [user_id]"

  Scenario: 无 JWT 文件 - 免费模式运行
    Given 配置目录中不存在 JWT 授权文件
    When 框架启动
    Then 全局 LICENSE 对象中 is_premium=false
    And 框架正常启动，仅加载免费功能
    And 启动日志输出 "License not found, running in free mode"

  Scenario: JWT 签名无效或已过期
    Given 用户放置了签名无效或已过期的 JWT 文件
    When 框架启动
    Then 框架记录警告日志 "Invalid or expired license, falling back to free mode"
    And 以免费模式运行，不抛出异常阻断启动
```

### US-2：高级功能权限门控

**作为** 框架开发者，**我希望** 高级模块和插件在未授权时自动降级或提示，**以便** 免费用户获得清晰的升级引导，Premium 用户无感知地使用全部功能。

#### 验收标准

```gherkin
Feature: 高级功能权限门控

  Scenario: Premium 用户访问高级模块
    Given LICENSE.is_premium = true
    When 调用任意高级模块入口方法
    Then 方法正常执行，无额外开销

  Scenario: 免费用户访问高级模块
    Given LICENSE.is_premium = false
    When 调用高级模块入口方法
    Then 抛出 LicenseRequiredException，消息包含功能名称和升级引导链接
    And 不执行任何业务逻辑

  Scenario: 配置参数按权限动态设置
    Given 框架完成授权加载
    When 初始化默认配置
    Then Premium 用户：max_tokens=8192，max_concurrent_agents=20
    And 免费用户：max_tokens=2048，max_concurrent_agents=3

  Scenario: 高级插件仅在 Premium 时注册
    Given 框架启动插件注册阶段
    When 扫描插件列表
    Then is_premium=true 时注册全部插件（含高级插件）
    And is_premium=false 时仅注册免费插件，高级插件跳过并记录 debug 日志
```

### US-3：分散式权限耦合（提高破解成本）

**作为** 框架维护者，**我希望** 授权标识深度耦合到关键算法和输出结构中，**以便** 即使破解者删除显式权限检查，功能异常或可追溯性仍然存在。

#### 验收标准

```gherkin
Feature: 分散式权限耦合

  Scenario: 关键算法使用 user_id 作为 seed
    Given LICENSE.user_id 已加载
    When 执行需要随机 seed 的关键算法（如 Agent 调度、采样策略）
    Then 使用 LICENSE.user_id 派生的值作为 seed 或 trace_id
    And 输出结果在日志/元数据中携带用户标识（不影响业务语义）

  Scenario: 删除权限检查后行为异常
    Given 破解者删除了 is_premium 检查代码但未同步修改 seed/trace 逻辑
    When 执行高级功能
    Then 因 seed 不一致导致结果与预期偏差，功能表现异常
    And 日志中仍保留 user_id 追踪信息
```

## 技术约束

| 约束 | 说明 |
|------|------|
| 完全离线 | 不发起任何网络请求验证授权，不设备绑定，不在线激活 |
| 不代理大模型 | 用户直连大模型，Key 由用户自行配置 |
| 零运行时开销 | JWT 验证仅在启动时执行一次，运行时仅为内存变量读取 O(1) |
| 纯 JWT 实现 | 权限控制仅通过 JWT（RS256）实现，无服务端实时校验 |
| 不阻断启动 | 授权失败降级为免费模式，不抛出致命异常 |

## 相关设计

- 迭代架构设计：[后端技术选型](../../../design/apps/service/tech-stack.md)（开源授权控制设计章节）
- 设计文档：`docs/design/features/license-control.md`（待创建）
- 任务：[backlog.md](../../../task/backlog.md) AAF-018
