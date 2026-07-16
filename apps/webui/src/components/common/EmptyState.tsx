/**
 * EmptyState——通用空数据占位组件
 * @author AaronZZH & Kiro
 *
 * 图标 + 标题 + 可选描述 + 可选操作按钮，用于列表/表格等场景的空数据态
 */

import { Inbox } from "lucide-react"
import type { ReactNode } from "react"
import { cn } from "@/lib/utils/cn"

interface EmptyStateProps {
  /** 标题文案，默认"暂无数据" */
  title?: string
  /** 补充说明文案 */
  description?: string
  /** 图标组件，默认 Inbox */
  icon?: ReactNode
  /** 操作按钮，如"创建"引导 */
  action?: ReactNode
  className?: string
}

export function EmptyState({
  title = "暂无数据",
  description,
  icon,
  action,
  className
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex min-h-[200px] flex-col items-center justify-center gap-2 px-6 py-10 text-center",
        className
      )}
    >
      <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
        {icon ?? <Inbox className="size-6" />}
      </div>
      <p className="font-medium text-muted-foreground text-sm">{title}</p>
      {description && <p className="text-muted-foreground/70 text-xs">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
