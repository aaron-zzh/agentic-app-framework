/**
 * useResponsive——响应式断点检测 hook
 * @author AaronZZH & Kiro
 *
 * 断点规则：
 * - ≥1280px：桌面（desktop）
 * - 768-1279px：平板（tablet）
 * - <768px：手机（mobile）
 *
 * @example
 * ```ts
 * const { isMobile, isTablet, isDesktop } = useResponsive()
 * ```
 */

"use client"

import { useEffect, useState } from "react"

/** 断点常量（与 Tailwind md/xl 对齐） */
const BREAKPOINTS = {
  mobile: 768,
  desktop: 1280
} as const

interface ResponsiveState {
  /** <768px */
  isMobile: boolean
  /** 768-1279px */
  isTablet: boolean
  /** ≥1280px */
  isDesktop: boolean
}

function getState(width: number): ResponsiveState {
  return {
    isMobile: width < BREAKPOINTS.mobile,
    isTablet: width >= BREAKPOINTS.mobile && width < BREAKPOINTS.desktop,
    isDesktop: width >= BREAKPOINTS.desktop
  }
}

/** 响应式断点 hook */
export function useResponsive(): ResponsiveState {
  const [state, setState] = useState<ResponsiveState>(() =>
    typeof window === "undefined"
      ? { isMobile: false, isTablet: false, isDesktop: true }
      : getState(window.innerWidth)
  )

  useEffect(() => {
    const mqlMobile = window.matchMedia(`(max-width: ${BREAKPOINTS.mobile - 1}px)`)
    const mqlDesktop = window.matchMedia(`(min-width: ${BREAKPOINTS.desktop}px)`)

    function update() {
      setState(getState(window.innerWidth))
    }

    mqlMobile.addEventListener("change", update)
    mqlDesktop.addEventListener("change", update)
    return () => {
      mqlMobile.removeEventListener("change", update)
      mqlDesktop.removeEventListener("change", update)
    }
  }, [])

  return state
}
