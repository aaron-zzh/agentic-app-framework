/**
 * CountBadge——Tab 数字徽标
 * @author AaronZZH & Kiro
 */

import { cn } from "@/lib/utils/cn"

interface CountBadgeProps {
  count: number
  variant?: "info" | "success"
}

export function CountBadge({ count, variant }: CountBadgeProps) {
  if (count === 0) return null
  return (
    <span
      className={cn(
        "inline-flex min-w-5 items-center justify-center rounded-full px-1.5 py-0.5 font-medium text-[10px]",
        variant === "info" && "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
        variant === "success" &&
          "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400",
        !variant && "bg-muted text-muted-foreground"
      )}
    >
      {count}
    </span>
  )
}
