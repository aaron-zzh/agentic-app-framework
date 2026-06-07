# 开发记录：AAF-020 协作开发功能

执行者：AI/developer-webui

## Chatter 组件重构

✅ 05-22 — developer-webui

- 新建 `features/chatter/` 统一对话组件（10 文件），保留 `features/livechat/` 不删除
- 基于 `useAgUiRuntime` + `HttpAgent`，通过 target 类型映射到不同端点（fallback 模式）
- 支持三种布局（panel/dialog/drawer）+ 三种 preset（ai/kiro/livechat）
- 集成 @dnd-kit 拖放：DraggableItem 包装可拖元素，DroppableComposer 接收拖入附件
- 决策：`onInteractOutside` 不被 Base UI Dialog 支持，dialog 模式改用 `modal={false}` 替代

## 实现文件

| 文件 | 说明 |
|------|------|
| `features/chatter/types.ts` | 类型定义（ChatterPreset/Layout/Target/DropItem/Props） |
| `features/chatter/Chatter.tsx` | 主组件，组合 DnD + Runtime + Layout + Panel |
| `features/chatter/ChatterRuntime.tsx` | AG-UI runtime，按 target 映射端点 |
| `features/chatter/ChatterLayout.tsx` | 布局容器选择器（panel/dialog/drawer） |
| `features/chatter/ChatterPanel.tsx` | 面板组合（toolbar + thread + composer） |
| `features/chatter/ChatterToolbar.tsx` | 工具栏（TargetSwitcher + SessionManager） |
| `features/chatter/ChatterThread.tsx` | 消息列表（ThreadPrimitive） |
| `features/chatter/ChatterComposer.tsx` | 输入区 + 附件列表 |
| `features/chatter/dnd/DraggableItem.tsx` | 拖拽包装器 |
| `features/chatter/dnd/DroppableComposer.tsx` | 拖放接收区域 |
| `features/chatter/index.ts` | barrel export |

## 实现决策

- 后端统一端点 `/api/chat/run` 尚未上线，当前按 target.type 映射到现有端点作为 fallback
- Base UI ToggleGroup 无 `type="single"` prop，通过 `onValueChange` 取最后一个值模拟单选
- Base UI DialogContent 无 `onInteractOutside` prop，使用 `modal={false}` 实现非模式弹窗

## 注意事项

- `@dnd-kit/core` 已在 package.json 中（^6.3.1），无需额外安装
- 后端统一端点上线后，只需修改 `ChatterRuntime.tsx` 中的 `getEndpointUrl` 函数


## AssistantScopeRuntime 缓存优化

✅ 2026-06-07 — developer-service

- `AssistantScopeRuntime.materialize()` 加 Caffeine 缓存，key = `assistantId:configHash`
- configHash 由 assistant/actor/role 的 `updateTime` 拼接，任一配置变更 → key 变 → 自动 miss
- 缓存参数：maximumSize=500，expireAfterAccess=30min
- 记忆/知识库检索不受影响（`MemoryContextHook` 每轮动态执行）；技能/工具白名单/systemPrompt 随配置变更自动刷新
