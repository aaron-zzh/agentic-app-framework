/**
 * Headless Assistant 安全事件客户端。
 *
 * 统一解析 Assistant 与业务生成接口返回的结构化 SSE 事件；客户端只消费安全事件，
 * 不读取或暴露模型原始思维链。
 *
 * @example
 * await executeAssistant("copywriter", request, {
 *   onEvent: (event) => observe(event),
 *   onChunk: (delta) => editor.push(delta),
 *   onDone: () => editor.done(),
 * })
 * @author AaronZZH & Kiro
 */

import axios from "axios"

import { buildApiUrl } from "./config"

export type AssistantExecutionPhase = "idle" | "running" | "success" | "error"

export const ASSISTANT_EVENT_TYPES = [
  "MESSAGE_DELTA",
  "MESSAGE_COMPLETED",
  "TASK_STATUS_CHANGED",
  "EXECUTION_STARTED",
  "EXECUTION_COMPLETED",
  "EXECUTION_FAILED",
  "EXECUTION_PAUSED",
  "EXECUTION_CANCELED",
  "COMMAND_REJECTED",
  "TOOL_CALL_STARTED",
  "TOOL_CALL_COMPLETED",
  "TOOL_CALL_FAILED"
] as const

export type KnownAssistantEventType = (typeof ASSISTANT_EVENT_TYPES)[number]
export type AssistantEventType = string
export type AssistantMemoryMode = "DEFAULT" | "DISABLED"

export interface AssistantKnowledgeOptions {
  knowledgeBaseIds: string[]
  includePublic: boolean
  topK: number
  similarityThreshold: number
}

export type AssistantModelOptions =
  | { mode: "AUTO"; modelId: null }
  | { mode: "EXPLICIT"; modelId: string }

export type AssistantExecutionMaterial =
  | {
      type: "TEXT"
      name?: string
      content: string
      resourceId?: never
      url?: never
    }
  | {
      type: "IMAGE"
      name?: string
      content?: never
      resourceId: string
      url?: never
    }

export interface AssistantExecutionRequest {
  assistantVersion: number
  input: string
  skillKey: string | null
  knowledge: AssistantKnowledgeOptions
  model: AssistantModelOptions
  materials: AssistantExecutionMaterial[]
  memoryMode: AssistantMemoryMode
}

export type AssistantEventPayload = Record<string, unknown>
export type AssistantContextSource = Record<string, unknown>

export interface AssistantSafeEvent {
  sequence: number
  type: AssistantEventType
  status: string
  createdAt: string
  payload: AssistantEventPayload
  contextSources: AssistantContextSource[]
}

