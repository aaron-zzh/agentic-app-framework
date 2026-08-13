import { create } from "zustand"
import { persist } from "zustand/middleware"

export type ThemeColor = "default" | "blue" | "purple" | "orange" | "green" | "rose" | "cyan"

interface UIState {
  sidebarOpen: boolean
  toggleSidebar: () => void
  compactLayout: boolean
  toggleCompactLayout: () => void
  themeColor: ThemeColor
  setThemeColor: (color: ThemeColor) => void
  /** 当前在侧边面板/抽屉中打开的记录 ID，null = 关闭 */
  recordPanelId: string | null
  recordPanelMode: "panel" | "drawer"
  /** 面板详情所属查询窗口的临时标识，不保存服务端记录数据 */
  recordPanelQueryToken?: string
  openRecordPanel: (id: string, mode?: "panel" | "drawer", queryToken?: string) => void
  closeRecordPanel: () => void
}

export const useUIStore = create<UIState>()(
  persist(
    (set) => ({
      sidebarOpen: true,
      toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
      compactLayout: true,
      toggleCompactLayout: () => set((state) => ({ compactLayout: !state.compactLayout })),
      themeColor: "default",
      setThemeColor: (color) => set({ themeColor: color }),
      recordPanelId: null,
      recordPanelMode: "panel",
      recordPanelQueryToken: undefined,
      openRecordPanel: (id, mode = "panel", queryToken) =>
        set({ recordPanelId: id, recordPanelMode: mode, recordPanelQueryToken: queryToken }),
      closeRecordPanel: () => set({ recordPanelId: null, recordPanelQueryToken: undefined })
    }),
    {
      name: "aaf-ui-preferences",
      // 只持久化用户偏好，不持久化临时 UI 状态
      partialize: (state) => ({
        themeColor: state.themeColor,
        compactLayout: state.compactLayout,
        sidebarOpen: state.sidebarOpen
      })
    }
  )
)
