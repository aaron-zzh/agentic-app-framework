/**
 * Yjs Doc + y-websocket provider 封装
 * @author AaronZZH & Kiro
 *
 * 需要安装依赖：
 *   pnpm add yjs y-websocket @lexical/yjs
 */

import { WebsocketProvider } from "y-websocket"
import * as Y from "yjs"

/** 根据 userId 生成稳定的 HSL 颜色 */
function userIdToColor(userId: string): string {
  let hash = 0
  for (let i = 0; i < userId.length; i++) {
    hash = userId.charCodeAt(i) + ((hash << 5) - hash)
  }
  const hue = Math.abs(hash) % 360
  return `hsl(${hue}, 70%, 45%)`
}

export interface YjsProviderConfig {
  docId: string
  userId: string
  userName: string
}

export interface YjsProviderInstance {
  doc: Y.Doc
  provider: WebsocketProvider
  destroy: () => void
}

/**
 * 创建 Yjs Doc 和 WebSocket provider
 * WS 地址：ws(s)://{host}/ws/yjs/{docId}
 */
export function createYjsProvider(config: YjsProviderConfig): YjsProviderInstance {
  const { docId, userId, userName } = config

  const doc = new Y.Doc()

  // 根据当前页面协议决定 ws/wss
  const protocol = typeof window !== "undefined" && window.location.protocol === "https:" ? "wss:" : "ws:"
  const host = typeof window !== "undefined" ? window.location.host : "localhost:3000"
  const wsUrl = `${protocol}//${host}/ws/yjs`

  const provider = new WebsocketProvider(wsUrl, docId, doc)

  // 设置用户感知信息（光标颜色 + 名称）
  const color = userIdToColor(userId)
  provider.awareness.setLocalStateField("user", {
    name: userName,
    color,
    colorLight: `${color.slice(0, -1)}, 0.2)`.replace("hsl", "hsla"),
  })

  return {
    doc,
    provider,
    destroy: () => {
      provider.destroy()
      doc.destroy()
    },
  }
}
