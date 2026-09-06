---
level: Practice
layer: Product
purpose: AAF-114 助理对话框体验优化需求规格
status: active
version: 1.0.0
date: 2026-09-05
author: AaronZZH & Kiro
tags:
  - AAF-114
  - Chatter
  - assistant-ui
related:
  - ../../backlog.md
  - ../AAF-106/tasks.md
  - ../AAF-107/tasks.md
---

# AAF-114 助理对话框体验优化

## 用户故事

作为 AAF 用户，我希望在统一助理对话框中明确看到当前助理与角色，并能便捷选择模型、添加图片或文本附件、控制计划与思考信息的展示，以便在不理解底层执行细节的情况下完成多模态对话和任务跟踪。

## 范围

- 标题栏展示“助理名称 · 角色”，并按 Assistant 分组切换 Role。
- 模型自动选择入口精简为带图标的“自动”，完整模型列表保持可用。
- Composer 支持按钮、拖放和粘贴添加图片与文本文件。
- 提供“计划”和“思考”两个展示偏好开关。
- 保留输入、长文本上下文、语音、发送、停止、会话和任务进度能力。

## 状态与安全边界

- Role 选择是 `{assistantId, roleKey}` 原子选择；请求目标与服务端 `aaf.role.resolved` 结果语义独立，不互相覆盖。
- 原生 assistant-ui attachment 是文件附件唯一状态源；既有 `ChatterDropItem` 仅表示应用上下文，不扩展为第二套文件上传状态。
- 图片先上传现有文件服务，服务端从 AG-UI 消息解析文件 key，再复用 `VisionMediaResolver`；文本附件按 assistant-ui 文本附件协议进入用户输入。
- “计划”仅控制后端 TaskBoard/ExecutorPlan 进度的显示，不强制服务端规划，不创建本地计划事实。
- “思考”仅控制运行时允许公开的 reasoning 摘要显示；AAF-106 对原始思维链的服务端拦截保持不变。
- TaskBoard、ExecutorPlan、Assistant、Role、Model 与 reasoning 均复用既有权威状态，不新增平行 store。

## 验收标准

### AC1 助理与角色选择

```gherkin
场景: 用户选择助理角色
  假如可用助理接口返回多个 Assistant 及其 Role
  当用户在对话框标题栏选择一个角色
  那么标题栏显示“助理名称 · 角色名称”
  并且下一次请求同时携带该 Assistant ID 与 Role key
  并且切换角色时清除未重新校验的 Skill 选择
```

### AC2 模型自动选择

```gherkin
场景: 使用自动模型
  当任务模型模式为 AUTO
  那么 Composer 显示带自动图标的“自动”入口
  并且用户仍可展开完整模型列表并选择显式模型
```

### AC3 图片和文本附件

```gherkin
场景: 添加受支持附件
  当用户通过按钮、拖放或粘贴添加图片或文本文件
  那么 Composer 展示可移除的附件项
  并且发送时文本内容或图片文件 key 被序列化到真实执行请求
  并且不支持的文件类型或超限文件不会被发送
```

### AC4 计划展示偏好

```gherkin
场景: 隐藏计划进度
  当用户关闭“计划”开关
  那么后端 TaskBoard/ExecutorPlan 状态仍保持不变
  但是对话框不展示任务计划进度面板
```

### AC5 安全思考展示

```gherkin
场景: 开启思考展示
  当用户开启“思考”开关
  那么运行时仅显示服务端允许公开的 reasoning 内容
  并且不会展示或承诺展示模型原始思维链
```

### AC6 紧凑布局

```gherkin
场景: 窄浮窗使用 Composer
  假如对话框宽度为 320px
  那么角色、模型、附件和模式控件不产生横向滚动
  并且语音、发送与停止能力仍可操作
```
