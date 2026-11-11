/**
 * 画布下钻面包屑：如"助理 / 运营助理"，点击第一段返回总览。
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronLeft } from "lucide-react"

interface CanvasBreadcrumbProps {
  /** 总览名称，如"助理" */
  root: string
  /** 当前下钻对象名称 */
  current: string
  onBack: () => void
}

export function CanvasBreadcrumb({ root, current, onBack }: CanvasBreadcrumbProps) {
  return (
    <nav
      aria-label="画布位置"
      className="flex items-center gap-1 rounded-full border border-foreground/10 bg-background/50 py-1 pr-3 pl-1 text-xs backdrop-blur-md"
    >
      <button
        type="button"
        onClick={onBack}
        className="flex items-center gap-0.5 rounded-full px-2 py-0.5 text-muted-foreground outline-none transition-colors hover:bg-foreground/[0.06] hover:text-foreground focus-visible:ring-2 focus-visible:ring-primary"
      >
        <ChevronLeft className="size-3.5" />
        {root}
      </button>
      <span className="text-muted-foreground/60">/</span>
      <span className="max-w-48 truncate font-medium">{current}</span>
    </nav>
  )
}
