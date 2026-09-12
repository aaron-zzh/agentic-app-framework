/**
 * Assistant AG-UI 正文流客户端。
 *
 * 正文仅消费 TEXT_MESSAGE_CONTENT.delta；任务状态、授权和安全引用仅消费
 * CUSTOM 中的 AafAiTaskEvent，绝不读取内部 ExecutionEvent payload。
 *
 * @example
 * await executeAssistantAgUi(request, {
 *   onChunk: (delta) => editor.push(delta),
 *   onDone: () => editor.done()
 * })
 * @author AaronZZH & Kiro
 */

import {
  type AafAiTaskEvent,
  type AafAiTaskEventData,
  isAafAiTaskEvent
} from "@/lib/api/rest/ai/task-event"
import { chatApi } from "./rest/ai"
import { backendStreamFetch } from "./streaming-client"

export type AssistantExecutionPhase =
  | "idle"
  | "running"
  | "awaiting_authorization"
  | "paused"
  | "success"
  | "error"

export type AssistantMemoryMode = "DEFAULT" | "DISABLED"
export type AssistantOutputLocale = "EN" | "JA" | "KO" | "FR" | "ES"
export type AssistantOutputFormat = "JSON"
export type AssistantActionAuthorizationPolicy =
  | "REQUEST_ON_DEMAND"
  | "PREAUTHORIZED_ONLY"
  | "DENY_AUTHORIZED_ACTIONS"

export interface AssistantTarget {
  id?: string
}

export type AssistantExecutionAttachment =
  | {
      type: "TEXT"
      name?: string
      content: string
      resourceId?: never
    }
  | {
      type: "IMAGE"
      name?: string
      content?: never
      resourceId: string
    }

export interface AssistantExecutionInput {
  text: string
  variables: Record<string, unknown>
  attachments: AssistantExecutionAttachment[]
}

export interface AssistantRoleSelection {
  key: string
}

export interface AssistantSkillSelection {
  code: string
}

export interface AssistantExecutionOptions {
  interactionMode: "TASK" | "CONVERSATIONAL"
  routeConstraint: "FIXED" | "AUTO"
  clarificationPolicy: "MINIMAL" | "FAIL_ON_BLOCKER" | "INTERACTIVE"
  actionAuthorizationPolicy: AssistantActionAuthorizationPolicy
  artifactPersistence: "AUTO_SAVE_DRAFT" | "RETURN_ONLY"
}

export interface AssistantKnowledgeOptions {
  mode: "DEFAULT" | "EXPLICIT" | "DISABLED"
  knowledgeBaseIds: string[]
  topK: number
  similarityThreshold: number
}

export type AssistantModelOptions =
  | { mode: "AUTO"; modelId: null }
  | { mode: "EXPLICIT"; modelId: string }

export interface AssistantMemoryOptions {
  mode: AssistantMemoryMode
}

export interface AssistantOutputOptions {
  maxCharLen?: number
  locale?: AssistantOutputLocale
  format?: AssistantOutputFormat
}

export interface AssistantExecutionRequest {
  assistant?: AssistantTarget
  execution: AssistantExecutionOptions
  input: AssistantExecutionInput
  role?: AssistantRoleSelection
  skill?: AssistantSkillSelection
  knowledge: AssistantKnowledgeOptions
  model: AssistantModelOptions
  memory: AssistantMemoryOptions
  output: AssistantOutputOptions
}

interface RunEvent {
  runId: string
}

export interface AssistantRunStartedEvent extends RunEvent {
  type: "RUN_STARTED"
}

export interface AssistantRunFinishedEvent extends RunEvent {
  type: "RUN_FINISHED"
  /**
   * AG-UI 标准 interrupt 终态（AAF-104 #10404）。非空时表示本次 run 因需要人工审批而暂停，
   * 不是失败——客户端应展示确认 UI 并通过 {@link resumeAssistantAgUi} 携带 {@code resume[]} 恢复。
   */
  interrupts: AssistantInterrupt[]
}

