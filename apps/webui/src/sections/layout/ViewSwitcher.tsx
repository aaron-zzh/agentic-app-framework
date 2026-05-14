/**
 * ViewSwitcher——视图切换 Tab（list/kanban/form）
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"

import { cn } from "@/lib/utils/cn"

const views = [
  { key: "list", label: "列表" },
  { key: "kanban", label: "看板" }
] as const

/** 视图切换器 */
export function ViewSwitcher() {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const currentView = searchParams.get("view") ?? "list"

  return (
    <div className="flex items-center gap-1 border-b px-4">
      {views.map((v) => (
        <Link
          key={v.key}
          href={`${pathname}?view=${v.key}`}
          className={cn(
            "border-b-2 px-3 py-2 text-sm transition-colors",
            currentView === v.key
              ? "border-primary font-medium text-foreground"
              : "border-transparent text-muted-foreground hover:text-foreground"
          )}
        >
          {v.label}
        </Link>
      ))}
    </div>
  )
}
