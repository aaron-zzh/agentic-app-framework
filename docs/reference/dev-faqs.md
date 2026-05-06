---
level: Practice
layer: Product
purpose: 解答 AAF 框架开发中的常见疑问
status: published
version: 1.0.0
date: 2026-03-30
author: AaronZZH
scope:
  includes:
    - 模块化设计疑问
    - 业务开发指引
    - 框架能力使用
gains:
  - 能快速解决开发中的常见困惑
---

# 开发者常见问题

> 面向框架开发者和业务开发者的技术问题解答。用户使用问题见 [用户常见问题](user-faqs.md)。

## Q1: 为什么要分这么多模块？

**A**: 模块化设计有以下优势：

- 职责清晰，易于维护
- 框架代码和业务代码分离
- 用户可按需引入依赖
- 团队可并行开发
- 框架升级不影响业务代码

## Q2: 我应该在哪里写业务代码？

**A**: 所有业务代码都在 `aaf-modules` 目录下开发：

- 使用现有模块（system、agent、workflow、knowledge）
- 或创建新模块（如 aaf-module-order）

## Q3: 如何使用框架提供的能力？

**A**: 直接注入框架服务即可：

```java
@Autowired
private AgentService agentService;

@Autowired
private WorkflowEngine workflowEngine;
```

## Q4: 是否可以不使用某些模块？

**A**: 可以，在 `aaf-server/pom.xml` 中移除不需要的模块依赖即可。

## Q5: 如何启用 AI 自动开发功能？

**A**: 在 `application.yml` 中配置：

```yaml
aaf:
  auto-dev-enabled: true
```
