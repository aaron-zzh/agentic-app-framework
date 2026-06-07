# ActivityStream 活动流设计

> 对标 Odoo Chatter，AAF 实体详情页的协作与沟通组件。

## 定位

ActivityStream 是挂载在任意实体详情页底部的协作面板，聚合三类信息：

- **系统日志**：字段变更、状态流转、创建等自动记录
- **人工评论**：用户手动留言，支持 @提及
- **待办活动**：跟进任务（电话/邮件/会议/待办），可分配、可标记完成

## 当前实现

### 文件位置

| 文件 | 职责 |
|------|------|
| `features/entity-engine/components/ActivityStream.tsx` | UI 组件（全部子组件内联） |
| `lib/api/rest/entity/activity.ts` | API 客户端 + 类型定义 |
| `lib/queries/use-activities.ts` | TanStack Query Hooks |

### 组件结构

```
ActivityStream
├── Tab: 全部
│   ├── CommentInput       评论输入框
│   └── ActivityTimeline   混合时间线（评论 + 系统日志）
├── Tab: 评论
│   ├── CommentInput
│   └── ActivityTimeline（filterType="comment"）
└── Tab: 待办
    ├── ScheduleInput      新建待办活动
    └── ScheduleList       待办列表
```

### 数据模型

```typescript
// 活动流条目（只读，系统自动写入或评论写入）
interface ActivityItem {
  id: string
  type: "create" | "update" | "status_change" | "comment" | "schedule"
  entityType: string
  entityId: string
  actorId: string
  actorName: string
  actorAvatar?: string
  content?: string
  changes?: { field: string; label: string; oldValue: unknown; newValue: unknown }[]
  mentions?: string[]   // 被 @ 的用户 ID 列表
  createdAt: string
}

// 待办活动（可读写）
interface ScheduledActivity {
  id: string
  type: "call" | "email" | "meeting" | "todo"
  title: string
  assigneeId: string
  dueDate: string
  done: boolean
}
```

### 使用方式

```tsx
// 在 FormView 底部挂载
<ActivityStream entityType="order" entityId={record.id} />
```

### 通知链路（已预留）

`mentions` 字段通过 `/comments` 传给后端，后端写 `type="mention"` 的通知记录，被提及用户在通知中心收到。前端通知类型定义已包含 `"mention"`，链路已打通，UI 选人功能待实现。

## 待开发功能

### 🔴 高优先级

**@提及选人 UI**

当前状态：`mentions` 字段已在 API 层预留，但评论框只是普通 `Textarea`，没有 `@` 触发下拉选人。

方案：参考 `features/rich-text-editor/plugins/MentionPlugin.tsx` 的实现逻辑，在 `CommentInput` 的 `Textarea` 上监听 `@` 字符，弹出用户搜索下拉，选中后插入 `@用户名` 并收集 userId 到 `mentions` 数组。

---

**附件上传**

当前状态：评论无法携带文件/图片。

方案：在 `CommentInput` 下方加附件按钮，复用 `components/upload/Upload.tsx`，上传后将附件 URL 追加到评论内容或单独的 `attachments` 字段（需后端扩展 `ActivityItem`）。

### 🟡 中优先级

**评论编辑**

当前只能删除，不能修改。需在 `ActivityItemRow` 加编辑入口，后端补 `PUT /comments/:id`。

---

**待办分配给他人**

`assigneeId` 当前写死 `"current-user"`，`ScheduleInput` 无选人控件。需加用户选择器，复用 `use-relationship-picker.ts` 的选人逻辑。

---

**待办逾期样式**

截止日期已过但未完成时，缺少红色警告标识。在 `ScheduleList` 的渲染逻辑里判断 `new Date(s.dueDate) < new Date() && !s.done` 即可加样式。

---

**分页 / 加载更多**

活动记录多时全量加载性能差。需在 `useActivities` hook 中加 `page`/`pageSize` 参数，UI 加"加载更多"按钮或虚拟滚动。

### 🟢 低优先级

**表情回应（Reaction）**

Odoo 支持 emoji reaction，AAF 暂无。需后端新增 `reactions` 表 + 前端气泡 UI。

---

**内部备注 vs 外部评论**

Odoo 区分"日志备注"（仅内部可见）和"发送消息"（通知相关人）。AAF 目前统一为评论，如需区分需扩展 `ActivityType` 加 `note` 类型，并在权限层过滤。

---

**关注者（Followers）打通**

项目已有 `SubscribeButton` 组件，但尚未与 ActivityStream 集成。打通后：评论/状态变更时自动通知关注者。

---

**邮件/消息发送集成**

Odoo 的留言可触发邮件通知。AAF 侧在有外部渠道集成（钉钉/企微/邮件）后，可从评论发送时触发通知下发。

## 扩展原则

- ActivityStream 当前与 entity-engine 强绑定，暂不独立为 feature
- 功能扩展到其他模块（文档详情、工单等）复用时，再提取为独立 feature
- 后端自动写入系统日志（create/update/status_change）的触发逻辑由各业务模块的 Service 层负责，ActivityStream 只负责展示
