/**
 * Studio Shell 多 tab 状态
 *
 * 设计：
 * - 五度空间多 tab：可同时打开 ≤9 个 tab，每次点侧栏 → 已开则切到，未开则新建
 * - 项目工作台例外：每个项目独立 tab
 * - 持久化：sessionStorage（关浏览器丢失，刷新保留）
 * - URL 单 active param 同步，浏览器前进/后退仍可用
 *
 * 详见 docs/design/apps/webui/user-studio-mvp.md A4 多 tab 详解
 */

import { create } from "zustand"
import { createJSONStorage, persist } from "zustand/middleware"
import type { StudioWorkspace } from "../nav-config"

const MAX_TABS = 9

export interface StudioTab {
  /** 唯一 id（创建时生成） */
  id: string
  /** 所属工作区 */
  workspace: StudioWorkspace
  /** tab 标题（动态：项目名/工作区名） */
  title: string
  /** 实际路由（保留浏览器前进后退） */
  url: string
  /** 滚动位置缓存（切回时恢复） */
  scrollY: number
  /** 是否固定，最后一个 tab 不可关闭 */
  pinned?: boolean
}

interface OpenTabInput {
  workspace: StudioWorkspace
  url: string
  title: string
  /** 若提供且工作区不是 projects，会替换已有同 workspace tab；
   *  projects 工作区每个 id 独立 tab */
  uniqueKey?: string
}

interface StudioShellState {
  tabs: StudioTab[]
  activeId: string | null
  /** 侧栏折叠（独立持久化） */
  sidebarCollapsed: boolean
  /** 助理浮球可见性 */
  assistantVisible: boolean

  /** 打开/切换 tab——已开则 active，未开则新建 */
  openTab: (input: OpenTabInput) => void
  closeTab: (id: string) => void
  reorderTab: (fromIdx: number, toIdx: number) => void
  setActive: (id: string) => void
  updateTab: (id: string, patch: Partial<StudioTab>) => void
  setScrollY: (id: string, y: number) => void
  toggleSidebar: () => void
  setAssistantVisible: (v: boolean) => void

  /** 重置（如登出） */
  reset: () => void
}

/** 生成 tab id 的纯函数 */
function genTabId(workspace: StudioWorkspace, uniqueKey?: string): string {
  return uniqueKey ? `${workspace}:${uniqueKey}` : workspace
}

const initialState = {
  tabs: [
    {
      id: "home",
      workspace: "home" as StudioWorkspace,
      title: "首页",
      url: "/studio",
      scrollY: 0,
      pinned: true
    }
  ] as StudioTab[],
  activeId: "home" as string | null,
  sidebarCollapsed: true,
  assistantVisible: true
}

export const useStudioShell = create<StudioShellState>()(
  persist(
    (set, get) => ({
      ...initialState,

      openTab: ({ workspace, url, title, uniqueKey }) => {
        const id = genTabId(workspace, uniqueKey)
        const { tabs } = get()
        const existing = tabs.find((t) => t.id === id)

        if (existing) {
          // 已存在：切到该 tab，并同步最新 url（如查询参数变化）
          set({
            activeId: id,
            tabs: tabs.map((t) => (t.id === id ? { ...t, url, title } : t))
          })
          return
        }

        // 容量上限：先驱逐最早未 pin 的 tab
        let nextTabs = tabs
        if (tabs.length >= MAX_TABS) {
          const evictIdx = tabs.findIndex((t) => !t.pinned)
          if (evictIdx >= 0) {
            nextTabs = tabs.filter((_, idx) => idx !== evictIdx)
          } else {
            // 全是 pinned，拒绝开新（理论上 MVP 不会触发）
            set({ activeId: tabs[0]?.id ?? null })
            return
          }
        }

        const newTab: StudioTab = { id, workspace, title, url, scrollY: 0 }
        set({ tabs: [...nextTabs, newTab], activeId: id })
      },

      closeTab: (id) => {
        const { tabs, activeId } = get()
        const target = tabs.find((t) => t.id === id)
        if (!target || target.pinned) return

        const idx = tabs.findIndex((t) => t.id === id)
        const nextTabs = tabs.filter((t) => t.id !== id)

        let nextActive = activeId
        if (activeId === id) {
          // 关掉 active：切到左侧邻居，无则右侧，无则 null
          nextActive = nextTabs[idx - 1]?.id ?? nextTabs[idx]?.id ?? null
        }

        set({ tabs: nextTabs, activeId: nextActive })
      },

      reorderTab: (fromIdx, toIdx) => {
        const { tabs } = get()
        if (fromIdx < 0 || toIdx < 0 || fromIdx >= tabs.length || toIdx >= tabs.length) return
        const next = [...tabs]
        const [moved] = next.splice(fromIdx, 1)
        next.splice(toIdx, 0, moved)
        set({ tabs: next })
      },

      setActive: (id) => set({ activeId: id }),

      updateTab: (id, patch) =>
        set((state) => ({
          tabs: state.tabs.map((t) => (t.id === id ? { ...t, ...patch } : t))
        })),

      setScrollY: (id, y) =>
        set((state) => ({
          tabs: state.tabs.map((t) => (t.id === id ? { ...t, scrollY: y } : t))
        })),

      toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),

      setAssistantVisible: (v) => set({ assistantVisible: v }),

      reset: () => set(initialState)
    }),
    {
      name: "aaf-studio-shell-v2",
      // tabs 用 sessionStorage（关浏览器丢失），但 sidebarCollapsed/assistantVisible 持久化更友好
      // MVP 简化：整体 sessionStorage，符合"关浏览器丢失，刷新保留"的约定
      storage: createJSONStorage(() =>
        typeof window === "undefined"
          ? ({ getItem: () => null, setItem: () => {}, removeItem: () => {} } as unknown as Storage)
          : window.sessionStorage
      ),
      // 仅持久化 tabs 与 sidebarCollapsed；activeId 持久；assistantVisible 持久
      partialize: (state) => ({
        tabs: state.tabs,
        activeId: state.activeId,
        sidebarCollapsed: state.sidebarCollapsed,
        assistantVisible: state.assistantVisible
      })
    }
  )
)

export const STUDIO_MAX_TABS = MAX_TABS
