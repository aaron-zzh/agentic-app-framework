/**
 * 全局事件流 wire contract 与订阅注册表单元测试。
 * @author AaronZZH & Kiro
 */

import { readFileSync } from "node:fs"
import { pathToFileURL } from "node:url"

import { describe, expect, it, vi } from "vitest"
import { publishEvent, subscribe, waitForEvent } from "./event-registry"
import {
  acceptEventEnvelope,
  eventCursorKey,
  parseEventEnvelope,
  type RealtimeEvent,
  readEventStream
} from "./event-stream"

function envelope(id: number, refs: Record<string, number> = { projectId: 7 }): string {
  return JSON.stringify({ id, type: "aigc.project.changed", refs, payload: {} })
}

function event(id: number, type: string): RealtimeEvent {
  return { id, type, refs: {}, payload: {} }
}

describe("全局事件流 wire contract", () => {
  it("cursor key 明确绑定 owner、org 与 workspace", () => {
    expect(eventCursorKey("42", { kind: "workspace", orgId: "8", workspaceId: "9" })).toBe(
      "aaf.events.cursor.v1:owner-42:org-8:workspace-9"
    )
    expect(eventCursorKey("42", { kind: "all-organizations" })).toContain(
      "owner-42:org-all:workspace-none"
    )
  })

  it("只接受 { id, type, refs, payload } 信封，refs 值必须是正整数", () => {
    expect(parseEventEnvelope(envelope(1))?.refs.projectId).toBe(7)
    expect(parseEventEnvelope(JSON.stringify({ id: 2, type: "aigc.x", payload: {} }))).toBeNull()
    expect(parseEventEnvelope(envelope(3, { taskId: 0 }))).toBeNull()
    expect(parseEventEnvelope(envelope(4).replace("7", '"7"'))).toBeNull()
  })

  it("分片 SSE 只分发 event 命名事件，注释帧被忽略", async () => {
    const onEvent = vi.fn()
    const first = new TextEncoder().encode(`: connected\n\nevent: event\ndata: ${envelope(5)}`)
    const second = new TextEncoder().encode("\n\n: heartbeat\n\nevent: ping\ndata: {}\n\n")
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(first)
        controller.enqueue(second)
        controller.close()
      }
    })

    await readEventStream(new Response(body, { status: 200 }), onEvent)

    expect(onEvent).toHaveBeenCalledOnce()
    expect(onEvent).toHaveBeenCalledWith(envelope(5))
  })

  it("重复 id 不重复处理，乱序未见 id 仍处理且 cursor 不回退", () => {
    const seen = new Set<bigint>()
    expect(acceptEventEnvelope(envelope(10), seen, 0n)?.cursor).toBe(10n)
    expect(acceptEventEnvelope(envelope(10), seen, 10n)).toBeNull()
    const older = acceptEventEnvelope(envelope(8), seen, 10n)
    expect(older?.event.id).toBe(8)
    expect(older?.cursor).toBe(10n)
  })

  it("Provider 只建立一个带授权上下文的 fetch SSE 连接，不使用 EventSource", () => {
    const moduleUrl = import.meta.url.startsWith("file:")
      ? import.meta.url
      : pathToFileURL(import.meta.url).href
    const source = readFileSync(new URL("./event-stream-provider.tsx", moduleUrl), "utf8")
    expect(source.split("await backendStreamFetch(").length - 1).toBe(1)
    expect(source).toContain('Accept: "text/event-stream"')
    expect(source).not.toContain("new EventSource")
  })
})

describe("事件订阅注册表", () => {
  it("按类型前缀分发，取消订阅后不再收到", () => {
    const aigc = vi.fn()
    const unsubscribe = subscribe("aigc.", aigc)
    publishEvent(event(101, "aigc.task.completed"))
    publishEvent(event(102, "async-task.status"))
    unsubscribe()
    publishEvent(event(103, "aigc.task.failed"))

    expect(aigc).toHaveBeenCalledOnce()
    expect(aigc.mock.calls[0]?.[0].id).toBe(101)
  })

  it("waitForEvent 先匹配最近缓冲，避免提交响应与订阅之间漏事件", async () => {
    publishEvent(event(201, "async-task.status"))

    const matched = await waitForEvent((item) => item.id === 201)

    expect(matched.id).toBe(201)
  })

  it("waitForEvent 等待后续事件，中止时以 AbortError 拒绝", async () => {
    const pending = waitForEvent((item) => item.id === 301)
    publishEvent(event(301, "async-task.status"))
    expect((await pending).id).toBe(301)

    const controller = new AbortController()
    const aborted = waitForEvent((item) => item.id === 999_999, controller.signal)
    controller.abort()
    await expect(aborted).rejects.toMatchObject({ name: "AbortError" })
  })
})
