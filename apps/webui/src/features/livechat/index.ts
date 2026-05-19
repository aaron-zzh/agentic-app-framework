/**
 * Livechat Feature——在线客服与聊天模块
 *
 * 基于 assistant-ui 统一架构，三种 runtime：
 * - AgUiRuntime：AI 助理
 * - LivechatRuntime：在线客服（WebSocket）
 * - IMRuntime：内部即时通讯（占位）
 *
 * @author AaronZZH & Kiro
 */

export { LivechatProvider } from "./LivechatProvider"
export { LivechatPanel } from "./LivechatPanel"
export type {
  RuntimeType,
  RuntimeConfig,
  AgUiRuntimeConfig,
  LivechatRuntimeConfig,
  IMRuntimeConfig
} from "./runtime"
export { DEFAULT_AG_UI_CONFIG } from "./runtime"
