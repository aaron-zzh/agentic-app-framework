/**
 * PageContainer——页面内容容器，控制最大宽度和内边距
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * // 默认：有边距，最大宽度 lg
 * <PageContainer>...</PageContainer>
 *
 * // 全屏（列表/看板）：无边距，无最大宽度限制
 * <PageContainer disablePadding maxWidth={false}>...</PageContainer>
 *
 * // 窄内容（表单/设置）：sm 宽度
 * <PageContainer maxWidth="sm">...</PageContainer>
 * ```
 */

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
  /** 最大宽度，false = 不限制（全屏） */
  maxWidth?: MaxWidth
  /** 禁用内边距（列表/看板等全屏视图） */
  disablePadding?: boolean
  className?: string
}

export function PageContainer({
  children,
  maxWidth = "lg",
  disablePadding = false,
  className
}: PageContainerProps) {
  return (
    <div
      className={cn(
        "mx-auto w-full",
        !disablePadding && "p-[var(--layout-content-padding)]",
        maxWidth && maxWidthMap[maxWidth],
        className
      )}
    >
      {children}
    </div>
  )
}
