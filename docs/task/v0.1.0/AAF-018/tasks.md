---
level: Practice
layer: Product
purpose: AAF-018 开源框架授权控制的技术任务清单
status: active
version: 1.0.0
date: 2026-05-22
author: AaronZZH
---

# 开源框架授权控制（AAF-018）

> 需求：[需求规格](requirement.md)
> 设计：[商业授权控制设计](../../../design/framework/security/license-control.md)
> 负责人：developer-service | 创建：05-22

## 任务列表

> **执行策略**：先建 License 核心对象和加载器，再接 AOP 门控，最后做分散式耦合。
> 放置模块：`aaf-framework`（`framework.security.license` 包）。

### License 核心

1. ✅ #01801 License 对象 + LicenseRequiredException — developer-service
   - `License` 单例（volatile 字段：premium/userId/tier/expiresAt）
   - `LicenseRequiredException`（含功能名 + 升级链接）
   - 放置：`aaf-framework/.../framework/security/license/`
   - verify: 单元测试覆盖 isPremium/getUserId 默认值

2. ✅ #01802 JWT 加载器（LicenseLoader） — developer-service (依赖: #01801)
   - 扫描 `~/.aaf/license.jwt` 和 `./config/license.jwt`
   - RS256 公钥内嵌代码（多处冗余），验签 + 过期检查
   - 三种路径：有效 → 设置 premium=true；无文件 → free mode；无效/过期 → 警告降级
   - 日志输出符合需求 AC（"License loaded: premium [user_id]" 等）
   - 实现为 `@EventListener(ApplicationStartedEvent.class)`
   - verify: LicenseLoaderTest 覆盖四个 Scenario（含过期场景）

3. ✅ #01803 @PremiumRequired 注解 + LicenseAspect — developer-service (依赖: #01801)
   - `@PremiumRequired` 注解（方法级）
   - `LicenseAspect`：Around 拦截，未授权抛 `LicenseRequiredException`
   - verify: LicenseAspectTest 覆盖 premium=true/false 两个场景

4. ✅ #01804 插件注册过滤（PluginRegistry） — developer-service (依赖: #01801)
   - `Plugin` 接口（`requiresPremium()` 方法）
   - `PluginRegistry.register(List<Plugin>)`：premium=false 时跳过高级插件并记录 debug 日志
   - verify: PluginRegistryTest 覆盖 premium/free 两种注册场景

### 分散式权限耦合

5. ✅ #01805 AgentScheduler seed 耦合 — developer-service (依赖: #01801)
   - `AgentScheduler` 构造时用 `hash(userId)` 作为 Random seed（userId=null 时 seed=0）
   - 放置：`aaf-framework/.../framework/intelligent/agent/`（复用已有包）
   - verify: AgentSchedulerTest 验证 seed 差异导致调度序列不同

6. ✅ #01806 配置参数动态设置（LicenseAwareConfig） — developer-service (依赖: #01801)
   - `LicenseAwareConfig`：`getMaxTokens()` / `getMaxConcurrentAgents()` 按 isPremium 返回不同值
   - Premium：max_tokens=8192，max_concurrent_agents=20；Free：2048/3
   - verify: LicenseAwareConfigTest 覆盖 premium/free 两种配置值

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->
<!-- 完成后标注负责人：✅ #01801 任务描述 - developer-service -->

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入。

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| architect（技术设计） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及 |
| developer（编码） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
