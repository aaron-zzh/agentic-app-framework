---
level: Practice
layer: Product
purpose: AAF-043 对话引擎的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 对话引擎（AAF-043）

> 负责人：architect + developer-service | 创建：05-19

## 任务列表

1. [ ] #4301 Spring AI 集成
   - Spring AI 依赖引入、ChatClient 配置
   - 多模型适配（OpenAI/Anthropic/本地模型）
   - 模型配置管理（API Key/Endpoint/参数）
   - verify: 调用 ChatClient 返回正常响应

2. [ ] #4302 流式输出
   - SSE 流式响应实现、Flux<String> 输出
   - 流式中断/取消支持
   - Token 使用量统计（流式场景）
   - verify: 前端可逐字接收流式响应

3. [ ] #4303 上下文窗口管理
   - 对话上下文组装（系统提示+历史+用户输入）
   - Token 计数与截断策略（滑动窗口/摘要压缩）
   - 上下文模板（不同场景不同系统提示）
   - verify: 长对话自动截断不超模型限制

4. [ ] #4304 对话历史持久化
   - 对话表（conversation）+ 消息表（message）设计
   - 对话 CRUD API、消息分页查询
   - 对话归档/删除
   - verify: 对话创建→发消息→查历史流程通过

5. [ ] #4305 多模型路由
   - 按场景/用户配置路由到不同模型
   - 模型降级策略（主模型失败切备用）
   - 模型调用计量与配额
   - verify: 路由规则生效，降级正常
