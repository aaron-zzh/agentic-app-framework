/**
 * Studio 用户级 Activity SSE 单例。
 *
 * 事件仅作为变化通知；业务 payload 不写入本地状态，统一失效权威查询。
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { useEffect } from "react"
import { aigcExecutionKeys } from "@/lib/api/rest/ai/aigc/execution"
import { aigcProjectKeys } from "@/lib/api/rest/ai/aigc/project"
import { aigcWorkKeys } from "@/lib/api/rest/ai/aigc/work"
import { MEDIA_QUERY_KEY } from "@/lib/api/rest/media/media-asset"
import { backendStreamFetch } from "@/lib/api/streaming-client"
import { useAuthStore } from "@/lib/store/auth-store"
import { type ScopeSelection, scopeHeaders, useOrgStore } from "@/lib/store/org-store"

const CURSOR_KEY_PREFIX = "aaf.aigc.activity.cursor.v1"
const RECONNECT_DELAY_MS = 1_000
export const MAX_SEEN_ACTIVITY_EVENTS = 512

export interface AigcActivityEnvelope {
  id: number
  type: string
  projectId: number | null
  executionRunId: number | null
  taskId: number | null
  mediaVersionId: number | null
  objectVersionId: number | null
  reviewId: number | null
  workId: number | null
  publicationId: number | null
  conversationId: number | null
  payload: Record<string, unknown>
}

export function activityCursorKey(ownerId: string, scope: ScopeSelection | null): string {
  const { orgId, workspaceId } = scopeHeaders(scope)
  return `${CURSOR_KEY_PREFIX}:owner-${ownerId}:org-${orgId ?? "none"}:workspace-${workspaceId ?? "none"}`
}

function cursorValue(value: string | null): bigint {
  return value !== null && /^\d+$/.test(value) ? BigInt(value) : 0n
}

export function parseActivityEnvelope(data: string): AigcActivityEnvelope | null {
  try {
    const parsed: unknown = JSON.parse(data)
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null
    const envelope = parsed as Record<string, unknown>
    const relationFields = [
      "projectId",
      "executionRunId",
      "taskId",
      "mediaVersionId",
      "objectVersionId",
      "reviewId",
      "workId",
      "publicationId",
      "conversationId"
    ] as const
    const hasValidRelations = relationFields.every((field) => {
      const value = envelope[field]
      return (
        value === null || (typeof value === "number" && Number.isSafeInteger(value) && value > 0)
      )
    })
    if (
      typeof envelope.id !== "number" ||
      !Number.isSafeInteger(envelope.id) ||
      envelope.id <= 0 ||
      typeof envelope.type !== "string" ||
      !hasValidRelations ||
      !envelope.payload ||
      typeof envelope.payload !== "object" ||
      Array.isArray(envelope.payload)
    ) {
      return null
    }
    return envelope as unknown as AigcActivityEnvelope
  } catch {
    return null
  }
}

export function acceptActivityEnvelope(
  data: string,
  seenEventIds: Set<bigint>,
  currentCursor: bigint
): { envelope: AigcActivityEnvelope; cursor: bigint } | null {
  const envelope = parseActivityEnvelope(data)
  if (envelope === null) return null
  const eventId = BigInt(envelope.id)
  if (seenEventIds.has(eventId)) return null
  seenEventIds.add(eventId)
  return { envelope, cursor: eventId > currentCursor ? eventId : currentCursor }
}

function dispatchSseBlock(block: string, onActivity: (data: string) => void): void {
  let eventType = "message"
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith("event:")) eventType = line.slice(6).trim()
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart())
  }
  if (eventType === "activity" && dataLines.length > 0) onActivity(dataLines.join("\n"))
}

/** 读取 fetch SSE 响应，仅分发命名为 activity 的事件。 */
export async function readActivityEventStream(
  response: Response,
  onActivity: (data: string) => void
): Promise<void> {
  if (!response.ok) throw new Error(`Activity SSE 请求失败（${response.status}）`)
  if (!response.body) throw new Error("Activity SSE 响应缺少流")

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() ?? ""
    for (const block of blocks) dispatchSseBlock(block, onActivity)
    if (done) {
      if (buffer.trim()) dispatchSseBlock(buffer, onActivity)
      return
    }
  }
}

function waitForReconnect(signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const onAbort = () => {
      clearTimeout(timer)
      resolve()
    }
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", onAbort)
      resolve()
    }, RECONNECT_DELAY_MS)
    signal.addEventListener("abort", onAbort, { once: true })
  })
}

export function ProjectActivityProvider() {
  const queryClient = useQueryClient()
  const ownerId = useAuthStore((state) => state.user?.id ?? null)
  const scope = useOrgStore((state) => state.currentScope)

  useEffect(() => {
    if (ownerId === null) return
    const cursorKey = activityCursorKey(ownerId, scope)
    let currentCursor = cursorValue(localStorage.getItem(cursorKey))
    const controller = new AbortController()
    const seenEventIds = new Set<bigint>()
    const invalidate = (data: string) => {
      const accepted = acceptActivityEnvelope(data, seenEventIds, currentCursor)
      if (accepted === null) return
      const { envelope, cursor } = accepted
      if (cursor > currentCursor) {
        currentCursor = cursor
        localStorage.setItem(cursorKey, currentCursor.toString())
      }

      queryClient.invalidateQueries({ queryKey: aigcExecutionKeys.all })
      queryClient.invalidateQueries({ queryKey: aigcWorkKeys.all })
      queryClient.invalidateQueries({ queryKey: MEDIA_QUERY_KEY })
      if (envelope.projectId === null) {
        queryClient.invalidateQueries({ queryKey: aigcProjectKeys.all })
        return
      }
      const projectId = envelope.projectId
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(projectId) })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.graph(projectId) })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(projectId) })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.mediaRefs(projectId) })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.reviews(projectId) })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.completionEvidence(projectId) })
      queryClient.invalidateQueries({ queryKey: ["aigc.project", "versions", projectId] })
    }

    const connect = async () => {
      while (!controller.signal.aborted) {
        try {
          const response = await backendStreamFetch(
            `/aigc/events/stream?after=${encodeURIComponent(currentCursor.toString())}`,
            { headers: { Accept: "text/event-stream" }, signal: controller.signal }
          )
          await readActivityEventStream(response, invalidate)
        } catch (error) {
          if (controller.signal.aborted) return
          if (error instanceof DOMException && error.name === "AbortError") return
        }
        await waitForReconnect(controller.signal)
      }
    }

    void connect()
    return () => controller.abort()
  }, [ownerId, queryClient, scope])

  return null
}
