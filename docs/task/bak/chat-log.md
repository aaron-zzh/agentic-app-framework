# AI 开发提示词记录

## 2026-5-20

- [x] 下面继续完成 AAF-020， 首先调整需求改为协作开发功能，结合我们019已完成的文档管理，实现前端auto-dev 通过live-chatter（弹窗，基于assisten-ui） + react-flow（左边是大纲层级右边是图，多层级可视化查看编辑文档，支持实时文档变更通知），后端在apps\service\aaf-auto-dev 中实现，同步本地开发文档到系统，支持前端修改后存储到本地文档，并支持新建。是否参考文档系统新建一个数据库表。然后需要能建立一个运行时，通过脚本与 kiro-cli 进行交互，把用户的指令发送给kiro(可制定采用哪个agent执行)，把返回实时通知给用户。可以模拟一个 ag-ui agent或者模拟一个内部用户来执行（也就是通过agui或者通过类似用户实时聊天的功能来实现，需要决策，还有检查我们的 live-chatter是否已经按设计文档实现，是否同时支持 agui+多用户聊天）。调整需求，建立拆分开发任务，协调前后端完成开发。你负责检查并提交，有错误的你负责修改。可以先查看相关设计文档全面了解后再调整需求及拆分任务。

## 2026-5-22 对话协作开发 + 实时对话组件重构

- [x] auto-dev 是否支持全部对话记录，可以按用户及AI回复进行过滤，并且关联生成的文档，按文档及源码路径查看对话记录。是否扩展一下当前的 开发文档模型 支持
- [x] 普通对话如何持久化存储，区分 AI对话消息（各类用户输入+AI回复）、聊天消息、会话（上下文）
- [x] 前端 Chatter 接口：当前是统一接口后端路由，是否把 auto-dev 与其他的分开单独走一个接口，我记得后端也是分开实现的吧，其他走默认。
- [x] chatter 添加实时 AI 语音对话功能，AG-UI协议是否支持，还是当前先实现语音输入完后返回带语音的消息。
- [x] 如何审查，前端可加载查看代码；方便快速审查反馈最近产出；风险项目放到单独文档自动提醒
- [x] 支持拖放界面中的参考文档/图片/文字到对话框。我记得加了页面及组件的元数据管理吧，当前是否支持？建议如何实现，给主要组件封装一层？
- [x] 需要支持异步创建任务，完成后自动领取任务。

## 2026-5-22 五层架构+智能体分层架构设计

- [x] 在 `docs\design\framework\execution-flow-diagram.md` 的每个图表前我加了评论，你进行回复，给出修改建议。

## 2026-5-29 功能补全

- [x] 参考tmp/nextjs/xueji/apps/demo 中的3d展示 添加实例页面，2. 检查v0.7是否已完成功能是否完整，补全并标记任务

## 规范类

- [ ] 添加一条开发规范：后续开发都先确认是否有设计文档，且分析是否符合整体设计，根据需要修改或创建设计文档，然后在开始开发，所有源码开头都要记录参考的设计文档（功能设计 + 技术方案）

## 2026-5-24 AIGC

- [ ] deepseek  V4 测试

## 待补全清单

v0.4 智能体与五层智能架构

AAF-048 Core 内核层

- DB 迁移脚本：ai_model、prompt_template、token_usage_record 三张表
- SpringAiLlmClient 实现（对接 Spring AI ChatClient）
- Token 用量告警 + 对话维度统计
- Prompt A/B 测试机制

AAF-049 Cognition 认知层

- DB 迁移脚本：long_term_memory、procedural_memory 两张表
- 分层违规修正：LongTermMemory/ProceduralMemory 改调 AtomMemoryEngine
- GraphMemoryNode JPA → Neo4j @Node 注解修正
- 记忆压缩/衰减定时任务
- Neo4j 双时态模型 + 连接池配置

AAF-050 Agent 智能体层

- MCP 服务器实际连接（当前占位）
- Spring AI ToolCallback 适配器
- 工具调用审计日志
- Agent 可视化配置前端页面
- AgentPool.reset() 实现
- SpringAiLlmClient / AgentScopeLlmClient 实现
- DB 迁移脚本（agent 相关表）

AAF-051 Assistant 助理层

- LLM 驱动意图分类（替代当前规则匹配）
- 多轮意图跟踪 + 消歧 + 槽位填充
- LLM 情感分析（替代关键词匹配）
- 情感历史追踪
- 负载均衡 / 优先级队列
- 旧 AssistantService 与新 DefaultAssistantExecutor 统一

AAF-052 Team 协作层

- A2A 实际网络通信（当前 sendMessage 只打日志）
- 团队持久化（当前 ConcurrentHashMap → DB）
- 团队 CRUD API + Controller
- 任务依赖图 DAG 执行引擎
- LLM 任务拆解
- DB 迁移脚本（team 相关表）

──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

v0.7 Auto Dev

AAF-067 CI/CD 集成

- CI pipeline 触发 API（调用 GitHub Actions / GitLab CI）
- GitHub Webhook 回调处理（构建状态通知）
- 构建状态查询接口
- 自动部署触发（环境 + 策略配置）
- 前端 CI 状态面板

──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

v0.11 整合验收与 Beta 发布

AAF-083 全链路联调

- 支付流程端到端集成测试
- 渠道消息收发集成测试
- 客服会话集成测试
- 统计采集集成测试
- E2E 测试（Playwright 引入 + 核心流程覆盖）
- 前端验收测试

AAF-084 文档与示例

- 支付接入指南
- 渠道配置指南
- AGUI 使用教程
- 画板/日历视图使用说明
- API Reference 文档（OpenAPI 导出）
- 示例项目（quickstart demo）

AAF-085 质量加固

- 后端新模块单元测试（pay/billing/channel/livechat/stats）
- 前端新组件单测（calendar/canvas/agui）
- ArchUnit 规则激活
- 性能/压力测试
