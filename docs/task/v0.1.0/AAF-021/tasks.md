# 技术任务：Auto Dev 平台（AAF-021）

> 需求：[requirement.md](requirement.md)
> 设计：[后端技术选型](../../../design/apps/service/tech-stack.md) | [Actor 模型](../../../design/framework/intelligent/actor.md)
> 负责人：待分配 | 创建：05-06

## 任务列表

### US-1/US-2：监控与代码生成（MVP）

1. [ ] #1 Auto Dev 模块骨架（controller/service/domain/repository）
   verify: 模块编译通过，Spring Boot 启动无报错
2. [ ] #2 多智能体代码生成接口（规划→编码→审查三阶段）
   verify: POST /api/auto-dev/generate 返回生成结果
3. [ ] #3 kiro-cli 事件上报接口 + SSE 实时推送
   verify: POST /api/monitor/events 写入数据库，GET /api/monitor/stream 推送事件
4. [ ] #4 前端 Auto Dev 监控面板
   verify: 页面展示执行状态和日志流

### US-3：任务调度引擎

5. [ ] #5 任务状态机（queued→dispatched→running→completed/failed/cancelled）
   verify: 状态流转单测全绿
6. [ ] #6 并发策略（skip/queue/replace）
   verify: 并发场景单测覆盖三种策略
7. [ ] #7 Sweeper 孤儿回收
   verify: 超时任务被标记 failed
8. [ ] #8 Session Resumption（agent+issue 复用 session）
   verify: 中断后恢复到上次状态
9. [ ] #9 Autopilot 触发（cron/webhook/API × create_issue/run_only）
   verify: 三种触发方式单测通过

### US-4：Agent Skill 知识沉淀

10. [ ] #10 Skill 格式规范定义 + 存储模型
    verify: Skill CRUD 接口可用
11. [ ] #11 经验提取（执行历史 → 可复用模式识别）
    verify: 从执行日志中提取出至少一个 Skill
12. [ ] #12 知识注入（按任务上下文匹配加载）
    verify: 新任务启动时自动加载相关 Skill

### US-5：多 Agent 并行隔离

13. [ ] #13 Worktree 管理（创建/回收 + 独立 .env）
    verify: 创建 worktree 后独立端口和数据库可用
14. [ ] #14 动态 Profile 命名（slug+hash）
    verify: 多 worktree 不冲突

### US-6：Assistant Skill 系统

15. [ ] #15 Skill 注册表 + 动态加载
    verify: 注册 Skill 后按意图匹配可发现
16. [ ] #16 Skill 组合编排
    verify: 多 Skill 组合执行单测通过
17. [ ] #17 Agent 调度（Skill → Agent 派发）
    verify: Assistant 选定 Skill 后正确派发 Agent

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->
