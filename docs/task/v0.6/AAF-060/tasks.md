---
level: Practice
layer: Product
purpose: AAF-060 智能体编排的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 智能体编排（AAF-060）

> 负责人：architect + developer-service + developer-webui | 创建：05-19

## 任务列表

### 节点定义

1. [ ] #6001 智能体节点
   - Agent 节点定义（选择已注册 Agent、配置 Prompt 覆盖）
   - 模型绑定（选择 LLM 模型、温度/Token 参数）
   - 工具绑定（从 MCP 工具列表选择可用工具）
   - 输入/输出映射（上游变量→Agent 输入、Agent 输出→下游变量）
   - verify: Agent 节点执行时正确调用指定 Agent 并传递参数

2. [ ] #6002 知识库与工具节点
   - 知识库检索节点（选择知识库、配置 Top-K、阈值）
   - MCP 工具调用节点（选择工具、参数映射、超时配置）
   - HTTP 请求节点（URL/Method/Headers/Body 配置、响应解析）
   - 代码执行节点（JavaScript/Python 代码片段、沙箱执行）
   - verify: 各类节点独立执行正确，输出格式符合预期

3. [ ] #6003 LLM 节点
   - LLM 直接调用节点（无 Agent 包装，纯 Prompt→Response）
   - 模型选择与参数配置（模型/温度/最大 Token/停止词）
   - 流式输出支持（SSE 推送中间结果）
   - 结构化输出（JSON Schema 约束、输出解析）
   - verify: LLM 节点调用成功，流式输出正常推送

### 控制流与运行时

4. [ ] #6004 控制流节点
   - 条件分支（IF/ELSE、Switch/Case，支持表达式和 LLM 判断）
   - 循环节点（ForEach 遍历列表、While 条件循环、最大迭代限制）
   - 并行节点（Fork 并行执行多分支、Join 等待全部/任一完成）
   - 等待节点（等待外部事件/人工输入/定时触发）
   - verify: 控制流节点逻辑正确，循环有终止保护

5. [ ] #6005 编排运行时
   - DAG 拓扑排序执行引擎（检测循环依赖、确定执行顺序）
   - 节点间数据传递（上下文变量池、类型转换）
   - 错误处理与重试（节点级重试策略、全局错误处理器、降级路径）
   - 执行日志（每节点输入/输出/耗时/状态记录）
   - verify: 复杂 DAG 正确执行，错误节点触发重试或降级