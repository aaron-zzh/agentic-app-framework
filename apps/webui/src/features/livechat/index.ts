/**
 * Livechat Feature——在线客服与聊天模块
 *
 * 基于 assistant-ui 统一架构，三种 runtime：
 * - AgUiRuntime：AI 助理（AG-UI SSE 协议）
 * - LivechatRuntime：在线客服（WebSocket）
 * - IMRuntime：内部即时通讯（占位）
 *
 * @author AaronZZH & Kiro
 */

// 布局
export { ChatLayout } from "./ChatLayout"

// 面板
export { LivechatPanel } from "./LivechatPanel"
export { LivechatProvider } from "./LivechatProvider"

// 消息渲染组件
export { MarkdownMessage } from "./components/MarkdownMessage"
export { CodeBlockCopyButton } from "./components/CodeBlockCopyButton"
export type { ErrorType } from "./components/ErrorMessage"
export { ErrorMessage } from "./components/ErrorMessage"
export { ImageMessage } from "./components/ImageMessage"
export { ToolCallMessage } from "./components/ToolCallMessage"

// 文件附件
export type { FileItem } from "./components/FileAttachment"
export { FileAttachment } from "./components/FileAttachment"
export { FileUploadArea } from "./components/FileUploadArea"

// 对话分支
export { BranchSwitcher, MessageEditComposer, MessageEditTrigger } from "./components/BranchSwitcher"

// 对话导出
export { ExportDialog } from "./components/ExportDialog"

// 语音交互
export {
  SpeechInput,
  SpeechOutput,
  RealtimeVoice,
  VoiceSettings,
  AudioRecorder,
  AudioPlayer,
} from "./voice"
export type { VoiceSettingsValue } from "./voice"

// Runtime
export type {
  AgUiRuntimeConfig,
  IMRuntimeConfig,
  LivechatRuntimeConfig,
  RuntimeConfig,
  RuntimeType
} from "./runtime"
export { DEFAULT_AG_UI_CONFIG } from "./runtime"
export { AgUiChatProvider } from "./runtime/ag-ui-runtime"
