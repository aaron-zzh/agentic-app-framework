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

/** 对话建议条目 */
export interface AgentSuggestion {
  prompt: string
  label?: string
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
  startRun: () =>
    set({
      phase: "running",
      activeTool: null,
      entries: [],
      suggestions: [],
      subTaskActivities: {},
      selectedRole: null
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
