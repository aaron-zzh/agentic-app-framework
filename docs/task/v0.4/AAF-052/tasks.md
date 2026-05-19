---
level: Practice
layer: Product
status: in-progress
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# Team 协作层（AAF-052）

## 技术任务

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| #5201 | 多 Assistant 编排 | 团队定义、角色分配、协作模式 | 🟡 部分完成 |
| #5202 | A2A 协议实现 | Agent-to-Agent 通信协议、消息格式、路由 | 🔴 框架占位，核心未实现 |
| #5203 | 任务分发 | 任务拆解、子任务分配、依赖管理 | 🟡 部分完成 |
| #5204 | 进度同步 | 执行状态广播、进度汇报、超时检测 | ✅ 已完成 |
| #5205 | 冲突仲裁 | 结果冲突检测、投票机制、人工升级 | ✅ 已完成 |

## 已完成

- `TeamOrchestrator`：团队定义（TeamDefinition/TeamMember）、角色分配（leader/member）、协作模式（LEADER_COORDINATED/PEER_COLLABORATION）
- `A2AProtocolService`：端点注册、消息发送（占位）、按能力发现 Agent
- `TaskDistributor`：能力匹配分配子任务、依赖检查
- `ProgressSyncService`：进度报告 + 事件总线广播 + 超时检测
- `ConflictArbitrator`：多数投票 / 最高置信度 / 人工升级三级仲裁

## 待实现

### #5201 多 Assistant 编排（补充）

- [ ] `TeamOrchestrator` 接口：AAF Team 编排契约，当前类改名为 `DefaultTeamOrchestrator` 实现此接口
- [ ] 团队持久化（当前 `ConcurrentHashMap` 内存存储，重启丢失）
- [ ] 团队 CRUD API（REST 接口）
- [ ] 协作模式执行引擎：Leader 模式下 Leader 自动拆分任务并分发

### #5202 A2A 协议实现（核心补充）

- [ ] 实际网络通信实现（当前 `sendMessage` 只打日志返回占位响应）
- [ ] 基于 HTTP/gRPC 的远程 Agent 调用
- [ ] 消息序列化/反序列化协议
- [ ] 异步消息队列支持（非阻塞通信）
- [ ] 心跳检测 + 端点健康状态管理

### #5203 任务分发（补充）

- [ ] 任务依赖图执行引擎：按 DAG 拓扑序执行，前置完成后自动触发后续
- [ ] 任务拆解：LLM 驱动将大任务自动拆分为子任务
- [ ] 任务状态持久化（当前无持久化）
- [ ] 任务重试/补偿机制

### 通用补充

- [ ] 团队相关表迁移脚本（`ai_team` / `ai_team_member` / `ai_team_task`）
- [ ] 团队执行日志与审计
- [ ] 团队协作可视化（执行流程图、进度看板）
