/**
 * 底部面板拖拽调整高度的逻辑
 * @author AaronZZH & Kiro
 */

import { useCallback, useRef, useState } from "react"

/** 提供面板高度与拖拽手柄事件，挂在拖拽区上即可调整 [data-panel] 容器高度 */
export function usePanelResize(defaultRatio = 0.6, minHeight = 200) {
  const [panelHeight, setPanelHeight] = useState<number>(() =>
    typeof window !== "undefined" ? Math.round(window.innerHeight * defaultRatio) : 400
  )
  const resizeStart = useRef<{ my: number; h: number } | null>(null)

  const handleResizeDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    resizeStart.current = {
      my: e.clientY,
      h: e.currentTarget.closest("[data-panel]")?.clientHeight ?? 400
    }
  }, [])

  const handleResizeMove = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if (!resizeStart.current) return
      const delta = resizeStart.current.my - e.clientY
      setPanelHeight(Math.max(minHeight, resizeStart.current.h + delta))
    },
    [minHeight]
  )

  const handleResizeUp = useCallback(() => {
    resizeStart.current = null
  }, [])

  return { panelHeight, handleResizeDown, handleResizeMove, handleResizeUp }
}
