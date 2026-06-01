/**
 * Livechat Runtime 层——两种 runtime
 *
 * - AgUiRuntime：AI 助理（AG-UI SSE 协议，对接 /agui/runs）
 * - IMRuntime：人与人对话（WebSocket 双向，含客服/IM/用户间聊天）
 *
 * @author AaronZZH & Kiro
 */

export type RuntimeType = "ag-ui" | "im"

/**
 * AgUi Runtime 配置——AI 助理模式
 * 使用 useAgUiRuntime，对接 AgentScope /agui/runs 端点
 */
export interface AgUiRuntimeConfig {
  type: "ag-ui"
  /** 路由到哪个 Agent（对应 AafAguiRegistryCustomizer 注册的 agentId） */
  agentId?: string
}

/**
 * IM Runtime 配置——人与人对话模式
 * 使用 ExternalStoreRuntime，对接 WebSocket 后端
 * 涵盖：在线客服（livechat）、内部即时通讯（im）、用户间聊天（user）
 */
export interface IMRuntimeConfig {
  type: "im"
  /** 当前用户 ID */
  userId: string
  /** 会话 ID */
  sessionId: string
  /** 会话类型：livechat=在线客服 / im=内部通讯 */
  sessionType?: "livechat" | "im"
}

export type RuntimeConfig = AgUiRuntimeConfig | IMRuntimeConfig

/** 默认 AgUi 配置 */
export const DEFAULT_AG_UI_CONFIG: AgUiRuntimeConfig = {
  type: "ag-ui"
}
