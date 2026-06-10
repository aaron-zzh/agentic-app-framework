/**
 * Chatter Feature——统一对话组件
 *
 * 通过 preset 和 layout 控制行为和布局：
 * - preset: ai / kiro / livechat
 * - layout: panel / dialog / drawer
 *
 * @author AaronZZH & Kiro
 */

export { Chatter } from "./Chatter"
export { ContextChip } from "./dnd/ContextChip"
export { DraggableItem } from "./dnd/DraggableItem"
export { useSemanticDraggable } from "./dnd/useSemanticDraggable"
export { useChatterLayoutPreference } from "./hooks/use-chatter-layout-preference"
export { useTaskBoard } from "./hooks/use-task-board"
export { RecoveryNotification } from "./RecoveryNotification"
export { TaskBoardPanel } from "./TaskBoardPanel"
export type { ChatterDropItem, ChatterProps, ChatterTarget } from "./types"
