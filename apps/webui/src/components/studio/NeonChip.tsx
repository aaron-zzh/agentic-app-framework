/**
 * NeonChip——霓虹标签
 *
 * 用于显示技能、状态、类型、模型名等小颗粒标识。
 * 暗色背景叠加细描边 + 微发光，亮色自动适配。
 *
 * @example
 *   <NeonChip>文生图</NeonChip>
 *   <NeonChip tone="violet" leading={<Sparkles />}>智能体</NeonChip>
 *   <NeonChip tone="emerald" dot>在线</NeonChip>
 */

import type * as React from "react"
import { cn } from "@/lib/utils/index"

type NeonChipTone = "neutral" | "violet" | "cyan" | "emerald" | "amber" | "rose"

interface NeonChipProps extends Omit<React.ComponentProps<"span">, "color"> {
  tone?: NeonChipTone
  /** 前置图标（如 lucide icon） */
  leading?: React.ReactNode
  /** 显示左侧圆点（在线/活跃指示） */
  dot?: boolean
  /** 加大尺寸 */
  size?: "sm" | "default"
}

const TONE_MAP: Record<NeonChipTone, string> = {
  neutral: "border-foreground/15 bg-foreground/[0.04] text-foreground/80",
  violet: "border-violet-400/30 bg-violet-400/[0.08] text-violet-300 dark:text-violet-300",
  cyan: "border-cyan-400/30 bg-cyan-400/[0.08] text-cyan-300 dark:text-cyan-300",
  emerald: "border-emerald-400/30 bg-emerald-400/[0.08] text-emerald-300 dark:text-emerald-300",
  amber: "border-amber-400/30 bg-amber-400/[0.08] text-amber-300 dark:text-amber-300",
  rose: "border-rose-400/30 bg-rose-400/[0.08] text-rose-300 dark:text-rose-300"
}

const DOT_TONE_MAP: Record<NeonChipTone, string> = {
  neutral: "bg-foreground/40",
  violet: "bg-violet-400 shadow-[0_0_6px_oklch(0.7_0.2_290)]",
  cyan: "bg-cyan-400 shadow-[0_0_6px_oklch(0.7_0.18_200)]",
  emerald: "bg-emerald-400 shadow-[0_0_6px_oklch(0.7_0.18_155)]",
  amber: "bg-amber-400 shadow-[0_0_6px_oklch(0.78_0.18_75)]",
  rose: "bg-rose-400 shadow-[0_0_6px_oklch(0.7_0.2_15)]"
}

export function NeonChip({
  className,
  tone = "neutral",
  leading,
  dot = false,
  size = "default",
  children,
  ...props
}: NeonChipProps) {
  return (
    <span
      data-slot="neon-chip"
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border bg-clip-padding font-medium",
        size === "sm" ? "px-2 py-0.5 text-[0.7rem]" : "px-2.5 py-0.5 text-xs",
        TONE_MAP[tone],
        className
      )}
      {...props}
    >
      {dot && <span className={cn("size-1.5 shrink-0 rounded-full", DOT_TONE_MAP[tone])} />}
      {leading && (
        <span className="-ml-0.5 flex size-3.5 items-center justify-center">{leading}</span>
      )}
      {children}
    </span>
  )
}
