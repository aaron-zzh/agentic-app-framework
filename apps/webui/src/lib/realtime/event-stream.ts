/**
 * 全局事件流 wire 协议：信封解析、游标与 fetch SSE 读取（纯逻辑，无 React 依赖）。
 *
 * 后端 `GET /api/events/stream` 以命名事件 `event` 推送信封 `{ id, type, refs, payload }`，
 * 心跳与连接确认为注释帧，不会产生业务事件。
 * @author AaronZZH & Kiro
 */

import { type ScopeSelection, scopeHeaders } from "@/lib/store/org-store"

const CURSOR_KEY_PREFIX = "aaf.events.cursor.v1"
const WIRE_EVENT_NAME = "event"
export const MAX_SEEN_EVENTS = 512

/** 全局事件信封。`refs` 只含正整数 ID，`payload` 仅为状态摘要。 */
export interface RealtimeEvent {
  id: number
  type: string
  refs: Record<string, number>
  payload: Record<string, unknown>
}

export function eventCursorKey(ownerId: string, scope: ScopeSelection | null): string {
  const { orgId, workspaceId } = scopeHeaders(scope)
  return `${CURSOR_KEY_PREFIX}:owner-${ownerId}:org-${orgId ?? "none"}:workspace-${workspaceId ?? "none"}`
}

export function cursorValue(value: string | null): bigint {
  return value !== null && /^\d+$/.test(value) ? BigInt(value) : 0n
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

export function parseEventEnvelope(data: string): RealtimeEvent | null {
  try {
    const parsed: unknown = JSON.parse(data)
    if (!isPlainObject(parsed)) return null
    const { id, type, refs, payload } = parsed
    if (typeof id !== "number" || !Number.isSafeInteger(id) || id <= 0) return null
    if (typeof type !== "string" || type.length === 0) return null
    if (!isPlainObject(refs) || !isPlainObject(payload)) return null
    const validRefs = Object.values(refs).every(
      (value) => typeof value === "number" && Number.isSafeInteger(value) && value > 0
    )
    if (!validRefs) return null
    return { id, type, refs: refs as Record<string, number>, payload }
  } catch {
    return null
  }
}

/** 去重并推进游标：已见过的 id 返回 null；乱序未见 id 仍处理且游标不回退。 */
export function acceptEventEnvelope(
  data: string,
  seenEventIds: Set<bigint>,
  currentCursor: bigint
): { event: RealtimeEvent; cursor: bigint } | null {
  const event = parseEventEnvelope(data)
  if (event === null) return null
  const eventId = BigInt(event.id)
  if (seenEventIds.has(eventId)) return null
  seenEventIds.add(eventId)
  while (seenEventIds.size > MAX_SEEN_EVENTS) {
    const oldestEventId = seenEventIds.values().next().value
    if (oldestEventId === undefined) break
    seenEventIds.delete(oldestEventId)
  }
  return { event, cursor: eventId > currentCursor ? eventId : currentCursor }
}

function dispatchSseBlock(block: string, onEvent: (data: string) => void): void {
  let eventType = "message"
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith("event:")) eventType = line.slice(6).trim()
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart())
  }
  if (eventType === WIRE_EVENT_NAME && dataLines.length > 0) onEvent(dataLines.join("\n"))
}

/** 读取 fetch SSE 响应，仅分发命名为 `event` 的事件，注释帧被忽略。 */
export async function readEventStream(
  response: Response,
  onEvent: (data: string) => void
): Promise<void> {
  if (!response.ok) throw new Error(`全局事件 SSE 请求失败（${response.status}）`)
  if (!response.body) throw new Error("全局事件 SSE 响应缺少流")

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() ?? ""
    for (const block of blocks) dispatchSseBlock(block, onEvent)
    if (done) {
      if (buffer.trim()) dispatchSseBlock(buffer, onEvent)
      return
    }
  }
}
