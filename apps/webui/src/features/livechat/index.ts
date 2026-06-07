/**
 * Livechat Feature——在线客服与聊天模块
 *
 * 基于 assistant-ui 统一架构，两种 runtime：
 * - AgUiChatProvider：AI 助理（AG-UI SSE 协议），供 chatter/FloatingAssistant 共用
 * - LivechatProvider：人与人对话（WebSocket，含客服/IM/用户间聊天）
 *
 * @author AaronZZH & Kiro
 */

// 布局（仅 KiroAgentDrawer 内部使用，不作为公开 API）
export { ChatLayout } from "./ChatLayout"
// 对话分支
export {
  BranchSwitcher,
  MessageEditComposer,
  MessageEditTrigger
} from "./components/BranchSwitcher"
export { CodeBlockCopyButton } from "./components/CodeBlockCopyButton"
export type { ErrorType } from "./components/ErrorMessage"
export { ErrorMessage } from "./components/ErrorMessage"
// 对话导出
export { ExportDialog } from "./components/ExportDialog"
// 文件附件
export type { FileItem } from "./components/FileAttachment"
export { FileAttachment } from "./components/FileAttachment"
export { FileUploadArea } from "./components/FileUploadArea"
export { ImageMessage } from "./components/ImageMessage"
// 消息渲染组件
export { MarkdownMessage } from "./components/MarkdownMessage"
export { ToolCallMessage } from "./components/ToolCallMessage"
// 面板
export { LivechatPanel } from "./LivechatPanel"
export { LivechatProvider } from "./LivechatProvider"
// Runtime
export type {
  AgUiRuntimeConfig,
  IMRuntimeConfig,
  RuntimeConfig,
  RuntimeType
} from "./runtime"
export { DEFAULT_AG_UI_CONFIG } from "./runtime"
export { AgUiChatProvider } from "./runtime/ag-ui-runtime"
export type { VoiceSettingsValue } from "./voice"
// 语音交互
export {
  AudioPlayer,
  AudioRecorder,
  RealtimeVoice,
  SpeechInput,
  SpeechOutput,
  VoiceSettings
} from "./voice"
