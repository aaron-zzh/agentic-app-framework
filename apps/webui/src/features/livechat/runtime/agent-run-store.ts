/**
 * Agent 运行状态 store——客户端瞬时 UI 状态（非服务端缓存）
 * 由 ag-ui-runtime 的 agent.subscribe 写入，AgentRunStatus 组件读取
 * @author AaronZZH & Kiro
 */
import { create } from "zustand"

export type AgentRunPhase = "idle" | "running" | "finished" | "error"

/** 运行过程中的一条事件记录 */
export interface AgentRunEntry {
  type: string
  title?: string
  message?: string
  timestamp: number
}

/**
 * 单次模型调用的诊断信息（AAF-114 #11412）——只保留可安全展示给用户的字段。
 *
 * 数据源为后端 `aaf.model.completed` CUSTOM 事件，`modelId` 是 AAF 内部模型标识（不是供应商原始模型名），
 * 不携带任何 provider 凭据、原始 API 响应或内部路由细节，默认可直接展示。
 */
export interface DiagnosticInfo {
  runId?: string
  modelId?: string
  inputTokens?: number
  outputTokens?: number
  cachedTokens?: number
  durationSeconds?: number
}

/**
 * 对话建议条目（AAF-114 #11412 增量：标题/描述/fill-only 自 auto-send/恢复语义）。
 *
 * `label` 保留兼容旧用法（无 title 时的展示文案回退）；`title`/`description` 是新增的结构化展示字段。
 * `autoSend` 缺省为 true（保持既有欢迎页建议全部自动发送的行为不变）；显式传 false 时点击只填入
 * composer 不自动发送（fill-only），适合需要用户先编辑再发送的建议场景。
 */
export interface AgentSuggestion {
  prompt: string
  label?: string
  /** 建议标题，展示优先级高于 label/prompt */
  title?: string
  /** 建议描述，标题下方的补充说明 */
  description?: string
  /** 点击后是否自动发送；缺省 true，与现有欢迎页建议行为一致 */
  autoSend?: boolean
}

/** AIGC 异步任务卡片（通过 ui_block CustomEvent 写入） */
export interface AigcTaskCard {
  taskId: number
  mediaType: "image" | "video" | "music"
  status: "PENDING" | "SUCCESS" | "FAIL"
  prompt: string
  message: string
  /** 完成后的媒体 URL */
  url?: string
}

/**
 * 子任务活动卡片（AG-UI ActivitySnapshot/ActivityDelta，activityType="SUBTASK"）
 * 由后端 InternalNodeEventConverter 随子任务 EXECUTION_STARTED/COMPLETED/FAILED/CANCELED 投影，
 * key 为 messageId（等同后端 subTaskId）
 */
export interface SubTaskActivity {
  subTaskId: string
  kind: string
  roleKey: string
  status: string
}

/**
 * 本次执行冻结的角色（AG-UI CUSTOM `aaf.role.resolved`，AAF-107 #10709）。
 * 由后端 AssistantApplicationService 每次执行无条件投影，走 PublicEventFallbackConverter 兜底 CUSTOM，
 * 不区分是用户显式指定还是模型（DefaultRoleSelector.selectByModel）动态选择——routeConstraint 可用于区分。
 */
export interface SelectedRole {
  roleKey: string
  roleName: string
  routeConstraint: string
}

interface AgentRunState {
  phase: AgentRunPhase
  activeTool: string | null
  entries: AgentRunEntry[]
  suggestions: AgentSuggestion[]
  aigcTasks: AigcTaskCard[]
  subTaskActivities: Record<string, SubTaskActivity>
  selectedRole: SelectedRole | null
  /** 当前活跃 AG-UI 线程 ID（AAF-114 #11412），供反馈等需要跨组件层级关联当前会话的场景读取 */
  currentThreadId: string | undefined
  /** 最近一次模型调用的诊断信息（AAF-114 #11412），run 开始时清空，收到 aaf.model.completed 时更新 */
  diagnostic: DiagnosticInfo
  startRun: () => void
  finishRun: () => void
  errorRun: (message?: string) => void
  startTool: (name: string) => void
  endTool: () => void
  pushEntry: (entry: AgentRunEntry) => void
  setSuggestions: (suggestions: AgentSuggestion[]) => void
  pushAigcTask: (card: AigcTaskCard) => void
  updateAigcTask: (taskId: number, patch: Partial<AigcTaskCard>) => void
  upsertSubTaskActivity: (activity: SubTaskActivity) => void
  setSelectedRole: (role: SelectedRole) => void
  setCurrentThreadId: (threadId: string | undefined) => void
  setRunId: (runId: string | undefined) => void
  updateDiagnostic: (patch: Partial<DiagnosticInfo>) => void
}

const MAX_ENTRIES = 50

function append(list: AgentRunEntry[], entry: AgentRunEntry): AgentRunEntry[] {
  const next = [...list, entry]
  return next.length > MAX_ENTRIES ? next.slice(next.length - MAX_ENTRIES) : next
}

export const useAgentRunStore = create<AgentRunState>((set) => ({
  phase: "idle",
  activeTool: null,
  entries: [],
  suggestions: [],
  aigcTasks: [],
  subTaskActivities: {},
  selectedRole: null,
  currentThreadId: undefined,
  diagnostic: {},
  setCurrentThreadId: (threadId) => set({ currentThreadId: threadId }),
  setRunId: (runId) => set((s) => ({ diagnostic: { ...s.diagnostic, runId } })),
  updateDiagnostic: (patch) => set((s) => ({ diagnostic: { ...s.diagnostic, ...patch } })),
  startRun: () =>
    set({
      phase: "running",
      activeTool: null,
      entries: [],
      suggestions: [],
      subTaskActivities: {},
      selectedRole: null,
      diagnostic: {}
    }),
  finishRun: () => set({ phase: "finished", activeTool: null }),
  errorRun: (message) =>
    set((s) => ({
      phase: "error",
      activeTool: null,
      entries: append(s.entries, { type: "RUN_ERROR", message, timestamp: Date.now() })
    })),
  startTool: (name) =>
    set((s) => ({
      activeTool: name,
      entries: append(s.entries, {
        type: "TOOL_CALL_STARTED",
        title: name,
        timestamp: Date.now()
      })
    })),
  endTool: () => set({ activeTool: null }),
  pushEntry: (entry) => set((s) => ({ entries: append(s.entries, entry) })),
  setSuggestions: (suggestions) => set({ suggestions }),
  pushAigcTask: (card) => set((s) => ({ aigcTasks: [...s.aigcTasks, card] })),
  updateAigcTask: (taskId, patch) =>
    set((s) => ({
      aigcTasks: s.aigcTasks.map((t) => (t.taskId === taskId ? { ...t, ...patch } : t))
    })),
  upsertSubTaskActivity: (activity) =>
    set((s) => ({
      subTaskActivities: { ...s.subTaskActivities, [activity.subTaskId]: activity }
    })),
  setSelectedRole: (role) => set({ selectedRole: role })
}))
