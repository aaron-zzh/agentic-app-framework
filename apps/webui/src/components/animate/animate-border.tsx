/**
 * AnimateBorder——旋转发光点边框动画（移植自 next-ts）
 * 需要外层 <MotionLazy> 包裹（m 组件依赖 LazyMotion）
 * @author AaronZZH & Kiro
 */

"use client"

import {
  m,
  useAnimationFrame,
  useMotionTemplate,
  useMotionValue,
  useTransform
} from "framer-motion"
import { useRef } from "react"
import { cn } from "@/lib/utils/cn"

interface AnimateBorderProps {
  children: React.ReactNode
  rounded?: "full" | "md" | "lg" | "xl"
  /** 边框厚度 px，默认 2 */
  borderWidth?: number
  /** 容器固定宽高 px（含 padding），不设则自适应内容 */
  size?: number
  /** 主发光点颜色 */
  primaryColor?: string
  /** 副发光点颜色（反向运动） */
  secondaryColor?: string
  /** 发光点尺寸 px，默认 60 */
  glowSize?: number
  /** 静态边框颜色 */
  outlineColor?: string
  /** 动画时长 s，默认 8 */
  duration?: number
  className?: string
}

const rcMap = { full: "rounded-full", md: "rounded-md", lg: "rounded-lg", xl: "rounded-xl" }

export function AnimateBorder({
  children,
  rounded = "full",
  borderWidth = 2,
  size,
  primaryColor = "#a855f7",
  secondaryColor = "#06b6d4",
  glowSize = 60,
  outlineColor = "rgba(168,85,247,0.12)",
  duration = 8,
  className
}: AnimateBorderProps) {
  const rc = rcMap[rounded]

  return (
    <div
      className={cn(
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden",
        rc,
        className
      )}
      style={{ padding: borderWidth, ...(size ? { width: size, height: size } : {}) }}
    >
      {/* 静态细圈 */}
      <span
        aria-hidden
        className="pointer-events-none absolute inset-0"
        style={{
          borderRadius: "inherit",
          padding: borderWidth,
          background: outlineColor,
          mask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
          WebkitMask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
          maskComposite: "exclude",
          WebkitMaskComposite: "xor"
        }}
      />

      {/* 主发光点 */}
      <MovingBorder
        duration={duration}
        size={glowSize}
        color={primaryColor}
        borderWidth={borderWidth}
      />

      {/* 副发光点（scale(-1,-1) 反向） */}
      <MovingBorder
        duration={duration}
        size={glowSize}
        color={secondaryColor}
        borderWidth={borderWidth}
        reverse
      />

      {/* 内容 */}
      <div className={cn("relative z-10 flex w-full items-center justify-center", rc)}>
        {children}
      </div>
    </div>
  )
}

// ─── MovingBorder ────────────────────────────────────────────────────────────

interface MovingBorderProps {
  duration: number
  size: number
  color: string
  borderWidth: number
  reverse?: boolean
}

function MovingBorder({ duration, size, color, borderWidth, reverse }: MovingBorderProps) {
  const svgRectRef = useRef<SVGRectElement>(null)
  const progress = useMotionValue(0)

  useAnimationFrame((time) => {
    const rect = svgRectRef.current
    if (!rect) return
    try {
      const pathLength = rect.getTotalLength()
      if (pathLength === 0) return
      const pixelsPerMs = pathLength / (duration * 1000)
      progress.set((time * pixelsPerMs) % pathLength)
    } catch {
      /* ignore */
    }
  })

  const x = useTransform(progress, (val) => {
    try {
      return svgRectRef.current?.getPointAtLength(val)?.x ?? 0
    } catch {
      return 0
    }
  })

  const y = useTransform(progress, (val) => {
    try {
      return svgRectRef.current?.getPointAtLength(val)?.y ?? 0
    } catch {
      return 0
    }
  })

  const transform = useMotionTemplate`translateX(${x}px) translateY(${y}px) translateX(-50%) translateY(-50%)`

  return (
    <span
      aria-hidden
      className="pointer-events-none absolute inset-0"
      style={{
        borderRadius: "inherit",
        padding: borderWidth,
        mask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
        WebkitMask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
        maskComposite: "exclude",
        WebkitMaskComposite: "xor",
        transform: reverse ? "scale(-1, -1)" : undefined
      }}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        preserveAspectRatio="none"
        width="100%"
        height="100%"
        style={{ position: "absolute" }}
        aria-hidden="true"
      >
        <rect ref={svgRectRef} fill="none" width="100%" height="100%" rx="30%" ry="30%" />
      </svg>

      <m.span
        className="absolute"
        style={{
          transform,
          width: size,
          height: size,
          filter: "blur(8px)",
          background: `radial-gradient(${color} 40%, transparent 80%)`
        }}
      />
    </span>
  )
}
