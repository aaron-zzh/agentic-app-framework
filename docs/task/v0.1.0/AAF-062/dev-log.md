# 开发记录：AAF-062 审批流程前端

执行者：AI/developer-webui

## 实现文件

| 文件 | 说明 |
|------|------|
| `apps/webui/src/lib/api/approval.ts` | 审批 API 客户端（对接 ApprovalController + WorkflowController） |
| `apps/webui/src/lib/queries/use-approval.ts` | TanStack Query hooks（待办/已办/时间线/投票/统计/加签/转签/撤回） |
| `apps/webui/src/features/flow-editor/nodes/approval/index.tsx` | 增强：审批人策略/超时/空审批人 Inspector + 会签节点 |
| `apps/webui/src/features/flow-editor/components/approval-panel.tsx` | 审批操作面板（操作按钮 + 意见 + 时间线 + 投票进度） |
| `apps/webui/src/app/(workspace)/workflow/page.tsx` | 审批列表页（待办/已办/我发起 + 统计卡片） |

## 实现决策

- API 路径对齐后端 `/api/system/workflow/approval/*` 和 `/api/system/workflow/tasks/my-pending`
- 使用 `request()` 统一封装（自动携带 JWT/org header），不用 workflow.ts 中的独立 `req()`
- 会签节点作为独立节点类型 `countersign` 注册，而非 userTask 的子模式，便于节点面板拖拽区分
- 审批操作面板独立于 WorkflowPanel，后者保留为简单场景使用

## 注意事项

- `currentUserId` 在 workflow/page.tsx 中为占位值 `"current-user"`，需接入 auth store
- 审批列表页的"处理"按钮暂未实现跳转逻辑，需后续对接表单详情页
- approval-panel 中的目标用户输入为纯文本，后续可替换为用户选择器组件
