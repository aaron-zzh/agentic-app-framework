/**
 * SectionWrapper——Section 包装器，注入 style + animation + darkMode 支持
 * @author AaronZZH & Kiro
 *
 * 每个 Section 渲染时由 PageEngine 包裹此组件，统一处理：
 * - 背景色/内边距/最大宽度等样式
 * - Intersection Observer 滚动动效
 * - 深色模式 class 注入
 */

"use client"

import { cn } from "@/lib/utils/cn"

import { type AnimationType, useScrollAnimation } from "./hooks/use-scroll-animation"
import type { SectionStyle } from "./types"

/** 内边距预设映射 */
const paddingMap: Record<string, string> = {
  sm: "py-8 px-4",
  md: "py-12 px-6",
  lg: "py-16 px-6",
  xl: "py-24 px-6",
  none: ""
}

interface SectionWrapperProps {
  /** Section ID（用于锚点导航） */
  id?: string
  /** 样式配置 */
  style?: SectionStyle
  /** 是否强制深色模式 */
  darkMode?: boolean
  children: React.ReactNode
}

/** Section 包装器——统一注入样式、动效、深色模式 */
export function SectionWrapper({ id, style, darkMode, children }: SectionWrapperProps) {
  const animation = (style?.animation ?? "none") as AnimationType
  const ref = useScrollAnimation<HTMLElement>(animation)

  const paddingClass = style?.padding ? (paddingMap[style.padding] ?? "") : "py-16 px-6"

  return (
    <section
      ref={ref}
      id={id}
      className={cn(
        paddingClass,
        style?.fullWidth ? "w-full" : "mx-auto w-full max-w-[var(--layout-marketing-max-width)]",
        darkMode && "dark",
        style?.className
      )}
      style={{
        backgroundColor: style?.backgroundColor,
        maxWidth: style?.fullWidth ? undefined : style?.maxWidth
      }}
    >
      {children}
    </section>
  )
}
