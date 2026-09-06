/**
 * Project Activity wire contract 单元测试。
 * @author AaronZZH & Kiro
 */

import { readFileSync } from "node:fs"

import { describe, expect, it, vi } from "vitest"
import {
  acceptActivityEnvelope,
  activityCursorKey,
  parseActivityEnvelope,
  readActivityEventStream
} from "./ProjectActivityProvider"

function envelope(id: number, projectId: number | null = 7): string {
  return JSON.stringify({
    id,
    type: "project.updated",
    projectId,
    executionRunId: null,
    taskId: null,
    mediaVersionId: null,
    objectVersionId: null,
    reviewId: null,
    workId: null,
    publicationId: null,
    conversationId: null,
    payload: {}
  })
}

describe("ProjectActivityProvider wire contract", () => {
  it("cursor key 明确绑定 owner、org 与 workspace", () => {
    expect(
      activityCursorKey("42", { kind: "workspace", orgId: "8", workspaceId: "9" })
    ).toBe("aaf.aigc.activity.cursor.v1:owner-42:org-8:workspace-9")
    expect(activityCursorKey("42", { kind: "all-organizations" })).toContain(
      "owner-42:org-all:workspace-none"
    )
  })

  it("只接受后端正式 *Id wire envelope，且要求全部关联字段存在", () => {
    expect(parseActivityEnvelope(envelope(1))?.projectId).toBe(7)
    expect(
      parseActivityEnvelope(
        JSON.stringify({ id: 2, type: "project.updated", projectId: 7, payload: {} })
      )
    ).toBeNull()
    expect(
      parseActivityEnvelope(envelope(3).replace('"executionRunId":null', '"executionRunId":"3"'))
    ).toBeNull()
    expect(
      parseActivityEnvelope(envelope(4).replace('"taskId":null', '"taskId":0'))
    ).toBeNull()
  })

  it("只建立一个带授权上下文的 fetch SSE 连接，不保留 EventSource 路径", () => {
    const source = readFileSync(new URL("./ProjectActivityProvider.tsx", import.meta.url), "utf8")
    expect(source.split("await backendStreamFetch(").length - 1).toBe(1)
    expect(source).toContain('Accept: "text/event-stream"')
    expect(source).not.toContain("new EventSource")
  })

  it("分片 SSE 只分发 activity 命名事件", async () => {
    const activity = vi.fn()
    const first = new TextEncoder().encode(`event: activity\ndata: ${envelope(5)}`)
    const second = new TextEncoder().encode("\n\nevent: ping\ndata: {}\n\n")
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(first)
        controller.enqueue(second)
        controller.close()
      }
    })

    await readActivityEventStream(new Response(body, { status: 200 }), activity)

    expect(activity).toHaveBeenCalledOnce()
    expect(activity).toHaveBeenCalledWith(envelope(5))
  })

  it("重复 id 不重复处理，乱序未见 id 仍处理且 cursor 不回退", () => {
    const seen = new Set<bigint>()
    const newest = acceptActivityEnvelope(envelope(10), seen, 0n)
    expect(newest?.cursor).toBe(10n)
    expect(acceptActivityEnvelope(envelope(10), seen, 10n)).toBeNull()
    const older = acceptActivityEnvelope(envelope(8), seen, 10n)
    expect(older?.envelope.id).toBe(8)
    expect(older?.cursor).toBe(10n)
  })
})
