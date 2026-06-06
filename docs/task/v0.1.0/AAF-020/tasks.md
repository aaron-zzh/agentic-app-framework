---
level: Practice
layer: Product
purpose: AAF-020 协作开发功能技术任务清单
status: active
version: 1.0.0
date: 2026-05-22
author: AaronZZH
---

# 协作开发功能（AAF-020）

> 需求：[需求规格](requirement.md)
> 设计参考：[协作控制台设计](../../../design/framework/auto-dev/auto-dev.md) | Auto-Dev 模块设计（待建）
> 负责人：developer-service + developer-webui | 创建：05-22

## 执行策略

后端先行（Flyway 迁移 → KiroAgentController → 文档新建接口 → SSE 变更通知），前端并行（文档页面重构 → Kiro Agent 弹窗）。

---

## 后端主线（developer-service）

### US-1 文档协作编辑

1. [x] #02001 Flyway 迁移 V4（autodev_session 表 + 文档变更事件支持） — developer-service
   - 新增 `autodev_session` 表（session_id / agent_role / status / user_id / create_time / update_time）
   - 迁移文件：`V4__autodev_session.sql`，放在 `aaf-auto-dev` 模块的 `resources/db/migration/`

2. [x] #02002 文档新建接口（POST /api/docs） — developer-service
   - 在 `DocumentController` 新增 `POST /api/docs` 端点
   - 参数：`DocCreateDTO`（title, filePath, docType, content）
   - 逻辑：写入本地 `docs/` 文件 + 插入 `doc_document` 表 + 提取链接关系
   - 路径安全校验：filePath 必须以 `docs/` 开头，防止路径穿越

3. [x] #02003 文档变更 SSE 端点（GET /api/docs/events） — developer-service
   - 在 `DocumentController` 新增 `GET /api/docs/events` SSE 端点
   - 维护 `ConcurrentHashMap<Long, List<SseEmitter>>` 按文档 ID 注册订阅者
   - `DocumentService.update()` 保存后广播 `{type: "doc_updated", docId, title}` 事件
   - 连接超时：5 分钟；断开时自动从注册表移除

### US-2 Kiro Agent 运行时

4. [x] #02004 KiroAgentController（/api/autodev/kiro/run） — developer-service
   - 在 `aaf-auto-dev` 模块新建 `com.xuejiai.aaf.autodev.agent.KiroAgentController`
   - 实现 AG-UI 协议端点 `POST /api/autodev/kiro/run`（与 `AgUiChatController` 接口格式一致）
   - 请求体：`KiroRunRequest`（threadId, messages, agentRole, state）
   - 通过 `ProcessBuilder` 调用 kiro-cli：`kiro chat --agent {agentRole} --message "{content}"`
   - 读取 stdout 逐行转为 AG-UI SSE 事件（`TEXT_MESSAGE_CONTENT` / `RUN_FINISHED` / `RUN_ERROR`）
   - 超时：5 分钟；进程结束后发送 `RUN_FINISHED` 事件

5. [x] #02005 KiroAgentController 辅助接口 — developer-service
   - `GET /api/autodev/kiro/agents`：返回可用 agent 角色列表（从 `.kiro/agents/` 目录扫描 yaml 文件名）
   - `GET /api/autodev/kiro/sessions`：查询 `autodev_session` 表，返回历史会话列表
   - `AutodevSessionRepository`（JPA）+ `AutodevSession` Entity

---

## 前端主线（developer-webui）

### US-1 文档页面重构

6. [x] #02006 /workspace/docs 页面重构（左侧大纲 + 右侧内容/图谱） — developer-webui
   - 重构 `apps/webui/src/app/(workspace)/docs/page.tsx`
   - 布局：`ResizablePanelGroup`（左侧大纲树 25% + 右侧内容区 75%）
   - 左侧：多级折叠文档树（复用现有 tree API），支持按 docType 分组展示
   - 右侧：Tab 切换（"内容"Tab 展示 Markdown 渲染 + 编辑按钮 / "关系图"Tab 展示 React Flow）
   - 新建文档按钮：弹出 `DocCreateDialog`（标题、路径、类型），调用 `POST /api/docs`

7. [x] #02007 文档变更实时通知（SSE 订阅） — developer-webui
   - 新建 `useDocEvents(docId)` hook，订阅 `GET /api/docs/events?docId={id}`
   - 收到 `doc_updated` 事件时：invalidate TanStack Query 缓存（`queryClient.invalidateQueries`）
   - 显示 toast 提示："文档已更新，已自动刷新"
   - 组件卸载时关闭 SSE 连接

### US-2 Kiro Agent 弹窗

8. [x] #02008 Kiro Agent 聊天弹窗（drawer 模式） — developer-webui
   - 新建 `KiroAgentDrawer.tsx`，使用 `AgUiChatProvider` 配置端点为 `/api/autodev/kiro/run`
   - 弹窗顶部：agent 角色选择器（下拉，调用 `GET /api/autodev/kiro/agents`）
   - 选择角色后，将 `agentRole` 通过 `state` 字段传入 AG-UI 请求
   - 使用 `ChatLayout drawer={true}` 作为内部布局
   - 在 `/workspace/docs` 页面右下角添加悬浮按钮触发弹窗

---

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| architect（技术设计） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及 |
| developer（编码） | 1 | 05-22 | ✅ CLEAR | 🔴 是 |
| architect（代码审查） | 1 | 05-22 | ✅ CLEAR（blocker=0, major=0） | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->
