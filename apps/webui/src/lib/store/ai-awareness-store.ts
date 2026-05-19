/**
 * AI 感知服务 Store——存储页面上下文和用户操作历史
 * 供 AI Agent 理解当前页面状态，主动提供辅助
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"

/** 操作历史最大保留条数 */
const MAX_ACTIONS = 50

/** 用户操作记录 */
export interface ActionRecord {
  type: string
  entity?: string
  timestamp: number
  detail?: string
}

/** AI 页面上下文 */
export interface AIPageContext {
  currentEntity?: string
  currentView?: string
  visibleFields?: string[]
  formValues?: Record<string, unknown>
  recentActions?: ActionRecord[]
  selectedRecords?: number
}

/** 页面上下文设置参数（不含 recentActions，由 recordAction 管理） */
type PageContextPayload = Omit<AIPageContext, "recentActions">

interface AIAwarenessState {
  /** 当前页面上下文（不含操作历史） */
  pageContext: PageContextPayload
  /** 操作历史（最近 50 步） */
  actions: ActionRecord[]
  /** 是否启用 AI 感知 */
  enabled: boolean

  /** 设置页面上下文 */
  setPageContext: (ctx: Partial<PageContextPayload>) => void
  /** 记录一次用户操作 */
  recordAction: (action: Omit<ActionRecord, "timestamp">) => void
  /** 收集完整上下文（合并页面状态 + 操作历史） */
  collectContext: () => AIPageContext
  /** 开关 AI 感知 */
  setEnabled: (enabled: boolean) => void
  /** 重置状态 */
  reset: () => void
}

export const useAIAwarenessStore = create<AIAwarenessState>((set, get) => ({
  pageContext: {},
  actions: [],
  enabled: true,

  setPageContext: (ctx) =>
    set((s) => ({ pageContext: { ...s.pageContext, ...ctx } })),

  recordAction: (action) =>
    set((s) => ({
      actions: [...s.actions, { ...action, timestamp: Date.now() }].slice(-MAX_ACTIONS),
    })),

  collectContext: () => {
    const { pageContext, actions } = get()
    return {
      ...pageContext,
      recentActions: actions,
    }
  },

  setEnabled: (enabled) => set({ enabled }),

  reset: () => set({ pageContext: {}, actions: [] }),
}))
