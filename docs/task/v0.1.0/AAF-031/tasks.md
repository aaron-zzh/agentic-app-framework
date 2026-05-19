---
level: Practice
layer: Product
purpose: AAF-031 协作与通知的技术任务清单
status: done
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 协作与通知（AAF-031）

> 设计：[结构化交互模式设计](../../../design/apps/webui/interaction-mode-structured-view.md) 章节十三、十四、二十、二十九、三十三、四十六、五十四、五十五
> 负责人：architect + developer-webui + developer-service | 创建：05-13

## 任务列表

> **执行策略**：先建通知基础设施（Toast + 消息中心），再建协作能力（乐观锁 + 实时感知），最后叠加活动流和订阅。
> 前置：AAF-028 #9（FormView）+ AAF-029 #2（Mutation Hooks）完成。

### 通知基础设施

1. ✅ #3101 Toast 通知系统 — developer-webui
   - 集成 sonner 库
   - 实现 NotificationService：success / error / warning / info
   - 支持 action 按钮（如"撤销"）
   - 支持 duration 配置（0=不自动关闭）
   - verify: 保存成功/失败/删除撤销 Toast 正确展示

2. ✅ #3102 消息中心后端 — developer-service
   - notification 表：id / user_id / type / title / body / entity_type / entity_id / read / created_at
   - API：`GET /api/notifications`（分页+筛选）/ `PUT /api/notifications/read` / `DELETE`
   - 未读计数：`GET /api/notifications/unread-count`
   - verify: CRUD 接口正确，未读计数准确

3. ✅ #3103 消息中心前端 — developer-webui (依赖: #2)
   - AppHeader 🔔 图标 + 未读红点
   - 点击展开通知面板（最近 5 条 + [查看全部]）
   - `/workspace/notifications` 完整视图：Tab 分类 + 已读/未读 + 批量操作
   - 点击消息自动标为已读 + 跳转关联记录
   - verify: 通知列表正确展示，标记已读后红点更新

4. ✅ #3104 WebSocket 实时推送 — developer-service + developer-webui (依赖: #2)
   - 后端：WebSocket endpoint `/ws/notifications`
   - 消息产生时推送到在线用户
   - 前端：连接 WebSocket，收到消息更新未读计数 + 可选 Toast
   - 断线重连 + 心跳保活
   - verify: 后端创建通知后前端实时收到

5. ✅ #3105 通知偏好设置 — developer-webui + developer-service (依赖: #3)
   - `/workspace/settings/notifications` 页面
   - 按类别配置通知通道（站内/邮件/企微）
   - 免打扰时段设置
   - 后端存储 user_notification_preference 表
   - verify: 关闭某类通知后不再收到

### 实时协作

6. ✅ #3106 乐观锁冲突处理 — developer-webui + developer-service (依赖: AAF-029 #2)
   - 记录携带 version 字段，提交时后端校验
   - 版本不一致 → 前端弹出冲突对话框
   - 对话框展示"我的修改 vs 服务端最新"
   - 用户选择：覆盖 / 合并 / 放弃
   - verify: 模拟并发编辑触发冲突对话框

7. ✅ #3107 多人在线感知 — developer-webui (依赖: #4)
   - `useRecordPresence(entity, id)` Hook
   - WebSocket 广播：谁在编辑哪条记录
   - 表单顶部显示在线编辑者头像
   - 用户离开记录时广播 leave
   - verify: 两个浏览器同时打开同一记录，互相看到对方头像

8. ✅ #3108 richText CRDT 协同 — developer-webui (依赖: #7)
   - Yjs + Tiptap 集成
   - `fieldDef.collaboration: true` 启用
   - WebSocket provider 同步编辑状态
   - 光标位置 + 用户颜色标识
   - verify: 两人同时编辑富文本，内容实时同步无冲突

### 版本历史

9. ✅ #3109 版本快照后端 — developer-service
   - record_version 表：id / entity_type / entity_id / version / data(JSONB) / user_id / created_at
   - 每次保存自动生成快照（可配置 maxPerRecord）
   - API：`GET /api/{entity}/{id}/versions` / `POST /api/{entity}/{id}/versions/{v}/restore`
   - verify: 保存记录后版本列表递增

10. ✅ #3110 版本历史 UI + Diff View — developer-webui (依赖: #9)
    - FormHeader [版本历史] 按钮 → 侧边抽屉
    - 版本时间线（时间 + 操作人 + 摘要）
    - 选择两个版本 → 字段级 Diff 对比
    - diffRenderers：InlineDiff / BlockDiff / ValueChangeBadge
    - [恢复此版本] 按钮
    - verify: 对比两个版本差异正确高亮

### 活动流

11. ✅ #3111 活动流后端 — developer-service
    - activity_log 表 + comment 表 + scheduled_activity 表
    - 自动记录：创建/修改/状态变更（JPA EntityListener）
    - 评论 API：CRUD + @mentions 解析
    - verify: 修改记录后 activity_log 自动产生

12. ✅ #3112 活动流前端（Chatter） — developer-webui (依赖: #11)
    - 表单底部 ActivityStream 组件
    - 操作日志 + 评论区 + 活动调度混合时间线
    - 评论输入：Markdown + @提及用户搜索
    - 活动调度：安排待办（类型/负责人/截止时间）
    - verify: 评论发布后时间线正确展示

### 字段订阅与待办

13. ✅ #3113 字段变更订阅 — developer-webui + developer-service (依赖: #4, #11)
    - 表单 [👁 关注] 按钮 → 订阅配置弹窗
    - sys_subscription 表：user_id / entity_type / entity_id / fields / channels
    - 字段变更时匹配订阅者 → 生成通知
    - 列表中已关注记录显示 👁 图标
    - verify: 订阅字段变更后收到通知

14. ✅ #3114 @提及待办联动 — developer-webui + developer-service (依赖: #12, #2)
    - 评论中 @用户 → 自动创建 sys_todo 记录
    - sys_todo 表：assignee_id / title / source_type / source_entity / source_id / status / due_date
    - `/workspace/todos` 待办视图：待处理/已完成/已忽略
    - 支持 @团队/角色 → 组内所有人收到
    - verify: @某人后其待办列表出现新条目

### PWA

15. ✅ #3115 PWA 基础支持 — developer-webui
    - manifest.json 配置（name/icons/display:standalone）
    - Service Worker 缓存静态资源
    - 移动端"添加到主屏幕"
    - 推送通知：Notification API + 后端事件触发
    - verify: 移动端添加到主屏幕后可独立运行

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
