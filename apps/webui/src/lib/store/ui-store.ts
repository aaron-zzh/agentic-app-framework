import { create } from "zustand"
import { persist } from "zustand/middleware"

export type ThemeColor = "default" | "blue" | "purple" | "orange" | "green" | "rose" | "cyan"

export interface WorkspaceItem {
  id: string
  name: string
  logo?: string
}

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
  openRecordPanel: (id: string, mode?: "panel" | "drawer") => void
  closeRecordPanel: () => void
  /** 当前工作区 */
  currentWorkspace: WorkspaceItem | null
  workspaces: WorkspaceItem[]
  setCurrentWorkspace: (workspace: WorkspaceItem) => void
  setWorkspaces: (workspaces: WorkspaceItem[]) => void
}

export const useUIStore = create<UIState>()(
  persist(
    (set) => ({
      sidebarOpen: true,
      toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
      compactLayout: true,
      toggleCompactLayout: () => set((s) => ({ compactLayout: !s.compactLayout })),
      themeColor: "default",
      setThemeColor: (color) => set({ themeColor: color }),
      recordPanelId: null,
      recordPanelMode: "panel",
      openRecordPanel: (id, mode = "panel") => set({ recordPanelId: id, recordPanelMode: mode }),
      closeRecordPanel: () => set({ recordPanelId: null }),
      currentWorkspace: null,
      workspaces: [],
      setCurrentWorkspace: (workspace) => set({ currentWorkspace: workspace }),
      setWorkspaces: (workspaces) => set({ workspaces })
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