export interface AssistantSseOptions {
  onEvent?: (event: AssistantSafeEvent) => void
  onChunk?: (delta: string, event: AssistantSafeEvent) => void
  onDone?: (event: AssistantSafeEvent) => void
  onError?: (error: Error, event?: AssistantSafeEvent) => void
  signal?: AbortSignal
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

function readContextSources(value: unknown): AssistantContextSource[] {
  if (!Array.isArray(value) || !value.every(isRecord)) {
    throw new Error("Assistant SSE 事件缺少有效 contextSources")
  }
  return value
}

/** 将单条 SSE data JSON 校验并转换为安全事件。 */
export function parseAssistantSafeEvent(data: string): AssistantSafeEvent {
  let value: unknown
  try {
    value = JSON.parse(data)
  } catch {
    throw new Error("Assistant SSE 事件不是有效 JSON")
  }

  if (!isRecord(value)) throw new Error("Assistant SSE 事件必须是对象")
  if (typeof value.sequence !== "number" || !Number.isFinite(value.sequence)) {
    throw new Error("Assistant SSE 事件缺少有效 sequence")
  }
  if (typeof value.type !== "string" || value.type.length === 0) {
    throw new Error("Assistant SSE 事件缺少 type")
  }
  if (typeof value.status !== "string" || value.status.length === 0) {
    throw new Error("Assistant SSE 事件缺少 status")
  }
  if (typeof value.createdAt !== "string" || value.createdAt.length === 0) {
    throw new Error("Assistant SSE 事件缺少 createdAt")
  }
  if (!isRecord(value.payload)) throw new Error("Assistant SSE 事件缺少 payload")

  return {
    sequence: value.sequence,
    type: value.type,
    status: value.status,
    createdAt: value.createdAt,
    payload: value.payload,
    contextSources: readContextSources(value.contextSources)
  }
}

export function assistantEventDelta(event: AssistantSafeEvent): string | null {
  return event.type === "MESSAGE_DELTA" && typeof event.payload.delta === "string"
    ? event.payload.delta
    : null
}

export function assistantCompletedText(event: AssistantSafeEvent): string | null {
  return event.type === "MESSAGE_COMPLETED" && typeof event.payload.text === "string"
    ? event.payload.text
    : null
}

export function isAssistantFailureEvent(event: AssistantSafeEvent): boolean {
  const status = event.status.toUpperCase()
  return (
    event.type === "COMMAND_REJECTED" ||
    event.type === "EXECUTION_FAILED" ||
    event.type === "EXECUTION_PAUSED" ||
    event.type === "EXECUTION_CANCELED" ||
    (event.type.startsWith("EXECUTION_") &&
      (status === "FAILED" || status === "ERROR" || status === "CANCELED" || status === "REJECTED"))
  )
}

export function isAssistantCompletedEvent(event: AssistantSafeEvent): boolean {
  return event.type === "EXECUTION_COMPLETED"
}

function eventError(event: AssistantSafeEvent): Error {
  const message = event.payload.message ?? event.payload.summary ?? event.payload.error
  return new Error(
    typeof message === "string" && message.length > 0 ? message : "Assistant 执行失败"
  )
}

function responseErrorMessage(value: unknown, fallback: string): string {
  if (!isRecord(value)) return fallback
  return typeof value.message === "string" && value.message.length > 0 ? value.message : fallback
}

async function dispatchEventData(
  data: string,
  options: AssistantSseOptions
): Promise<"continue" | "stop"> {
  if (!data) return "continue"
  if (data === "[DONE]") {
    options.onError?.(new Error("SSE 在终态前结束"))
    return "stop"
  }

  let event: AssistantSafeEvent
  try {
    event = parseAssistantSafeEvent(data)
  } catch (error) {
    options.onError?.(error instanceof Error ? error : new Error(String(error)))
    return "stop"
  }

  options.onEvent?.(event)
  const delta = assistantEventDelta(event)
  if (delta !== null) options.onChunk?.(delta, event)

  if (isAssistantFailureEvent(event)) {
    options.onError?.(eventError(event), event)
    return "stop"
  }
  if (isAssistantCompletedEvent(event)) {
    options.onDone?.(event)
    return "stop"
  }
  return "continue"
}

/** 解析完整 WHATWG SSE 字段，支持跨 chunk、多行 data 与三种换行。 */
export async function readAssistantEventStream(
  body: ReadableStream<Uint8Array>,
  options: AssistantSseOptions
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  let dataBuffer = ""
  let settled = false

  const settledOptions: AssistantSseOptions = {
    ...options,
    onDone: (event) => {
      if (settled) return
      settled = true
      options.onDone?.(event)
    },
    onError: (error, event) => {
      if (settled) return
      settled = true
      options.onError?.(error, event)
    }
  }

  const flushData = async (): Promise<boolean> => {
    if (dataBuffer === "") return false
    const data = dataBuffer.endsWith("\n") ? dataBuffer.slice(0, -1) : dataBuffer
    dataBuffer = ""
    return (await dispatchEventData(data, settledOptions)) === "stop"
  }

  const consumeLine = async (line: string): Promise<boolean> => {
    if (line === "") return flushData()
    if (line.startsWith(":")) return false

    const colon = line.indexOf(":")
    const field = colon === -1 ? line : line.slice(0, colon)
    let fieldValue = colon === -1 ? "" : line.slice(colon + 1)
    if (fieldValue.startsWith(" ")) fieldValue = fieldValue.slice(1)
    if (field === "data") dataBuffer += `${fieldValue}\n`
    return false
  }

  try {
    while (!settled) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      let pendingCarriageReturn = ""
      if (buffer.endsWith("\r")) {
        pendingCarriageReturn = "\r"
        buffer = buffer.slice(0, -1)
      }
      const lines = buffer.replace(/\r\n?/g, "\n").split("\n")
      buffer = (lines.pop() ?? "") + pendingCarriageReturn

      for (const line of lines) {
        if (await consumeLine(line)) break
      }
    }

    if (!settled) {
      buffer += decoder.decode()
      const trailingLines = buffer.replace(/\r\n?/g, "\n").split("\n")
      buffer = ""
      for (const line of trailingLines) {
        if (await consumeLine(line)) break
      }
      if (!settled) await flushData()
    }
    if (!settled) settledOptions.onError?.(new Error("SSE 在终态前结束"))
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") return
    settledOptions.onError?.(error instanceof Error ? error : new Error(String(error)))
  } finally {
    reader.releaseLock()
  }
}

/** 向任意返回安全事件的 POST SSE 接口发起请求。 */
export async function postAssistantEventStream(
  path: string,
  request: unknown,
  options: AssistantSseOptions
): Promise<void> {
  const auth = axios.defaults.headers.common.Authorization as string | undefined
  let response: Response

  try {
    response = await fetch(buildApiUrl(path), {
      method: "POST",
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
        ...(auth ? { Authorization: auth } : {})
      },
      body: JSON.stringify(request),
      credentials: "include",
      signal: options.signal
    })
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") return
    options.onError?.(error instanceof Error ? error : new Error(String(error)))
    return
  }

  if (!response.ok || !response.body) {
    let message = response.status === 401 ? "登录已过期，请刷新页面重试" : `HTTP ${response.status}`
    try {
      message = responseErrorMessage(await response.json(), message)
    } catch {
      // 响应体不是 JSON 时保留 HTTP 错误摘要。
    }
    options.onError?.(new Error(message))
    return
  }

  const contentType = response.headers.get("content-type") ?? ""
  if (!contentType.includes("text/event-stream")) {
    options.onError?.(new Error("Assistant 响应不是 text/event-stream"))
    return
  }

  await readAssistantEventStream(response.body, options)
}

/** 调用 Headless Assistant execution 接口。 */
export function executeAssistant(
  assistantId: string | number,
  request: AssistantExecutionRequest,
  options: AssistantSseOptions
): Promise<void> {
  return postAssistantEventStream(
    `/assistants/${encodeURIComponent(String(assistantId))}/executions`,
    request,
    options
  )
}