export interface AssistantInterrupt {
  id: string
  reason: string
  message: string | null
  /** AAF 语义：工具名（{@code RunLifecycleEventConverter} 用 {@code action} 承载，非调用实例 ID）。 */
  toolCallId: string | null
  /** 该受控操作是否具备真实补偿/撤销能力，来自 {@code HumanApproval.reversible()}。 */
  reversible: boolean
}

export interface AssistantRunErrorEvent extends RunEvent {
  type: "RUN_ERROR"
  message: string
}

export interface AssistantTextMessageStartEvent extends RunEvent {
  type: "TEXT_MESSAGE_START"
  messageId: string
}

export interface AssistantTextMessageContentEvent extends RunEvent {
  type: "TEXT_MESSAGE_CONTENT"
  messageId: string
  delta: string
}

export interface AssistantTextMessageEndEvent extends RunEvent {
  type: "TEXT_MESSAGE_END"
  messageId: string
}

export interface AssistantToolCallStartEvent extends RunEvent {
  type: "TOOL_CALL_START"
  toolCallId: string
  toolCallName: string
}

export interface AssistantToolCallResultEvent extends RunEvent {
  type: "TOOL_CALL_RESULT"
  toolCallId: string
  /** AG-UI 契约中 content 是字符串；后端把脱敏后的安全字段序列化为 JSON 文本。 */
  content: string
}

export interface AssistantCustomEvent extends RunEvent {
  type: "CUSTOM"
  name: AafAiTaskEvent["type"]
  value: AafAiTaskEvent
}

export type AssistantAgUiEvent =
  | AssistantRunStartedEvent
  | AssistantRunFinishedEvent
  | AssistantRunErrorEvent
  | AssistantTextMessageStartEvent
  | AssistantTextMessageContentEvent
  | AssistantTextMessageEndEvent
  | AssistantToolCallStartEvent
  | AssistantToolCallResultEvent
  | AssistantCustomEvent

export interface AssistantAgUiStreamOptions {
  onEvent?: (event: AssistantAgUiEvent) => void
  onChunk?: (delta: string, event: AssistantTextMessageContentEvent) => void
  /** 携带 {@code interrupts} 数组（AAF-104 #10404）；非空时是审批暂停而非成功完成，见 {@link AssistantRunFinishedEvent}。 */
  onDone?: (event: AssistantRunFinishedEvent) => void
  onError?: (error: Error, event?: AssistantRunErrorEvent) => void
  /** {@link executeAssistantAgUi} 创建会话后立即回调，供调用方保留 threadId 以便后续 {@link resumeAssistantAgUi}。 */
  onSessionReady?: (session: { threadId: string }) => void
  signal?: AbortSignal
}

interface AssistantAgUiRunMessage {
  id: string
  role: string
  content: { type: "text"; text: string }[]
}

interface AssistantExecutionAgUiRunRequest {
  threadId: string
  runId: string
  parentRunId: null
  /** 本次 run 的调用参数走协议的 forwardedProps；state 留给线程级共享状态。 */
  forwardedProps: {
    mode: "EXECUTION"
    request: AssistantExecutionRequest
  }
  messages: AssistantAgUiRunMessage[]
}

interface AssistantChatAgUiRunRequest {
  threadId: string
  runId: string
  parentRunId: null
  forwardedProps: {
    mode: "CHAT"
    taskModelSelection: { mode: "AUTO"; modelId: null } | { mode: "EXPLICIT"; modelId: string }
  }
  messages: AssistantAgUiRunMessage[]
}

export interface AssistantChatRequest {
  threadId: string
  modelId?: string
  messages: { role: string; text: string }[]
}

