/**
 * 文案 Assistant 执行逻辑：生成、改写、爆款复制及受控草稿授权/恢复。
 * 草稿由 Agent Loop 通过 ToolGateway 保存，前端仅消费产物引用。
 * @author AaronZZH & Kiro
 */

import { useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import type { StreamingEditorHandle } from "@/features/rich-text-editor"
import {
  type AssistantAgUiEvent,
  type AssistantAgUiStreamOptions,
  type AssistantAuthorizationRequest,
  type AssistantExecutionPhase,
  type AssistantExecutionRequest,
  type AssistantOutputLocale,
  executeAssistantAgUi,
  streamApprovedAssistantAgUi
} from "@/lib/api/assistant-agui"
import { type AafAiTaskEvent, type AafAiTaskEventData, humanApprovalApi } from "@/lib/api/rest/ai"
import { type ScopeSelection, useOrgStore } from "@/lib/store/org-store"
import { useAigcStore } from "../store"

export type CopywritingProcessKind = "phase" | "context" | "tool" | "message" | "safety"

export interface CopywritingProcessEntry {
  id: string
  sequence: number | null
  kind: CopywritingProcessKind
  status: string
  summary: string
  createdAt: string
}

export interface CopywritingToolCall {
  id: string
  name: string
  status: string
}

export interface CopywritingContextSource {
  key: string
  label: string
}

const EMPTY_CONTEXT_SOURCES: CopywritingContextSource[] = []

interface StructuredStreamOptions {
  start: (options: AssistantAgUiStreamOptions) => Promise<void>
  editor: StreamingEditorHandle | null
  fallbackContent: string
  success: (content: string) => void
  errorMessage: string
  continuation?: boolean
}

interface CopywritingExecutionOptions {
  text: string
  skillCode: string
  variables?: Record<string, unknown>
  modelId?: string
  maxCharLen?: number
  locale?: string | null
  imageResourceIds?: string[]
}

const ASSISTANT_OUTPUT_LOCALES: readonly AssistantOutputLocale[] = ["EN", "JA", "KO", "FR", "ES"]

function isAssistantOutputLocale(locale: string): locale is AssistantOutputLocale {
  return ASSISTANT_OUTPUT_LOCALES.some((candidate) => candidate === locale)
}

function assistantOutputLocale(
  locale: string | null | undefined
): AssistantOutputLocale | undefined {
  return locale !== null && locale !== undefined && isAssistantOutputLocale(locale)
    ? locale
    : undefined
}

export const COPYWRITING_ROLE_KEY = "system.role.content-creator"

export function copywritingExecutionRequest({
  text,
  skillCode,
  variables = {},
  modelId,
  maxCharLen,
  locale,
  imageResourceIds = []
}: CopywritingExecutionOptions): AssistantExecutionRequest {
  return {
    execution: {
      interactionMode: "TASK",
      routeConstraint: "FIXED",
      clarificationPolicy: "FAIL_ON_BLOCKER",
      actionAuthorizationPolicy: "REQUEST_ON_DEMAND",
      artifactPersistence: "AUTO_SAVE_DRAFT"
    },
    input: {
      text,
      variables,
      attachments: imageResourceIds.map((resourceId) => ({ type: "IMAGE", resourceId }))
    },
    role: { key: COPYWRITING_ROLE_KEY },
    skill: { code: skillCode },
    knowledge: {
      mode: "DEFAULT",
      knowledgeBaseIds: [],
      topK: 5,
      similarityThreshold: 0.2
    },
    model: modelId ? { mode: "EXPLICIT", modelId } : { mode: "AUTO", modelId: null },
    memory: { mode: "DEFAULT" },
    output: {
      maxCharLen,
      locale: assistantOutputLocale(locale)
    }
  }
}

function maxCharLen(length: "short" | "medium" | "long"): number {
  return { short: 200, medium: 500, long: 3000 }[length]
}

function dataText(data: AafAiTaskEventData, ...keys: string[]): string | null {
  for (const key of keys) {
    const value = data[key]
    if (typeof value === "string" && value.length > 0) return value
  }
  return null
}

function toolCallFromEvent(event: AssistantAgUiEvent): CopywritingToolCall | null {
  if (event.type === "TOOL_CALL_START") {
    return { id: event.toolCallId, name: event.toolName, status: "RUNNING" }
  }
  if (event.type === "TOOL_CALL_RESULT") {
    return {
      id: event.toolCallId,
      name: dataText(event.result, "toolName") ?? "受控工具",
      status: dataText(event.result, "resultState") ?? "COMPLETED"
    }
  }
  if (event.type !== "CUSTOM" || !event.value.type.startsWith("aaf.tool.")) return null
  const { data } = event.value
  const name = dataText(data, "toolName") ?? "受控工具"
  const id = dataText(data, "toolCallId") ?? name
  return { id, name, status: event.value.status }
}

function draftArtifactId(data: AafAiTaskEventData): number | null {
  const artifactId = data.artifactId
  return data.artifactState === "DRAFT" &&
    typeof artifactId === "number" &&
    Number.isSafeInteger(artifactId) &&
    artifactId > 0
    ? artifactId
    : null
}

export function assistantDraftArtifactId(event: AssistantAgUiEvent): number | null {
  if (event.type === "TOOL_CALL_RESULT") return draftArtifactId(event.result)
  return event.type === "CUSTOM" && event.value.type === "aaf.tool.completed"
    ? draftArtifactId(event.value.data)
    : null
}

function aafEventSummary(event: AafAiTaskEvent): {
  kind: CopywritingProcessKind
  summary: string
} {
  if (event.type === "aaf.task.started") return { kind: "phase", summary: "执行已开始" }
  if (event.type === "aaf.task.completed") return { kind: "phase", summary: "执行已安全完成" }
  if (event.type === "aaf.task.failed") return { kind: "phase", summary: "执行失败" }
  if (event.type === "aaf.task.paused") return { kind: "phase", summary: "执行已持久化暂停" }
  if (event.type === "aaf.task.canceled") return { kind: "phase", summary: "执行已取消" }
  if (event.type === "aaf.authorization.requested") {
    const tool = dataText(event.data, "toolName") ?? "受控工具"
    return { kind: "safety", summary: `${tool} 等待用户授权` }
  }
  if (event.type === "aaf.authorization.granted") {
    return { kind: "safety", summary: "工具授权已批准，正在恢复执行" }
  }
  if (event.type === "aaf.authorization.denied") {
    return { kind: "safety", summary: "工具授权已拒绝" }
  }
  if (event.type === "aaf.task.rejected") return { kind: "phase", summary: "命令已被拒绝" }
  if (event.type === "aaf.message.progress") {
    return { kind: "message", summary: "正在接收安全输出" }
  }
  if (event.type === "aaf.message.completed") {
    return { kind: "message", summary: "安全输出已完成" }
  }
  if (event.type === "aaf.task.status_changed") {
    const task = dataText(event.data, "stage", "phase", "summaryCode") ?? "执行任务"
    return { kind: "safety", summary: `${task}：${event.status}` }
  }
  if (event.type.startsWith("aaf.tool.")) {
    const tool = dataText(event.data, "toolName") ?? "受控工具"
    return { kind: "tool", summary: `${tool}：${event.status}` }
  }
  if (event.type === "aaf.recovery.started") {
    return { kind: "phase", summary: "授权已确认，正在恢复执行" }
  }
  return { kind: "safety", summary: `安全阶段：${event.type}` }
}

function safeEventSummary(event: AssistantAgUiEvent): {
  kind: CopywritingProcessKind
  summary: string
} {
  if (event.type === "RUN_STARTED") return { kind: "phase", summary: "执行已开始" }
  if (event.type === "RUN_FINISHED") return { kind: "phase", summary: "执行已安全完成" }
  if (event.type === "RUN_ERROR") return { kind: "phase", summary: "执行失败" }
  if (event.type === "TEXT_MESSAGE_CONTENT") {
    return { kind: "message", summary: "正在接收安全输出" }
  }
  if (event.type === "TEXT_MESSAGE_END") {
    return { kind: "message", summary: "安全输出已完成" }
  }
  if (event.type === "TOOL_CALL_START") {
    return { kind: "tool", summary: `${event.toolName}：RUNNING` }
  }
  if (event.type === "TOOL_CALL_RESULT") {
    const tool = dataText(event.result, "toolName") ?? "受控工具"
    const status = dataText(event.result, "resultState") ?? "COMPLETED"
    return { kind: "tool", summary: `${tool}：${status}` }
  }
  if (event.type === "CUSTOM") return aafEventSummary(event.value)
  return { kind: "message", summary: "正在准备安全输出" }
}

function eventStatus(event: AssistantAgUiEvent): string {
  if (event.type === "CUSTOM") return event.value.status
  if (event.type === "RUN_STARTED") return "RUNNING"
  if (event.type === "RUN_FINISHED") return "COMPLETED"
  if (event.type === "RUN_ERROR") return "FAILED"
  if (event.type === "TOOL_CALL_START") return "RUNNING"
  if (event.type === "TOOL_CALL_RESULT") {
    return dataText(event.result, "resultState") ?? "COMPLETED"
  }
  return "STREAMING"
}

function copywritingScopeKey(
  activeUserId: string | null,
  scope: ScopeSelection | null
): string | null {
  if (activeUserId === null || scope?.kind !== "workspace") return null
  return JSON.stringify([activeUserId, scope.kind, scope.orgId, scope.workspaceId])
}

/** 文案生成相关状态与动作；参数（type、length 等）直接读 store */
export function useCopywriting() {
  const activeUserId = useOrgStore((state) => state.activeUserId)
  const currentScope = useOrgStore((state) => state.currentScope)
  const scopeKey = copywritingScopeKey(activeUserId, currentScope)
  const boundScopeKey = useAigcStore((state) => state.copywritingScopeKey)
  const bindScope = useAigcStore((state) => state.bindCopywritingScope)
  const prompt = useAigcStore((state) => state.copywritingPrompt)
  const setPrompt = useAigcStore((state) => state.setCopywritingPrompt)
  const content = useAigcStore((state) => state.copywritingContent)
  const setContent = useAigcStore((state) => state.setCopywritingContent)
  const storedDocumentId = useAigcStore((state) => state.copywritingDocumentId)
  const generating = useAigcStore((state) => state.copywritingGenerating)
  const type = useAigcStore((state) => state.copywritingType)
  const model = useAigcStore((state) => state.copywritingModel)
  const referenceImages = useAigcStore((state) => state.copywritingReferenceImages)
  const scopeMatches = scopeKey !== null && boundScopeKey === scopeKey
  const documentId = scopeMatches ? storedDocumentId : null

  const [phase, setPhase] = useState<AssistantExecutionPhase>("idle")
  const [processEntries, setProcessEntries] = useState<CopywritingProcessEntry[]>([])
  const [toolCalls, setToolCalls] = useState<CopywritingToolCall[]>([])
  const [pendingApproval, setPendingApproval] = useState<AssistantAuthorizationRequest | null>(null)
  const [approvalReady, setApprovalReady] = useState(false)
  const [approvalLoading, setApprovalLoading] = useState(false)
  const pausedStreamRef = useRef<StructuredStreamOptions | null>(null)
  const streamingEditorRef = useRef<StreamingEditorHandle>(null)

  const [viralStep, setViralStep] = useState<1 | 2 | 3>(1)
  const [viralSource, setViralSource] = useState("")
  const [viralAnalysis, setViralAnalysis] = useState("")
  const [analyzing, setAnalyzing] = useState(false)
  const analysisEditorRef = useRef<StreamingEditorHandle>(null)
  const resultEditorRef = useRef<StreamingEditorHandle>(null)

  useEffect(() => {
    bindScope(scopeKey)
  }, [bindScope, scopeKey])

  function appendProcessEntry(entry: CopywritingProcessEntry) {
    setProcessEntries((current) => {
      if (
        entry.kind === "message" &&
        entry.summary === "正在接收安全输出" &&
        current.some((item) => item.summary === entry.summary)
      ) {
        return current
      }
      return [...current.slice(-49), entry]
    })
  }

  function beginExecution(summary: string) {
    setPhase("running")
    if (scopeKey !== null) {
      useAigcStore.getState().setCopywritingPersistedDocument(scopeKey, null)
    }
    setPendingApproval(null)
    setApprovalReady(false)
    pausedStreamRef.current = null
    setToolCalls([])
    setProcessEntries([
      {
        id: `local-${Date.now()}`,
        sequence: null,
        kind: "phase",
        status: "RUNNING",
        summary,
        createdAt: new Date().toISOString()
      }
    ])
  }

  function recordEvent(event: AssistantAgUiEvent) {
    const display = safeEventSummary(event)
    const publicEvent = event.type === "CUSTOM" ? event.value : null
    appendProcessEntry({
      id: publicEvent?.eventId ?? `${event.runId}-${event.type}-${Date.now()}`,
      sequence: publicEvent?.sequence ?? null,
      kind: display.kind,
      status: eventStatus(event),
      summary: display.summary,
      createdAt: publicEvent?.createdAt ?? new Date().toISOString()
    })

    const tool = toolCallFromEvent(event)
    if (tool !== null) {
      setToolCalls((current) => {
        const existing = current.findIndex((item) => item.id === tool.id)
        if (existing === -1) return [...current, tool]
        return current.map((item, index) => (index === existing ? tool : item))
      })
    }
    const artifactId = assistantDraftArtifactId(event)
    if (artifactId !== null && scopeKey !== null) {
      useAigcStore.getState().setCopywritingPersistedDocument(scopeKey, artifactId)
    }
    if (event.type === "RUN_STARTED") setPhase("running")
    if (event.type === "RUN_FINISHED") setPhase("success")
    if (event.type === "RUN_ERROR") setPhase("error")
    if (event.type === "CUSTOM") {
      if (event.value.type === "aaf.authorization.requested") {
        setPhase("awaiting_authorization")
      }
      if (
        event.value.type === "aaf.authorization.granted" ||
        event.value.type === "aaf.recovery.started"
      ) {
        setPhase("running")
      }
    }
  }

  async function runStructuredStream(options: StructuredStreamOptions) {
    const { start, editor, fallbackContent, success, errorMessage, continuation = false } = options
    if (continuation) {
      setPhase("running")
      appendProcessEntry({
        id: `resume-${Date.now()}`,
        sequence: null,
        kind: "phase",
        status: "RECOVERING",
        summary: "授权已确认，正在恢复执行",
        createdAt: new Date().toISOString()
      })
    } else {
      beginExecution("正在准备安全执行上下文")
    }
    editor?.start()
    let accumulated = ""
    let settled = false

    await start({
      onEvent: recordEvent,
      onChunk: (chunk) => {
        accumulated += chunk
        success(accumulated)
        editor?.push(chunk)
      },
      onApprovalRequired: (approval) => {
        pausedStreamRef.current = options
        setPendingApproval(approval)
        setApprovalReady(false)
        setPhase("awaiting_authorization")
      },
      onPaused: () => {
        if (settled) return
        settled = true
        success(fallbackContent)
        editor?.reset()
        setApprovalReady(true)
        setPhase("awaiting_authorization")
      },
      onDone: () => {
        if (settled) return
        settled = true
        setPendingApproval(null)
        setApprovalReady(false)
        pausedStreamRef.current = null
        const finalContent = accumulated || fallbackContent
        success(finalContent)
        editor?.done(finalContent)
        setPhase("success")
      },
      onError: (error) => {
        if (settled) return
        settled = true
        pausedStreamRef.current = null
        success(fallbackContent)
        editor?.reset()
        setPhase("error")
        appendProcessEntry({
          id: `error-${Date.now()}`,
          sequence: null,
          kind: "phase",
          status: "ERROR",
          summary: error.message || errorMessage,
          createdAt: new Date().toISOString()
        })
        toast.error(error.message || errorMessage)
      }
    })
  }

  async function handleApprovalDecision(decision: "APPROVED" | "REJECTED") {
    const approval = pendingApproval
    if (approval === null || !approvalReady || approvalLoading) return

    setApprovalLoading(true)
    try {
      await humanApprovalApi.decide(approval.approvalId, {
        decision,
        reason: decision === "REJECTED" ? "用户拒绝了工具授权" : undefined
      })
      if (decision === "REJECTED") {
        setPendingApproval(null)
        setApprovalReady(false)
        pausedStreamRef.current = null
        setPhase("paused")
        appendProcessEntry({
          id: `approval-rejected-${Date.now()}`,
          sequence: null,
          kind: "safety",
          status: "REJECTED",
          summary: "已拒绝受控工具操作，执行保持暂停",
          createdAt: new Date().toISOString()
        })
        toast.info("已拒绝工具授权")
        return
      }

      const continuation = pausedStreamRef.current
      if (continuation === null) throw new Error("缺少可恢复的 Assistant 执行上下文")
      setPendingApproval(null)
      setApprovalReady(false)
      useAigcStore.getState().setCopywritingGenerating(true)
      try {
        await runStructuredStream({
          ...continuation,
          start: (streamOptions) => streamApprovedAssistantAgUi(approval.approvalId, streamOptions),
          errorMessage: "授权后恢复执行失败",
          continuation: true
        })
      } finally {
        useAigcStore.getState().setCopywritingGenerating(false)
      }
    } catch (error) {
      setPhase("awaiting_authorization")
      toast.error(error instanceof Error ? error.message : "处理工具授权失败")
    } finally {
      setApprovalLoading(false)
    }
  }

  async function handleGenerate() {
    const state = useAigcStore.getState()
    if (state.copywritingGenerating) return
    if (!state.copywritingPrompt.trim()) {
      toast.warning("请先输入创作主题或要求")
      return
    }

    state.setCopywritingGenerating(true)
    const previousContent = state.copywritingContent
    try {
      await runStructuredStream({
        start: (streamOptions) =>
          executeAssistantAgUi(
            copywritingExecutionRequest({
              text: state.copywritingPrompt,
              skillCode: state.copywritingType,
              modelId: state.copywritingModel || undefined,
              maxCharLen: maxCharLen(state.copywritingLength),
              locale: state.copywritingTranslateTo,
              imageResourceIds: state.copywritingReferenceImages.map((image) => image.key)
            }),
            streamOptions
          ),
        editor: streamingEditorRef.current,
        fallbackContent: previousContent,
        success: setContent,
        errorMessage: "生成失败"
      })
    } finally {
      useAigcStore.getState().setCopywritingGenerating(false)
    }
  }

  async function handleRewrite() {
    const state = useAigcStore.getState()
    if (state.copywritingGenerating || !state.copywritingContent.trim()) return

    state.setCopywritingGenerating(true)
    const original = state.copywritingContent
    try {
      await runStructuredStream({
        start: (streamOptions) =>
          executeAssistantAgUi(
            copywritingExecutionRequest({
              text: "请改写提供的文案",
              skillCode: state.copywritingType,
              variables: { content: original },
              modelId: state.copywritingModel || undefined
            }),
            streamOptions
          ),
        editor: streamingEditorRef.current,
        fallbackContent: original,
        success: setContent,
        errorMessage: "改写失败"
      })
    } finally {
      useAigcStore.getState().setCopywritingGenerating(false)
    }
  }

  async function handleAnalyze() {
    if (!viralSource.trim()) return
    setAnalyzing(true)
    setViralAnalysis("")
    setViralStep(2)
    try {
      await runStructuredStream({
        start: (streamOptions) =>
          executeAssistantAgUi(
            copywritingExecutionRequest({
              text: "请分析提供的爆款内容结构",
              skillCode: "biz-analysis",
              variables: { content: viralSource },
              modelId: model || undefined
            }),
            streamOptions
          ),
        editor: analysisEditorRef.current,
        fallbackContent: viralAnalysis,
        success: setViralAnalysis,
        errorMessage: "分析失败"
      })
    } finally {
      setAnalyzing(false)
    }
  }

  async function handleViralGenerate() {
    const state = useAigcStore.getState()
    if (state.copywritingGenerating) return

    state.setCopywritingGenerating(true)
    setViralStep(3)
    const previousContent = state.copywritingContent
    try {
      await runStructuredStream({
        start: (streamOptions) =>
          executeAssistantAgUi(
            copywritingExecutionRequest({
              text: "请根据提供的爆款结构分析创作文案",
              skillCode: "redbook",
              variables: { analysis: viralAnalysis },
              modelId: state.copywritingModel || undefined
            }),
            streamOptions
          ),
        editor: resultEditorRef.current,
        fallbackContent: previousContent,
        success: setContent,
        errorMessage: "生成失败"
      })
    } finally {
      useAigcStore.getState().setCopywritingGenerating(false)
    }
  }

  return {
    prompt,
    setPrompt,
    content,
    setContent,
    generating,
    documentId,
    phase,
    pendingApproval,
    approvalReady,
    approvalLoading,
    handleApprovalDecision,
    processEntries,
    contextSources: EMPTY_CONTEXT_SOURCES,
    toolCalls,
    selectedSkillKey: type,
    selectedModelId: model || null,
    selectedMaterials: referenceImages.map((image) => ({ id: image.key, label: image.name })),
    streamingEditorRef,
    viralStep,
    setViralStep,
    viralSource,
    setViralSource,
    viralAnalysis,
    setViralAnalysis,
    analyzing,
    analysisEditorRef,
    resultEditorRef,
    handleGenerate,
    handleRewrite,
    handleAnalyze,
    handleViralGenerate
  }
}
