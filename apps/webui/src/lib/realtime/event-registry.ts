/**
 * 全局事件订阅注册表：Provider 分发收到的事件，各模块按类型前缀订阅。
 *
 * 保留最近 64 条事件，避免"提交请求的响应返回前事件已到达"导致 waitForEvent 漏事件。
 * 事件只用于触发失效或状态展示，不得把载荷复制进 Zustand。
 * @author AaronZZH & Kiro
 */

import type { RealtimeEvent } from "./event-stream"

export const RECENT_EVENT_LIMIT = 64

export type RealtimeEventHandler = (event: RealtimeEvent) => void

const subscribers = new Map<string, Set<RealtimeEventHandler>>()
const recentEvents: RealtimeEvent[] = []

/** 订阅类型以 `typePrefix` 开头的事件（如 `aigc.`、`async-task.`），返回取消函数。 */
export function subscribe(typePrefix: string, handler: RealtimeEventHandler): () => void {
  let handlers = subscribers.get(typePrefix)
  if (handlers === undefined) {
    handlers = new Set()
    subscribers.set(typePrefix, handlers)
  }
  handlers.add(handler)
  return () => {
    const current = subscribers.get(typePrefix)
    if (current === undefined) return
    current.delete(handler)
    if (current.size === 0) subscribers.delete(typePrefix)
  }
}

/** 由全局 Provider 调用：缓冲并分发一条已去重的事件。 */
export function publishEvent(event: RealtimeEvent): void {
  recentEvents.push(event)
  if (recentEvents.length > RECENT_EVENT_LIMIT) recentEvents.shift()
  for (const [prefix, handlers] of [...subscribers]) {
    if (!event.type.startsWith(prefix)) continue
    for (const handler of [...handlers]) handler(event)
  }
}

/** 清空最近事件缓冲（用户切换或连接重置时调用）。 */
export function clearRecentEvents(): void {
  recentEvents.length = 0
}

/**
 * 等待首个满足条件的事件：先检查最近缓冲，再等待后续事件。
 * `signal` 中止时以 AbortError 拒绝。
 */
export function waitForEvent(
  predicate: (event: RealtimeEvent) => boolean,
  signal?: AbortSignal
): Promise<RealtimeEvent> {
  return new Promise<RealtimeEvent>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException("操作已取消", "AbortError"))
      return
    }
    const buffered = recentEvents.find(predicate)
    if (buffered !== undefined) {
      resolve(buffered)
      return
    }

    const cleanup = () => {
      unsubscribe()
      signal?.removeEventListener("abort", onAbort)
    }
    const onAbort = () => {
      cleanup()
      reject(new DOMException("操作已取消", "AbortError"))
    }
    const unsubscribe = subscribe("", (event) => {
      if (!predicate(event)) return
      cleanup()
      resolve(event)
    })
    signal?.addEventListener("abort", onAbort, { once: true })
  })
}