interface AssistantResumeAgUiRunRequest {
  threadId: string
  runId: string
  parentRunId: null
  forwardedProps: Record<string, never>
  messages: never[]
  resume: {
    interruptId: string
    status: "resolved" | "cancelled"
    payload: { approved: boolean }
  }[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

function requiredText(value: Record<string, unknown>, key: string): string {
  const field = value[key]
  if (typeof field !== "string" || field.length === 0) {
    throw new Error(`AG-UI 事件缺少有效 ${key}`)
  }
  return field
}

/**
 * 解析 TOOL_CALL_RESULT 的 content。
 *
 * <p>AG-UI 的 content 契约是字符串，后端把脱敏后的安全字段序列化为 JSON 文本；解析失败或含非标量值时
 * 返回空对象，调用方按缺省处理。
 */
export function parseToolResultContent(content: string): AafAiTaskEventData {
  let value: unknown
  try {
    value = JSON.parse(content)
  } catch {
    return {}
  }
  return isAafEventData(value) ? value : {}
}

function isAafEventData(value: unknown): value is AafAiTaskEventData {
  return (
    isRecord(value) &&
    Object.values(value).every(
      (item) => typeof item === "string" || typeof item === "number" || typeof item === "boolean"
    )
  )
}

/** 解析 {@code RUN_FINISHED.outcome}：无 outcome 或非 interrupt 类型时返回空数组（正常完成）。 */
function parseInterrupts(value: Record<string, unknown>): AssistantInterrupt[] {
  const outcome = value.outcome
  if (!isRecord(outcome) || outcome.type !== "interrupt") return []
  const interrupts = outcome.interrupts
  if (!Array.isArray(interrupts)) return []
  return interrupts.filter(isRecord).map((entry) => ({
    id: requiredText(entry, "id"),
    reason: requiredText(entry, "reason"),
    message: typeof entry.message === "string" ? entry.message : null,
    toolCallId: typeof entry.toolCallId === "string" ? entry.toolCallId : null,
    reversible: isRecord(entry.metadata) && entry.metadata.reversible === true
  }))
}

/** 校验并解析单条 Assistant AG-UI 事件。 */
export function parseAssistantAgUiEvent(data: string): AssistantAgUiEvent {
  let value: unknown
  try {
    value = JSON.parse(data)
  } catch {
    throw new Error("AG-UI 事件不是有效 JSON")
  }
  if (!isRecord(value)) throw new Error("AG-UI 事件必须是对象")

  const type = requiredText(value, "type")
  const runId = requiredText(value, "runId")
  switch (type) {
    case "RUN_STARTED":
      return { type, runId }
    case "RUN_FINISHED":
      return { type, runId, interrupts: parseInterrupts(value) }
    case "RUN_ERROR":
      return { type, runId, message: requiredText(value, "message") }
    case "TEXT_MESSAGE_START":
      return { type, runId, messageId: requiredText(value, "messageId") }
    case "TEXT_MESSAGE_CONTENT":
      return {
        type,
        runId,
        messageId: requiredText(value, "messageId"),
        delta: requiredText(value, "delta")
      }
    case "TEXT_MESSAGE_END":
      return { type, runId, messageId: requiredText(value, "messageId") }
    case "TOOL_CALL_START":
      return {
        type,
        runId,
        toolCallId: requiredText(value, "toolCallId"),
        toolCallName: requiredText(value, "toolCallName")
      }
    case "TOOL_CALL_RESULT":
      return {
        type,
        runId,
        toolCallId: requiredText(value, "toolCallId"),
        content: requiredText(value, "content")
      }
    case "CUSTOM": {
      const name = requiredText(value, "name")
      if (!isAafAiTaskEvent(value.value) || value.value.type !== name) {
        throw new Error("AG-UI CUSTOM 缺少匹配的 AafAiTaskEvent")
      }
      return { type, runId, name: value.value.type, value: value.value }
    }
    default:
      throw new Error(`不支持的 Assistant AG-UI 事件: ${type}`)
  }
}

function responseErrorMessage(value: unknown, fallback: string): string {
  if (!isRecord(value)) return fallback
  return typeof value.message === "string" && value.message.length > 0 ? value.message : fallback
}

async function dispatchEventData(
  data: string,
  options: AssistantAgUiStreamOptions
): Promise<"continue" | "stop"> {
  if (data.length === 0) return "continue"

  let event: AssistantAgUiEvent
  try {
    event = parseAssistantAgUiEvent(data)
  } catch (error) {
    options.onError?.(error instanceof Error ? error : new Error(String(error)))
    return "stop"
  }

  options.onEvent?.(event)
  if (event.type === "TEXT_MESSAGE_CONTENT") options.onChunk?.(event.delta, event)
  if (event.type === "RUN_ERROR") {
    options.onError?.(new Error(event.message), event)
    return "stop"
  }
  if (event.type === "RUN_FINISHED") {
    options.onDone?.(event)
    return "stop"
  }
  return "continue"
}

/** 解析完整 WHATWG SSE 字段，支持跨 chunk、多行 data 与三种换行。 */
export async function readAssistantAgUiEventStream(
  body: ReadableStream<Uint8Array>,
  options: AssistantAgUiStreamOptions
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  let dataBuffer = ""
  let settled = false

  const settledOptions: AssistantAgUiStreamOptions = {
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
    const eventData = dataBuffer.endsWith("\n") ? dataBuffer.slice(0, -1) : dataBuffer
    dataBuffer = ""
    return (await dispatchEventData(eventData, settledOptions)) === "stop"
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
    if (!settled) settledOptions.onError?.(new Error("AG-UI SSE 在终态前结束"))
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") return
    settledOptions.onError?.(error instanceof Error ? error : new Error(String(error)))
  } finally {
    reader.releaseLock()
  }
}

async function requestAssistantAgUiStream(
  path: string,
  init: RequestInit,
  options: AssistantAgUiStreamOptions
): Promise<void> {
  let response: Response
  try {
    const headers = new Headers(init.headers)
    if (!headers.has("Accept")) headers.set("Accept", "text/event-stream, application/json")
    response = await backendStreamFetch(path, { ...init, headers, signal: options.signal })
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

  await readAssistantAgUiEventStream(response.body, options)
}

/** 通过唯一 AG-UI 正文入口执行 Assistant 请求。 */
export async function executeAssistantAgUi(
  request: AssistantExecutionRequest,
  options: AssistantAgUiStreamOptions
): Promise<void> {
  let session: { threadId: string }
  try {
    session = await chatApi.createSession({ type: "ai" })
  } catch (error) {
    options.onError?.(error instanceof Error ? error : new Error(String(error)))
    return
  }
  options.onSessionReady?.(session)
  const envelope: AssistantExecutionAgUiRunRequest = {
    threadId: session.threadId,
    runId: crypto.randomUUID(),
    parentRunId: null,
    forwardedProps: { mode: "EXECUTION", request },
    messages: [
      {
        id: crypto.randomUUID(),
        role: "user",
        content: [{ type: "text", text: request.input.text }]
      }
    ]
  }
  return requestAssistantAgUiStream(
    "/agui/run",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(envelope)
    },
    options
  )
}

/**
 * 携带审批决定恢复因 interrupt 而暂停的 run（AAF-104 #10404）。
 *
 * 与新建 run 走同一 {@code POST /agui/run} 入口，仅 {@code resume} 非空——不新建 execution，
 * 服务端按 {@code interruptId}（即 {@code approvalId}）落定决定后续接原 execution。
 */
export function resumeAssistantAgUi(
  params: { threadId: string; approvalId: string; approved: boolean },
  options: AssistantAgUiStreamOptions
): Promise<void> {
  const envelope: AssistantResumeAgUiRunRequest = {
    threadId: params.threadId,
    runId: crypto.randomUUID(),
    parentRunId: null,
    forwardedProps: {},
    messages: [],
    resume: [
      {
        interruptId: params.approvalId,
        status: "resolved",
        payload: { approved: params.approved }
      }
    ]
  }
  return requestAssistantAgUiStream(
    "/agui/run",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(envelope)
    },
    options
  )
}

/** 通过唯一 AG-UI 正文入口执行对话请求。 */
export function executeAssistantChatAgUi(
  request: AssistantChatRequest,
  options: AssistantAgUiStreamOptions
): Promise<void> {
  const envelope: AssistantChatAgUiRunRequest = {
    threadId: request.threadId,
    runId: crypto.randomUUID(),
    parentRunId: null,
    forwardedProps: {
      mode: "CHAT",
      taskModelSelection: request.modelId
        ? { mode: "EXPLICIT", modelId: request.modelId }
        : { mode: "AUTO", modelId: null }
    },
    messages: request.messages.map((message) => ({
      id: crypto.randomUUID(),
      role: message.role,
      content: [{ type: "text", text: message.text }]
    }))
  }
  return requestAssistantAgUiStream(
    "/agui/run",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(envelope)
    },
    options
  )
}
