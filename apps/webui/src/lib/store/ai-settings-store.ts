/**
 * AI 辅助设置 Zustand store
 * 管理 AI 辅助功能的全局开关和灵敏度配置
 * @author AaronZZH & Kiro
 */
import { create } from "zustand"
import { persist } from "zustand/middleware"

/** AI 建议灵敏度：影响建议触发频率 */
export type AISensitivity = "low" | "medium" | "high"

interface AISettingsState {
  /** AI 辅助全局开关 */
  enabled: boolean
  /** 建议灵敏度 */
  sensitivity: AISensitivity
  /** 切换全局开关 */
  toggleEnabled: () => void
  /** 设置开关状态 */
  setEnabled: (enabled: boolean) => void
  /** 设置灵敏度 */
  setSensitivity: (sensitivity: AISensitivity) => void
}

export const useAISettingsStore = create<AISettingsState>()(
  persist(
    (set) => ({
      enabled: true,
      sensitivity: "medium",
      toggleEnabled: () => set((s) => ({ enabled: !s.enabled })),
      setEnabled: (enabled) => set({ enabled }),
      setSensitivity: (sensitivity) => set({ sensitivity }),
    }),
    { name: "aaf-ai-settings" }
  )
)
