"use client"

import { ChevronDown } from "lucide-react"
import { cn } from "@/lib/utils/index"
import { useStudioShell } from "./store"

/** 侧栏折叠按钮——悬浮在侧栏与内容区交界处，不受 overflow 裁剪 */
export function SidebarCollapseButton() {
  const { sidebarCollapsed, toggleSidebar } = useStudioShell()

  return (
    <button
      type="button"
      onClick={toggleSidebar}
      className="absolute top-16 z-20 flex size-5 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border border-foreground/[0.12] bg-background text-muted-foreground shadow-md hover:text-foreground transition-[left] duration-200"
      style={{ left: sidebarCollapsed ? 64 : 220 }}
      aria-label={sidebarCollapsed ? "展开侧边栏" : "收起侧边栏"}
    >
      <ChevronDown
        className={cn(
          "size-3.5 transition-transform",
          sidebarCollapsed ? "rotate-90" : "-rotate-90"
        )}
      />
    </button>
  )
}
