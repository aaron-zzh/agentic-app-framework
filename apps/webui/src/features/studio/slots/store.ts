/**
 * Studio 动态面板槽 Store
 *
 * 设计理念：
 * - 整体布局右侧预留槽位，可同时挂载 ≤5 个面板
 * - 后端通过 SSE/WS 推送"打开面板"事件 → 前端自动加载
 * - 超过 5 个时自动替换最早一个；被替换的面板沉淀到"最近"列表，可重新打开
 * - 用户可手动关闭（关闭也进"最近"），可拖拽 resize 宽度
 *
 * 用法：
 *   import { useSlotStore } from '@/features/studio/slots'
 *   const open = useSlotStore(s => s.openSlot)
 *   open({ panelType: 'weather', payload: { city: '北京' } })
 *
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"

/** 内置面板类型 */
export type SlotPanelType =
  | "weather" // 天气查询（演示）
  | "recent-tasks" // 任务进度
  | "notifications" // 通知
  | "credits" // 积分余额
  | "project-summary" // 项目摘要

/** 单个槽位实例 */
export interface SlotInstance {
  /** 唯一 ID（panelType + 时间戳） */
  id: string
  panelType: SlotPanelType
  /** 面板特定参数（如天气城市/项目 id） */
  payload?: Record<string, unknown>
  /** 创建时间戳 */
  openedAt: number
  /** 最后激活时间（用于排序） */
  lastActiveAt: number
  /** 自定义宽度（px），undefined 用默认 */
  width?: number
}

/** 最近被替换/关闭的面板（用于重新打开） */
export interface RecentSlotItem {
  id: string
  panelType: SlotPanelType
  payload?: Record<string, unknown>
  closedAt: number
}

interface SlotState {
  /** 当前打开的面板（最多 5 个） */
  active: SlotInstance[]
  /** 最近关闭/替换的面板（最多保留 20 个） */
  recent: RecentSlotItem[]
  /** Dock 是否折叠 */
  collapsed: boolean
}

interface SlotActions {
  /** 打开一个面板。同 panelType + payload 不重复，仅刷新 lastActiveAt */
  openSlot: (input: { panelType: SlotPanelType; payload?: Record<string, unknown> }) => void
  /** 手动关闭面板 */
  closeSlot: (id: string) => void
  /** 从最近列表重新打开 */
  reopenRecent: (id: string) => void
  /** 清空最近列表 */
  clearRecent: () => void
  /** 调整面板宽度 */
  resizeSlot: (id: string, width: number) => void
  /** 切换 Dock 折叠 */
  toggleCollapsed: () => void
}

const MAX_ACTIVE = 5
const MAX_RECENT = 20

/** 判断两个 payload 是否等价（浅比较） */
function payloadEquals(a?: Record<string, unknown>, b?: Record<string, unknown>): boolean {
  if (a === b) return true
  if (!a || !b) return false
  const ak = Object.keys(a)
  const bk = Object.keys(b)
  if (ak.length !== bk.length) return false
  return ak.every((k) => a[k] === b[k])
}

export const useSlotStore = create<SlotState & SlotActions>()(
  persist(
    (set, get) => ({
      active: [],
      recent: [],
      collapsed: false,

      openSlot: ({ panelType, payload }) => {
        const now = Date.now()
        const { active, recent } = get()

        // 已存在同 panelType + payload 的面板 → 仅刷新激活时间
        const existing = active.find(
          (s) => s.panelType === panelType && payloadEquals(s.payload, payload)
        )
        if (existing) {
          set({
            active: active.map((s) => (s.id === existing.id ? { ...s, lastActiveAt: now } : s))
          })
          return
        }

        const newSlot: SlotInstance = {
          id: `${panelType}-${now}`,
          panelType,
          payload,
          openedAt: now,
          lastActiveAt: now
        }

        // 已满 → 移除最早激活的，沉淀到 recent
        if (active.length >= MAX_ACTIVE) {
          const oldest = [...active].sort((a, b) => a.lastActiveAt - b.lastActiveAt)[0]
          if (oldest) {
            const moved: RecentSlotItem = {
              id: oldest.id,
              panelType: oldest.panelType,
              payload: oldest.payload,
              closedAt: now
            }
            set({
              active: [...active.filter((s) => s.id !== oldest.id), newSlot],
              recent: [moved, ...recent].slice(0, MAX_RECENT)
            })
            return
          }
        }

        set({ active: [...active, newSlot] })
      },

      closeSlot: (id) => {
        const { active, recent } = get()
        const closing = active.find((s) => s.id === id)
        if (!closing) return
        const moved: RecentSlotItem = {
          id: closing.id,
          panelType: closing.panelType,
          payload: closing.payload,
          closedAt: Date.now()
        }
        set({
          active: active.filter((s) => s.id !== id),
          recent: [moved, ...recent].slice(0, MAX_RECENT)
        })
      },

      reopenRecent: (id) => {
        const { recent } = get()
        const item = recent.find((r) => r.id === id)
        if (!item) return
        // 从 recent 移除并打开
        set({ recent: recent.filter((r) => r.id !== id) })
        get().openSlot({ panelType: item.panelType, payload: item.payload })
      },

      clearRecent: () => set({ recent: [] }),

      resizeSlot: (id, width) => {
        const { active } = get()
        set({
          active: active.map((s) => (s.id === id ? { ...s, width } : s))
        })
      },

      toggleCollapsed: () => set((s) => ({ collapsed: !s.collapsed }))
    }),
    {
      name: "aaf-studio-slots",
      // 仅持久化 recent + collapsed，active 关页面后清空（演示导向）
      partialize: (state) => ({ recent: state.recent, collapsed: state.collapsed })
    }
  )
)
