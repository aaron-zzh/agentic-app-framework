/**
 * 顶栏收起把手：挂在顶栏下沿右半部分正中（3/4 处，避开首页正中的分区切换按钮组）的小标签，点击切换顶栏收起与展开。
 *
 * 收起时用主题色描边和光晕提示可展开，不加动画；展开时弱化为普通把手。
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown } from "lucide-react"
import { cn } from "@/lib/utils/index"

interface TopbarCollapseHandleProps {
  collapsed: boolean
  onToggle: () => void
}

export function TopbarCollapseHandle({ collapsed, onToggle }: TopbarCollapseHandleProps) {
  const label = collapsed ? "展开顶栏" : "收起顶栏"
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={label}
      aria-expanded={!collapsed}
      title={label}
      className={cn(
        "absolute top-full left-3/4 z-30 flex h-3.5 w-14 -translate-x-1/2 items-center justify-center",
        "rounded-b-lg border-[0.5px] border-t-0 outline-none backdrop-blur-md transition-all duration-200",
        "hover:h-4 focus-visible:ring-2 focus-visible:ring-primary",
        collapsed
          ? "border-primary/25 bg-primary/15 text-primary shadow-[0_4px_14px_-2px_color-mix(in_oklch,var(--color-primary)_60%,transparent)] hover:bg-primary/25"
          : "border-foreground/10 bg-background/80 text-muted-foreground shadow-sm hover:text-foreground"
      )}
    >
      <ChevronDown
        className={cn("size-3 transition-transform duration-200", !collapsed && "rotate-180")}
      />
    </button>
  )
}
