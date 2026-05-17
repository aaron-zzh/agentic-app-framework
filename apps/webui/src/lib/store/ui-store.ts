import { create } from "zustand"

export type ThemeColor = "default" | "blue" | "green"

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
  closeRecordPanel: () => set({ recordPanelId: null })
}))
