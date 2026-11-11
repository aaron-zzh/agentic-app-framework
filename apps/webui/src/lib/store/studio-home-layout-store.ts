/**
 * Studio 首页布局偏好：隐藏的面板、折叠的面板、底部创作区是否折叠、中央画布当前标签页。
 *
 * 只存在本地，纯客户端 UI 状态，不放服务端数据。读取本地存储时逐项过滤非法值。
 *
 * @example
 * const hiddenKeys = useStudioHomeLayoutStore((s) => s.hiddenKeys)
 * const setWidgetHidden = useStudioHomeLayoutStore((s) => s.setWidgetHidden)
 * setWidgetHidden("agent-runs", true)
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"

export const STUDIO_HOME_LAYOUT_STORAGE_KEY = "aaf-studio-home-layout"

/** 中央画布标签页：助理 / 项目与任务 / 工作流 */
export const STUDIO_HOME_CANVAS_TABS = ["assistant", "project", "workflow"] as const
export type StudioHomeCanvasTab = (typeof STUDIO_HOME_CANVAS_TABS)[number]

const DEFAULT_CANVAS_TAB: StudioHomeCanvasTab = "assistant"

/** 判断任意值是否为合法的画布标签页 */
export function isStudioHomeCanvasTab(value: unknown): value is StudioHomeCanvasTab {
  return typeof value === "string" && (STUDIO_HOME_CANVAS_TABS as readonly string[]).includes(value)
}

interface StudioHomeLayoutState {
  /** 用户隐藏的面板 key */
  hiddenKeys: string[]
  /** 用户折叠的面板 key */
  collapsedKeys: string[]
  /** 底部创作区是否折叠（折叠后只留最近生成条） */
  bottomCollapsed: boolean
  /** 中央画布当前标签页 */
  canvasTab: StudioHomeCanvasTab
  /** 设置某个面板是否隐藏 */
  setWidgetHidden: (key: string, hidden: boolean) => void
  /** 设置某个面板是否折叠 */
  setWidgetCollapsed: (key: string, collapsed: boolean) => void
  setBottomCollapsed: (collapsed: boolean) => void
  setCanvasTab: (tab: StudioHomeCanvasTab) => void
  /** 恢复默认布局：全部显示、全部展开；画布标签页保持不变 */
  resetLayout: () => void
}

type PersistedLayout = Pick<
  StudioHomeLayoutState,
  "hiddenKeys" | "collapsedKeys" | "bottomCollapsed" | "canvasTab"
>

/** 只保留字符串 key 并去重，防止本地存储被篡改或旧版本残留非法值 */
function sanitizeKeys(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return Array.from(new Set(value.filter((key): key is string => typeof key === "string")))
}

/** 在列表中加入或移除某个 key */
function toggleKey(keys: string[], key: string, present: boolean): string[] {
  const rest = keys.filter((item) => item !== key)
  return present ? [...rest, key] : rest
}

export const useStudioHomeLayoutStore = create<StudioHomeLayoutState>()(
  persist(
    (set) => ({
      hiddenKeys: [],
      collapsedKeys: [],
      bottomCollapsed: false,
      canvasTab: DEFAULT_CANVAS_TAB,
      setWidgetHidden: (key, hidden) =>
        set((state) => ({ hiddenKeys: toggleKey(state.hiddenKeys, key, hidden) })),
      setWidgetCollapsed: (key, collapsed) =>
        set((state) => ({ collapsedKeys: toggleKey(state.collapsedKeys, key, collapsed) })),
      setBottomCollapsed: (collapsed) => set({ bottomCollapsed: collapsed }),
      setCanvasTab: (tab) => set({ canvasTab: tab }),
      resetLayout: () => set({ hiddenKeys: [], collapsedKeys: [], bottomCollapsed: false })
    }),
    {
      name: STUDIO_HOME_LAYOUT_STORAGE_KEY,
      partialize: (state): PersistedLayout => ({
        hiddenKeys: state.hiddenKeys,
        collapsedKeys: state.collapsedKeys,
        bottomCollapsed: state.bottomCollapsed,
        canvasTab: state.canvasTab
      }),
      merge: (persisted, current) => {
        const stored = (persisted ?? {}) as Partial<Record<keyof PersistedLayout, unknown>>
        return {
          ...current,
          hiddenKeys: sanitizeKeys(stored.hiddenKeys),
          collapsedKeys: sanitizeKeys(stored.collapsedKeys),
          bottomCollapsed: stored.bottomCollapsed === true,
          canvasTab: isStudioHomeCanvasTab(stored.canvasTab) ? stored.canvasTab : DEFAULT_CANVAS_TAB
        }
      }
    }
  )
)
