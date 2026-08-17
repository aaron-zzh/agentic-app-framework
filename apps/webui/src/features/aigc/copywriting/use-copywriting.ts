/**
 * 文案面板生成逻辑：口播/小红书生成、改写、爆款复制三步向导（分析→生成）、保存文档
 * 统一收拢生成相关状态与流式回调，面板组件只负责布局
 * @author AaronZZH & Kiro
 */

import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import type { StreamingEditorHandle } from "@/features/rich-text-editor"
import type { AiSseOptions } from "@/lib/api/ai-stream"
import {
  type AssistantContextSource,
  type AssistantExecutionPhase,
  type AssistantExecutionRequest,
  type AssistantOutputLocale,
  type AssistantSafeEvent,
  assistantCompletedText,
  executeAssistant,
  isAssistantCompletedEvent,
  isAssistantFailureEvent
} from "@/lib/api/headless-assistant"
import { aigcProjectApi, copywritingKeys, useAttachAigcProjectDocument } from "@/lib/api/rest/ai"
import { useCreateDocument, useUpdateDocument } from "@/lib/api/rest/system"
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

interface StructuredStreamOptions {
  start: (options: AiSseOptions) => Promise<void>
  editor: StreamingEditorHandle | null
  fallbackContent: string
  success: (content: string) => void
  errorMessage: string
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

function copywritingExecutionRequest({
  text,
  skillCode,
  variables = {},
  modelId,
  maxCharLen,
  locale,
  imageResourceIds = []
}: CopywritingExecutionOptions): AssistantExecutionRequest {
  return {
    input: {
      text,
      variables,
      attachments: imageResourceIds.map((resourceId) => ({ type: "IMAGE", resourceId }))
    },
    skill: { code: skillCode },
    knowledge: {
      knowledgeBaseIds: [],
      includePublic: false,
      topK: 5,
      similarityThreshold: 0.2
    },
    model: modelId ? { mode: "EXPLICIT", modelId } : { mode: "AUTO", modelId: null },
    memory: { mode: "DISABLED" },
    output: {
      maxCharLen,
      locale:
        locale === null || locale === undefined ? undefined : (locale as AssistantOutputLocale)
    }
  }
}

function maxCharLen(length: "short" | "medium" | "long"): number {
  return { short: 200, medium: 500, long: 3000 }[length]
}

function payloadText(payload: Record<string, unknown>, ...keys: string[]): string | null {
  for (const key of keys) {
    const value = payload[key]
    if (typeof value === "string" && value.length > 0) return value
  }
  return null
}

function contextSourceItem(source: AssistantContextSource): CopywritingContextSource | null {
  const sourceKey = payloadText(source, "sourceKey")
  if (sourceKey === null) return null
  return {
    key: sourceKey,
    label: payloadText(source, "summary", "reason", "sourceKey", "type") ?? sourceKey
  }
}

function toolCallFromEvent(event: AssistantSafeEvent): CopywritingToolCall {
  const name = payloadText(event.payload, "toolName", "name") ?? "受控工具"
  const id = payloadText(event.payload, "toolCallId", "callId", "id") ?? name
  return { id, name, status: event.status }
}

function safeEventSummary(event: AssistantSafeEvent): {
  kind: CopywritingProcessKind
  summary: string
} {
  if (event.type === "EXECUTION_STARTED") return { kind: "phase", summary: "执行已开始" }
  if (event.type === "EXECUTION_COMPLETED") return { kind: "phase", summary: "执行已安全完成" }
  if (event.type === "EXECUTION_FAILED") return { kind: "phase", summary: "执行失败" }
  if (event.type === "EXECUTION_CANCELED") return { kind: "phase", summary: "执行已取消" }
  if (event.type === "COMMAND_REJECTED") return { kind: "phase", summary: "命令已被拒绝" }
  if (event.type === "MESSAGE_DELTA") return { kind: "message", summary: "正在接收安全输出" }
  if (event.type === "MESSAGE_COMPLETED") return { kind: "message", summary: "安全输出已完成" }
  if (event.type === "TASK_STATUS_CHANGED") {
    const task = payloadText(event.payload, "summary", "stage", "taskName", "task") ?? "执行任务"
    return { kind: "safety", summary: `${task}：${event.status}` }
  }
  if (event.type.startsWith("TOOL_CALL_")) {
    const tool = toolCallFromEvent(event)
    return { kind: "tool", summary: `${tool.name}：${event.status}` }
  }
  if (event.contextSources.length > 0) {
    return { kind: "context", summary: `已装载 ${event.contextSources.length} 个授权上下文来源` }
  }
  return { kind: "safety", summary: `安全阶段：${event.type}` }
}

function copywritingScopeKey(
  activeUserId: string | null,
  scope: ScopeSelection | null
): string | null {
  if (activeUserId === null || scope?.kind !== "workspace") return null
  return JSON.stringify([activeUserId, scope.kind, scope.orgId, scope.workspaceId])
}

function currentCopywritingScopeKey(): string | null {
  const orgState = useOrgStore.getState()
  return copywritingScopeKey(orgState.activeUserId, orgState.currentScope)
}

/** 文案生成相关状态与动作；参数（type、length 等）直接读 store */
export function useCopywriting(projectId?: number) {
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
  const storedPersistedContent = useAigcStore((state) => state.copywritingPersistedContent)
  const storedLinkedProjectIds = useAigcStore((state) => state.copywritingLinkedProjectIds)
  const generating = useAigcStore((state) => state.copywritingGenerating)
  const saving = useAigcStore((state) => state.copywritingSaving)
  const type = useAigcStore((state) => state.copywritingType)
  const model = useAigcStore((state) => state.copywritingModel)
  const referenceImages = useAigcStore((state) => state.copywritingReferenceImages)
  const scopeMatches = scopeKey !== null && boundScopeKey === scopeKey
  const documentId = scopeMatches ? storedDocumentId : null
  const persistedContent = scopeMatches ? storedPersistedContent : ""
  const linkedProjectIds = scopeMatches ? storedLinkedProjectIds : []

  const [phase, setPhase] = useState<AssistantExecutionPhase>("idle")
  const [processEntries, setProcessEntries] = useState<CopywritingProcessEntry[]>([])
  const [contextSources, setContextSources] = useState<CopywritingContextSource[]>([])
  const [toolCalls, setToolCalls] = useState<CopywritingToolCall[]>([])
  const streamingEditorRef = useRef<StreamingEditorHandle>(null)

  const [viralStep, setViralStep] = useState<1 | 2 | 3>(1)
  const [viralSource, setViralSource] = useState("")
  const [viralAnalysis, setViralAnalysis] = useState("")
  const [analyzing, setAnalyzing] = useState(false)
  const analysisEditorRef = useRef<StreamingEditorHandle>(null)
  const resultEditorRef = useRef<StreamingEditorHandle>(null)

  const queryClient = useQueryClient()
  const createDoc = useCreateDocument()
  const updateDoc = useUpdateDocument()
  const linkDoc = useAttachAigcProjectDocument()

  useEffect(() => {
    bindScope(scopeKey)
  }, [bindScope, scopeKey])

  const saved =
    documentId !== null &&
    content === persistedContent &&
    (projectId == null || linkedProjectIds.includes(projectId))

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
    setContextSources([])
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

  function recordEvent(event: AssistantSafeEvent) {
    const display = safeEventSummary(event)
    appendProcessEntry({
      id: `${event.sequence}-${event.type}`,
      sequence: event.sequence,
      kind: display.kind,
      status: event.status,
      summary: display.summary,
      createdAt: event.createdAt
    })

    if (event.contextSources.length > 0) {
      const sources = event.contextSources
        .map(contextSourceItem)
        .filter((source): source is CopywritingContextSource => source !== null)
      setContextSources((current) => {
        const bySourceKey = new Map(current.map((source) => [source.key, source]))
        for (const source of sources) bySourceKey.set(source.key, source)
        return [...bySourceKey.values()]
      })
    }
    if (event.type.startsWith("TOOL_CALL_")) {
      const tool = toolCallFromEvent(event)
      setToolCalls((current) => {
        const existing = current.findIndex((item) => item.id === tool.id)
        if (existing === -1) return [...current, tool]
        return current.map((item, index) => (index === existing ? tool : item))
      })
    }
    if (event.type === "EXECUTION_STARTED") setPhase("running")
    if (isAssistantCompletedEvent(event)) setPhase("success")
    if (isAssistantFailureEvent(event)) setPhase("error")
  }

  async function runStructuredStream(options: StructuredStreamOptions) {
    const { start, editor, fallbackContent, success, errorMessage } = options
    beginExecution("正在准备安全执行上下文")
    editor?.start()
    let accumulated = ""
    let completedText: string | null = null
    let settled = false

    await start({
      onEvent: (event) => {
        recordEvent(event)
        completedText = assistantCompletedText(event) ?? completedText
      },
      onChunk: (chunk) => {
        accumulated += chunk
        success(accumulated)
        editor?.push(chunk)
      },
      onDone: (event) => {
        if (settled) return
        settled = true
        completedText = assistantCompletedText(event) ?? completedText
        const finalContent = (completedText ?? accumulated) || fallbackContent
        success(finalContent)
        editor?.done(finalContent)
        setPhase("success")
      },
      onError: (error) => {
        if (settled) return
        settled = true
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

  async function handleSaveDoc() {
    const activeScopeKey = currentCopywritingScopeKey()
    if (activeScopeKey === null) return

    const currentStore = useAigcStore.getState()
    currentStore.bindCopywritingScope(activeScopeKey)
    const state = useAigcStore.getState()
    const contentSnapshot = state.copywritingContent
    if (state.copywritingSaving || state.copywritingGenerating || !contentSnapshot.trim()) return

    state.setCopywritingSaving(true)
    const lines = contentSnapshot.trim().split("\n")
    const title = lines[0].replace(/^#+\s*/, "").trim() || "文案"
    const initialDocumentId = state.copywritingDocumentId
    let activeDocumentId = initialDocumentId

    try {
      if (activeDocumentId === null) {
        const document = await createDoc.mutateAsync({
          title,
          content: contentSnapshot,
          docType: "copywriting",
          filePath: ""
        })
        if (document.id === null) throw new Error("新建文档未返回有效 ID")
        activeDocumentId = document.id
        useAigcStore
          .getState()
          .setCopywritingPersistedDocument(activeScopeKey, activeDocumentId, contentSnapshot)
        void queryClient.invalidateQueries({ queryKey: copywritingKeys.all })
      } else if (contentSnapshot !== state.copywritingPersistedContent) {
        await updateDoc.mutateAsync({
          id: activeDocumentId,
          title,
          content: contentSnapshot,
          docType: "copywriting"
        })
        useAigcStore
          .getState()
          .setCopywritingPersistedDocument(activeScopeKey, activeDocumentId, contentSnapshot)
        void queryClient.invalidateQueries({ queryKey: copywritingKeys.all })
      }

      if (useAigcStore.getState().copywritingScopeKey !== activeScopeKey) return

      if (projectId != null && !state.copywritingLinkedProjectIds.includes(projectId)) {
        try {
          const project = await aigcProjectApi.project(projectId)
          await linkDoc.mutateAsync({
            projectId,
            documentVersionId: activeDocumentId,
            role: "output",
            expectedProjectVersion: project.version
          })
          useAigcStore.getState().markCopywritingProjectLinked(activeScopeKey, projectId)
          void queryClient.invalidateQueries({ queryKey: copywritingKeys.all })
        } catch {
          toast.warning("文案已保存，但关联项目失败；再次保存可重试关联")
          return
        }
      }

      toast.success(initialDocumentId === null ? "文案已保存" : "文案更新已保存")
    } catch {
      toast.error("文案保存失败")
    } finally {
      useAigcStore.getState().setCopywritingSaving(false)
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
          executeAssistant(
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
          executeAssistant(
            copywritingExecutionRequest({
              text: "请改写提供的文案",
              skillCode: "aigc-copywriting",
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
          executeAssistant(
            copywritingExecutionRequest({
              text: "请分析提供的爆款内容结构",
              skillCode: "aigc-copywriting",
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
          executeAssistant(
            copywritingExecutionRequest({
              text: "请根据提供的爆款结构分析创作文案",
              skillCode: "aigc-copywriting",
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
    saved,
    saving,
    documentId,
    phase,
    processEntries,
    contextSources,
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
    handleSaveDoc,
    handleGenerate,
    handleRewrite,
    handleAnalyze,
    handleViralGenerate
  }
}
