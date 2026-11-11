/**
 * Studio 首页屏幕模式：宽屏铺满一屏，以及浏览器全屏（投屏）开关。
 *
 * - 铺满：视口宽度 ≥ 1280px 或处于全屏时，首页取消最大宽度，卡片行按剩余高度拉伸；
 *   高度不够时卡片不压缩，页面照常滚动。
 * - 全屏：对首页容器调用浏览器 Fullscreen API，只投出首页内容，按 Esc 或再次点击退出。
 *
 * @example
 * const containerRef = useRef<HTMLDivElement>(null)
 * const { fit, fullscreen, fullscreenSupported, toggleFullscreen } = useHomeScreenMode(containerRef)
 * @author AaronZZH & Kiro
 */

"use client"

import { type RefObject, useCallback, useSyncExternalStore } from "react"
import { toast } from "sonner"

/** 铺满一屏的最小视口宽度，与首页 xl 栅格断点一致 */
const WIDE_QUERY = "(min-width: 1280px)"

function subscribeWide(onChange: () => void): () => void {
  const query = window.matchMedia(WIDE_QUERY)
  query.addEventListener("change", onChange)
  return () => query.removeEventListener("change", onChange)
}

function subscribeFullscreen(onChange: () => void): () => void {
  document.addEventListener("fullscreenchange", onChange)
  return () => document.removeEventListener("fullscreenchange", onChange)
}

const subscribeNothing = () => () => undefined

interface HomeScreenMode {
  /** 是否铺满一屏 */
  fit: boolean
  /** 首页是否处于浏览器全屏 */
  fullscreen: boolean
  /** 浏览器是否允许全屏 */
  fullscreenSupported: boolean
  /** 进入或退出全屏 */
  toggleFullscreen: () => void
}

export function useHomeScreenMode(targetRef: RefObject<HTMLElement | null>): HomeScreenMode {
  const wide = useSyncExternalStore(
    subscribeWide,
    () => window.matchMedia(WIDE_QUERY).matches,
    () => false
  )
  const fullscreen = useSyncExternalStore(
    subscribeFullscreen,
    () => document.fullscreenElement !== null && document.fullscreenElement === targetRef.current,
    () => false
  )
  const fullscreenSupported = useSyncExternalStore(
    subscribeNothing,
    () => document.fullscreenEnabled,
    () => false
  )

  const toggleFullscreen = useCallback(() => {
    if (document.fullscreenElement) {
      void document.exitFullscreen()
      return
    }
    targetRef.current?.requestFullscreen().catch(() => {
      toast.error("浏览器拒绝了全屏请求")
    })
  }, [targetRef])

  return { fit: wide || fullscreen, fullscreen, fullscreenSupported, toggleFullscreen }
}
