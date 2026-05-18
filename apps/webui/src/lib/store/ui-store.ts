import { create } from "zustand"

export type ThemeColor = "default" | "blue" | "green"

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
  openRecordPanel: (id: string) => void
  closeRecordPanel: () => void
  /** 当前工作区 */
  currentWorkspace: WorkspaceItem | null
  workspaces: WorkspaceItem[]
  setCurrentWorkspace: (workspace: WorkspaceItem) => void
  setWorkspaces: (workspaces: WorkspaceItem[]) => void
}

export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: true,
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
  compactLayout: true,
  toggleCompactLayout: () => set((s) => ({ compactLayout: !s.compactLayout })),
  themeColor: "default",
  setThemeColor: (color) => set({ themeColor: color }),
  recordPanelId: null,
  openRecordPanel: (id) => set({ recordPanelId: id }),
  closeRecordPanel: () => set({ recordPanelId: null }),
  currentWorkspace: null,
  workspaces: [],
  setCurrentWorkspace: (workspace) => set({ currentWorkspace: workspace }),
  setWorkspaces: (workspaces) => set({ workspaces })
}))
