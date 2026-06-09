/**
 * Chatter 全局状态 store
 * 本地优先（Zustand persist → localStorage），远程兜底（/api/context/chatter-config）
 *
 * 策略：
 * 1. 读取：先读 localStorage，命中直接用；未命中则请求后端，写入 localStorage
 * 2. 写入：立即写 localStorage，异步同步到后端（fire-and-forget，失败不阻塞）
 *
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"
import type { ChatterLayout, ChatterPreset } from "@/features/chatter/types"
import { request } from "@/lib/api/rest/entity/crud"

export interface ChatterPageConfig {
  preset: ChatterPreset
  agentRole?: string
  open: boolean
  /** 页面声明的布局模式，未声明时默认 dialog（浮动） */
  layout?: ChatterLayout
}

interface ChatterStore {
  /** 当前页面 ID */
  currentPageId: string | null
  /** 按 pageId 存储的配置（本地缓存） */
  configs: Record<string, ChatterPageConfig>
  /** 全局 open 状态（dialog 模式） */
  open: boolean
  /** 页面声明的布局覆盖（panel/dialog），null 表示使用默认 dialog */ 
  layoutOverride: ChatterLayout | null

  setCurrentPage: (pageId: string) => void
  setOpen: (open: boolean) => void
  setConfig: (pageId: string, config: Partial<ChatterPageConfig>) => void
  getConfig: (pageId: string) => ChatterPageConfig
  setLayoutOverride: (layout: ChatterLayout | null) => void
}

const DEFAULT_CONFIG: ChatterPageConfig = {
  preset: "ai",
  open: false
}

export const useChatterStore = create<ChatterStore>()(
  persist(
    (set, get) => ({
      currentPageId: null,
      configs: {},
      open: false,
      layoutOverride: null,

      setCurrentPage: (pageId) => set({ currentPageId: pageId }),

      setOpen: (open) => {
        set({ open })
        // 同步到当前页面配置
        const { currentPageId } = get()
        if (currentPageId) {
          get().setConfig(currentPageId, { open })
        }
      },

      setConfig: (pageId, config) => {
        set((state) => ({
          configs: {
            ...state.configs,
            [pageId]: { ...DEFAULT_CONFIG, ...state.configs[pageId], ...config }
          }
        }))
        // 异步同步到后端（fire-and-forget）
        syncToRemote(pageId, { ...DEFAULT_CONFIG, ...get().configs[pageId], ...config })
      },

      getConfig: (pageId) => {
        return get().configs[pageId] ?? DEFAULT_CONFIG
      },

      setLayoutOverride: (layout) => set({ layoutOverride: layout })
    }),
    {
      name: "aaf-chatter-config",
      // 只持久化 configs，不持久化 open（每次打开页面默认关闭）
      partialize: (state) => ({ configs: state.configs })
    }
  )
)

/** 异步同步配置到后端（失败静默，不影响本地使用） */
async function syncToRemote(pageId: string, config: ChatterPageConfig): Promise<void> {
  try {
    await request("/context/chatter-config", {
      method: "PUT",
      body: JSON.stringify({ pageId, ...config })
    })
  } catch {
    // 静默失败，本地缓存已更新
  }
}

/** 从后端加载配置（本地无缓存时调用） */
export async function loadRemoteConfig(pageId: string): Promise<ChatterPageConfig | null> {
  try {
    return await request<ChatterPageConfig | null>(
      `/context/chatter-config?pageId=${encodeURIComponent(pageId)}`
    )
  } catch {
    return null
  }
}
