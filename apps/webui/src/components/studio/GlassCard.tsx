/**
 * GlassCard——玻璃质感卡片基类
 *
 * 风格层基础组件（user-studio-mvp.md A6/B 设计语言）。
 * 在 ui/Card 之上叠加：内描边光晕 + 玻璃叠加 + 悬浮上浮微动效。
 * 暗色主题为主，亮色自动适配。
 *
 * @example
 *   <GlassCard><GlassCardBody>...</GlassCardBody></GlassCard>
 *   <GlassCard glow="accent" interactive>...</GlassCard>
 */

import type * as React from "react"
import { cn } from "@/lib/utils/index"

type GlassCardGlow = "none" | "accent" | "violet" | "cyan"

interface GlassCardProps extends React.ComponentProps<"div"> {
  /** 光晕颜色：none 关闭，accent 跟随主题，violet/cyan 固定色 */
  glow?: GlassCardGlow
  /** 是否启用悬浮交互（hover 上浮 + 描边增亮） */
  interactive?: boolean
}

const GLOW_MAP: Record<GlassCardGlow, string> = {
  none: "",
  accent:
    "before:bg-[radial-gradient(ellipse_at_top,_var(--color-primary)_0%,_transparent_60%)] before:opacity-[0.08]",
  violet:
    "before:bg-[radial-gradient(ellipse_at_top,_oklch(0.65_0.2_290)_0%,_transparent_60%)] before:opacity-[0.10]",
  cyan: "before:bg-[radial-gradient(ellipse_at_top,_oklch(0.7_0.15_200)_0%,_transparent_60%)] before:opacity-[0.10]"
}

export function GlassCard({
  className,
  glow = "accent",
  interactive = false,
  children,
  ...props
}: GlassCardProps) {
  return (
    <div
      data-slot="glass-card"
      className={cn(
        "relative isolate overflow-hidden rounded-2xl bg-card text-card-foreground",
        "[box-shadow:var(--glass-card-shadow)]",
        "dark:ring-1 dark:ring-foreground/[0.06]",
        // 顶部光晕层（伪元素）
        "before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:-z-10 before:h-32 before:content-['']",
        GLOW_MAP[glow],
        // 交互模式
        interactive && [
          "cursor-pointer transition-all duration-200",
          "hover:-translate-y-0.5",
          "hover:[--glass-card-shadow:0_6px_24px_-4px_rgb(0_0_0/0.16),0_2px_8px_0_rgb(0_0_0/0.10)]",
          "dark:hover:ring-foreground/[0.12]",
          "dark:hover:[--glass-card-shadow:inset_0_1px_0_0_var(--bg-glass-overlay-hover),inset_0_0_0_1px_var(--bg-glass-overlay),0_8px_24px_-8px_rgb(0_0_0_/_0.4)]"
        ],
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
}

export function GlassCardBody({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("p-5", className)} {...props} />
}

export function GlassCardHeader({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "flex items-start justify-between gap-3 border-foreground/[0.06] border-b px-5 py-4",
        className
      )}
      {...props}
    />
  )
}

export function GlassCardTitle({ className, ...props }: React.ComponentProps<"h3">) {
  return <h3 className={cn("font-medium text-base leading-snug", className)} {...props} />
}

export function GlassCardDescription({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-muted-foreground text-sm leading-5", className)} {...props} />
}
