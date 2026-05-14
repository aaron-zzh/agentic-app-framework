import { create } from "zustand"

/** 客户端 UI 状态（侧边栏/主题/命令面板等） */
interface UIState {
  sidebarOpen: boolean
  toggleSidebar: () => void
}

export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: true,
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen }))
}))
