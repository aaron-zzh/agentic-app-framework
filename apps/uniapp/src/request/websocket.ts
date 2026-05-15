/**
 * WebSocket 封装（基于 @hyoga/uni-socket.io，Socket.IO 协议）
 *
 * 版本对应：@hyoga/uni-socket.io 3.x ↔ 服务端 socket.io 4.x
 */
import io from '@hyoga/uni-socket.io'

export interface WebSocketOptions {
  /** 连接成功回调 */
  onConnect?: () => void
  /** 断开连接回调 */
  onDisconnect?: (reason: string) => void
  /** 错误回调 */
  onError?: (err: unknown) => void
}

export interface WebSocketInstance {
  /** 监听事件 */
  on: (event: string, handler: (data: unknown) => void) => void
  /** 发送事件 */
  emit: (event: string, data?: unknown) => void
  /** 断开连接 */
  disconnect: () => void
  /** Socket.IO 连接 ID */
  readonly id: string
}

/**
 * 创建 WebSocket 连接
 * @param url - 服务端地址（ws:// 或 wss://）
 * @param token - 认证 token，通过 query 传递
 * @param options - 回调选项
 */
export function createWebSocket(
  url: string,
  token: string,
  options: WebSocketOptions = {},
): WebSocketInstance {
  const socket = io(url, {
    query: { token },
    transports: ['websocket'],
    timeout: 10000,
    reconnection: true,
    reconnectionAttempts: 5,
    reconnectionDelay: 3000,
  })

  socket.on('connect', () => {
    options.onConnect?.()
  })

  socket.on('disconnect', (reason: string) => {
    options.onDisconnect?.(reason)
  })

  socket.on('error', (err: unknown) => {
    options.onError?.(err)
  })

  return {
    on: (event, handler) => socket.on(event, handler),
    emit: (event, data) => socket.emit(event, data),
    disconnect: () => socket.disconnect(),
    get id() {
      return socket.id
    },
  }
}
