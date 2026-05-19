/**
 * Livechat Runtime 层——三种 runtime 导出
 *
 * - AgUiRuntime：AI 助理（对接 /api/chat SSE）
 * - LivechatRuntime：在线客服（对接 WebSocket 后端）
 * - IMRuntime：内部即时通讯（占位）
 *
 * @author AaronZZH & Kiro
 */

export type RuntimeType = "ag-ui" | "livechat" | "im"

/**
 * AgUi Runtime 配置——AI 助理模式
 * 使用 assistant-ui 内置 AI runtime，对接 /api/chat SSE 端点
 */
export interface AgUiRuntimeConfig {
  type: "ag-ui"
  /** AI 对话 API 端点 */
  endpoint: string
}

/**
 * Livechat Runtime 配置——在线客服模式
 * 使用 ExternalStoreRuntime，对接 WebSocket 后端
 */
export interface LivechatRuntimeConfig {
  type: "livechat"
  /** 用户 ID */
  userId: string
  /** 会话 ID */
  sessionId: string
}

/**
 * IM Runtime 配置——内部即时通讯（占位）
 */
export interface IMRuntimeConfig {
  type: "im"
  /** 用户 ID */
  userId: string
  /** 对话对象 ID */
  peerId: string
}

export type RuntimeConfig = AgUiRuntimeConfig | LivechatRuntimeConfig | IMRuntimeConfig

/** 默认 AgUi 配置 */
export const DEFAULT_AG_UI_CONFIG: AgUiRuntimeConfig = {
  type: "ag-ui",
  endpoint: "/api/chat"
}
