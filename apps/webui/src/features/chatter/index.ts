/**
 * Chatter Feature——统一对话组件
 *
 * 子模块：
 * - runtime/   运行时（AgUi / Livechat 分发）
 * - thread/    消息列表（Markdown、Reasoning、ToolCall、ActionBar 等）
 * - composer/  输入区
 * - toolbar/   工具栏
 * - layout/    布局容器（Panel、Dialog、GlobalChatter、FloatingButton）
 * - task/      任务面板（TaskBoard、ToolConfirm、RecoveryNotification）
 * - omni/      实时语音面板
 * - dnd/       拖放
 * - hooks/     hooks
 *
 * @author AaronZZH & Kiro
 */

export { Chatter } from "./Chatter"
export { ChatterComposer } from "./composer"
export { ContextChip, DraggableItem, DroppableComposer, useSemanticDraggable } from "./dnd"
export { useChatterLayoutPreference, useTaskBoard } from "./hooks"
export {
  ChatterLayout as ChatterLayoutContainer,
  ChatterPanel,
  FloatingChatterButton,
  GlobalChatter,
  GlobalChatterDialog
} from "./layout"
export { OmniRealtimePanel } from "./omni"
export { ChatterRuntime } from "./runtime"
export {
  RecoveryNotification,
  TaskBoardPanel,
  TaskExecutionTimeline,
  ToolConfirmOverlay
} from "./task"
export { ChatterThread, MarkdownText } from "./thread"
export { ChatterToolbar } from "./toolbar"
export type {
  ChatterDropItem,
  ChatterLayout,
  ChatterPreset,
  ChatterProps,
  ChatterTarget
} from "./types"
