/**
 * Studio 顶栏页面自定义状态
 *
 * 页面通过 useStudioHeader 声明自己的返回目标，StudioTopbar 读取并渲染。
 * 仅存放界面状态，不存服务端数据。
 */

import { useEffect } from "react"
import { create } from "zustand"

export interface StudioHeaderBack {
  /** 返回目标；缺省时使用 router.back()，无历史记录则回 /studio */
  href?: string
  /** 无障碍名称与悬浮提示，缺省为"返回" */
  label?: string
}

interface StudioHeaderState {
  back: StudioHeaderBack | null
  setBack: (back: StudioHeaderBack | null) => void
}

export const useStudioHeaderStore = create<StudioHeaderState>()((set) => ({
  back: null,
  setBack: (back) => set({ back })
}))

interface UseStudioHeaderOptions {
  /** 在顶栏左侧显示返回按钮 */
  back?: StudioHeaderBack
}

/** 页面声明顶栏自定义内容；页面卸载时自动清除 */
export function useStudioHeader({ back }: UseStudioHeaderOptions) {
  const setBack = useStudioHeaderStore((s) => s.setBack)
  const href = back?.href
  const label = back?.label
  const hasBack = back !== undefined

  useEffect(() => {
    setBack(hasBack ? { href, label } : null)
    return () => setBack(null)
  }, [hasBack, href, label, setBack])
}
