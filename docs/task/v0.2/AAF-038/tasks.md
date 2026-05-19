---
level: Practice
layer: Product
purpose: AAF-038 认证与登录的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 认证与登录（AAF-038）

> 负责人：architect + developer-service + developer-webui | 创建：05-19

## 任务列表

### 后端认证

1. [ ] #3801 用户实体与数据模型
   - 用户表（sys_user）、角色表、用户-角色关联表
   - Flyway 迁移脚本
   - verify: 迁移执行成功，表结构正确

2. [ ] #3802 邮箱注册与登录
   - 注册接口（邮箱+密码+验证码）、登录接口
   - 密码加密（BCrypt）、邮箱验证码发送
   - verify: 注册→验证→登录流程通过

3. [ ] #3803 JWT 认证机制
   - JWT 生成/验证/刷新、Access Token + Refresh Token 双 Token
   - Token 黑名单（Redis）、多端会话管理
   - verify: Token 签发/刷新/失效全流程通过

4. [ ] #3804 OAuth 第三方登录
   - 微信开放平台 OAuth、企业微信 OAuth、钉钉 OAuth
   - 统一 OAuth 回调处理、账号绑定/解绑
   - verify: 至少微信 OAuth 流程跑通

### 前端认证

5. [ ] #3805 登录注册页面
   - 登录页（邮箱+密码/第三方）、注册页、忘记密码
   - Token 存储与自动刷新、路由守卫
   - verify: 前端登录→跳转→Token 刷新流程通过
