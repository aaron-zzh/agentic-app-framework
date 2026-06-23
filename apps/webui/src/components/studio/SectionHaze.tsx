/**
 * SectionHaze——背景光雾
 *
 * 用于页面顶部装饰，营造科技/沉浸氛围。
 * 性能策略：纯 CSS gradient + blur，无 canvas/three.js；
 * 移动端自动降低强度，亮/暗主题通过 CSS variable 切换。
 *
 * v0.2 (M4): 改用 --haze-* token，色值由 global.css 主题统一管理。
 *
 * @example
 *   <div className="relative">
 *     <SectionHaze />
 *     <div>内容...</div>
 *   </div>
 */

import { cn } from "@/lib/utils/index"

type SectionHazeVariant = "violet" | "cyan" | "amber" | "blend" | "soft"

interface SectionHazeProps {
  variant?: SectionHazeVariant
  /** 是否吸顶填满父容器 */
  fill?: boolean
  className?: string
}

const TOKEN_MAP: Record<SectionHazeVariant, string> = {
  violet: "var(--haze-violet)",
  cyan: "var(--haze-cyan)",
  amber: "var(--haze-amber)",
  blend: "var(--haze-blend)",
  soft: "var(--haze-soft)"
}

export function SectionHaze({ variant = "blend", fill = true, className }: SectionHazeProps) {
  return (
    <div
      aria-hidden="true"
      data-slot="section-haze"
      style={{ backgroundImage: TOKEN_MAP[variant] }}
      className={cn("pointer-events-none -z-10 select-none", fill && "absolute inset-0", className)}
    />
  )
}
