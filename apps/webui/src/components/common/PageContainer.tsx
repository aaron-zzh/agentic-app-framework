/**
 * PageContainer——页面内容容器，控制最大宽度和内边距
 * @author AaronZZH & Kiro
 *
 * 支持 compactLayout 全局设置：紧凑模式有 maxWidth 限制，宽屏模式全宽。
 *
 * @example
 * ```tsx
 * <PageContainer>...</PageContainer>                    // 跟随全局设置
 * <PageContainer maxWidth="sm">...</PageContainer>      // 强制窄宽度
 * <PageContainer disablePadding maxWidth={false}>...</PageContainer>  // 全屏
 * ```
 */

"use client"

import { useUIStore } from "@/lib/store/ui-store"
import { cn } from "@/lib/utils/cn"

const maxWidthMap = {
  sm: "max-w-2xl",
  md: "max-w-4xl",
  lg: "max-w-6xl",
  xl: "max-w-7xl",
  full: "max-w-full"
} as const

type MaxWidth = keyof typeof maxWidthMap | false

interface PageContainerProps {
  children: React.ReactNode
  /** 最大宽度，false = 不限制。未指定时跟随全局 compactLayout 设置 */
  maxWidth?: MaxWidth
  /** 禁用内边距（列表/看板等全屏视图） */
  disablePadding?: boolean
  className?: string
}

export function PageContainer({
  children,
  maxWidth,
  disablePadding = false,
  className
}: PageContainerProps) {
  const compactLayout = useUIStore((s) => s.compactLayout)

  // 优先级：props 显式指定 > 全局设置
  const resolvedMaxWidth = maxWidth !== undefined ? maxWidth : compactLayout ? "lg" : false

  return (
    <div
      className={cn(
        "mx-auto w-full",
        !disablePadding && "p-[var(--layout-content-padding)]",
        resolvedMaxWidth && maxWidthMap[resolvedMaxWidth],
        className
      )}
    >
      {children}
    </div>
  )
}
