/**
 * Toolbar——视图工具栏（搜索 + 新建按钮 + 视图切换）
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"

import { cn } from "@/lib/utils/cn"
import type { EntityDef } from "@/features/entity-engine/types"

const views = [
  { key: "list", label: "列表", icon: "☰" },
  { key: "kanban", label: "看板", icon: "▦" }
] as const

interface ToolbarProps {
  entity: EntityDef
}

/** 视图工具栏 */
export function Toolbar({ entity }: ToolbarProps) {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const currentView = searchParams.get("view") ?? "list"
  const canCreate = entity.access?.create !== false

  return (
    <div className="flex items-center justify-between border-b px-4 py-2">
      <div className="flex items-center gap-3">
        <h1 className="text-lg font-semibold">{entity.label}</h1>

        {/* 视图切换 */}
        <div className="flex items-center gap-0.5 rounded-md border p-0.5">
          {views.map((v) => (
            <Link
              key={v.key}
              href={`${pathname}?view=${v.key}`}
              title={v.label}
              className={cn(
                "rounded px-2 py-1 text-xs transition-colors",
                currentView === v.key
                  ? "bg-accent font-medium text-accent-foreground"
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              {v.icon}
            </Link>
          ))}
        </div>
      </div>

      <div className="flex items-center gap-2">
        {/* 搜索框 */}
        <input
          type="text"
          placeholder="搜索..."
          className="h-8 w-48 rounded-md border border-input bg-transparent px-2.5 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />

        {/* 新建按钮 */}
        {canCreate && (
          <Link
            href={`${pathname}/new`}
            className="inline-flex h-8 items-center gap-1 rounded-md bg-primary px-3 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            + 新建
          </Link>
        )}
      </div>
    </div>
  )
}
