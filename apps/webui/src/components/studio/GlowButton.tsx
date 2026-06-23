/**
 * GlowButton——发光按钮（核心 CTA）
 *
 * 风格层基础组件。复用 ui/Button 的 base-ui 行为，覆盖渐变描边 + 悬浮发光样式。
 * 不重新实现 button 逻辑（避免并行抽象——硬规则）。
 *
 * @example
 *   <GlowButton>开始创作</GlowButton>
 *   <GlowButton tone="violet" size="lg">→</GlowButton>
 */

import { Button as ButtonPrimitive } from "@base-ui/react/button"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils/index"

const glowButtonVariants = cva(
  cn(
    "group/glow-button relative inline-flex shrink-0 select-none items-center justify-center gap-1.5",
    "whitespace-nowrap rounded-lg font-medium text-sm",
    "border border-transparent bg-clip-padding outline-none",
    "transition-all duration-200",
    "focus-visible:ring-3 focus-visible:ring-ring/50",
    "disabled:pointer-events-none disabled:opacity-50",
    "[&_svg:not([class*='size-'])]:size-4 [&_svg]:pointer-events-none [&_svg]:shrink-0"
  ),
  {
    variants: {
      tone: {
        // 主调：跟随系统 primary
        primary:
          "bg-primary text-primary-foreground hover:shadow-[0_0_24px_-4px_var(--color-primary)] active:translate-y-px",
        // 紫色渐变：蓝紫强调（首屏 CTA 用）
        violet: cn(
          "text-white",
          "bg-gradient-to-br from-[oklch(0.55_0.25_270)] to-[oklch(0.55_0.25_310)]",
          "shadow-[0_0_0_1px_rgb(255_255_255_/_0.08)_inset,0_4px_16px_-4px_oklch(0.55_0.25_290_/_0.4)]",
          "hover:shadow-[0_0_0_1px_rgb(255_255_255_/_0.12)_inset,0_8px_28px_-4px_oklch(0.55_0.25_290_/_0.6)]",
          "hover:brightness-110 active:translate-y-px"
        ),
        // 描边幽灵：边框 + hover 填光
        ghost:
          "border-foreground/15 bg-transparent text-foreground hover:border-foreground/30 hover:bg-foreground/[0.04] active:translate-y-px",
        // 翠绿
        emerald: cn(
          "text-white",
          "bg-gradient-to-br from-[oklch(0.55_0.18_150)] to-[oklch(0.50_0.18_170)]",
          "shadow-[0_0_0_1px_rgb(255_255_255_/_0.08)_inset,0_4px_16px_-4px_oklch(0.55_0.18_155_/_0.4)]",
          "hover:brightness-110 active:translate-y-px"
        ),
        // 玫红
        rose: cn(
          "text-white",
          "bg-gradient-to-br from-[oklch(0.60_0.22_10)] to-[oklch(0.55_0.22_350)]",
          "shadow-[0_0_0_1px_rgb(255_255_255_/_0.08)_inset,0_4px_16px_-4px_oklch(0.58_0.22_5_/_0.4)]",
          "hover:brightness-110 active:translate-y-px"
        )
      },
      size: {
        sm: "h-8 px-3 text-[0.8rem]",
        default: "h-9 px-4",
        lg: "h-11 px-6 text-base"
      }
    },
    defaultVariants: {
      tone: "primary",
      size: "default"
    }
  }
)

export interface GlowButtonProps
  extends ButtonPrimitive.Props,
    VariantProps<typeof glowButtonVariants> {}

export function GlowButton({ className, tone, size, ...props }: GlowButtonProps) {
  return (
    <ButtonPrimitive
      data-slot="glow-button"
      className={cn(glowButtonVariants({ tone, size }), className)}
      {...props}
    />
  )
}

export { glowButtonVariants }
