import { create } from "zustand"

export type ThemeColor = "default" | "blue" | "green"

/** 客户端 UI 状态（侧边栏/主题/布局模式等） */
interface UIState {
  sidebarOpen: boolean
  toggleSidebar: () => void
  /** 紧凑布局（内容区有 maxWidth 限制），false = 全宽 */
  compactLayout: boolean
  toggleCompactLayout: () => void
  /** 主题色 */
  themeColor: ThemeColor
  setThemeColor: (color: ThemeColor) => void
}

export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: true,
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
  compactLayout: true,
  toggleCompactLayout: () => set((s) => ({ compactLayout: !s.compactLayout })),
  themeColor: "default",
  setThemeColor: (color) => set({ themeColor: color })
}))
