---
level: Practice
layer: Product
purpose: AAF-018 开源框架授权控制开发记录
status: active
version: 1.0.0
date: 2026-05-22
author: AaronZZH
---

# 开发记录：开源框架授权控制（AAF-018）

执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `aaf-framework/.../security/license/License.java` | 全局授权状态单例（volatile 字段，O(1) 读取） |
| `aaf-framework/.../security/license/LicenseLoader.java` | 启动时加载 license.jwt，RS256 验签，三路降级 |
| `aaf-framework/.../security/license/LicenseRequiredException.java` | 未授权异常（含功能名 + 升级链接） |
| `aaf-framework/.../security/license/PremiumRequired.java` | 标记高级功能的注解 |
| `aaf-framework/.../security/license/LicenseAspect.java` | AOP 门控，拦截 @PremiumRequired 方法 |
| `aaf-framework/.../security/license/Plugin.java` | 插件接口 |
| `aaf-framework/.../security/license/PluginRegistry.java` | 按授权状态过滤插件注册 |
| `aaf-framework/.../intelligent/agent/AgentScheduler.java` | 用 userId.hashCode() 作为 Random seed |
| `aaf-framework/.../security/license/LicenseAwareConfig.java` | 按授权状态动态返回配置参数 |

## 实现决策

- LicenseLoader 内嵌测试用 RSA 公钥（PEM），生产环境需替换为真实公钥
- LicenseAspect 使用 Spring AOP（`@Aspect`），依赖已有的 `spring-boot-starter-aspectj`，无需新增依赖
- AgentScheduler 作为独立 `@Component`，构造时固定 seed，不影响现有 agent 逻辑

## 注意事项

- 生产发布前需替换 `LicenseLoader.PUBLIC_KEY_PEM` 为真实 RSA 公钥
- `License.reset()` 和 `License.activate()` 包级可见，仅供测试和 LicenseLoader 调用
- aaf-api 模块测试因本地无 PostgreSQL 连接失败，属于预存基础设施问题，与本次改动无关
