/**
 * Studio TabBar——多 tab 主区切换栏
 *
 * 行为：
 * - 显示当前打开的 tabs，active 高亮
 * - 点 tab 切换 + router.push
 * - 关闭按钮（pinned 不可关，最后一个 tab 不可关）
 * - 容量上限提示
 *
 * 详见 docs/design/apps/webui/user-studio-mvp.md A4
 */

"use client"

import { X } from "lucide-react"
import { useRouter } from "next/navigation"
import { useMemo } from "react"
import { cn } from "@/lib/utils/index"
import { getWorkspaceConfig } from "../nav-config"
import { useStudioShell } from "./store"

export function StudioTabBar() {
  const router = useRouter()
  const tabs = useStudioShell((s) => s.tabs)
  const activeId = useStudioShell((s) => s.activeId)
  const setActive = useStudioShell((s) => s.setActive)
  const closeTab = useStudioShell((s) => s.closeTab)

  // 隐藏 tab bar 当 tabs 为空（首屏未访问任何工作区时不显示）
  const visible = tabs.length > 0

  const tabsWithIcon = useMemo(
    () =>
      tabs.map((t) => {
        const cfg = getWorkspaceConfig(t.workspace)
        return { ...t, Icon: cfg.icon }
      }),
    [tabs]
  )

  if (!visible) return null

  return (
    <div
      data-slot="studio-tab-bar"
      className={cn(
        "flex h-10 shrink-0 items-stretch gap-0.5 overflow-x-auto border-foreground/[0.06] border-b bg-background/40 px-2 backdrop-blur"
      )}
    >
      {tabsWithIcon.map((tab) => {
        const isActive = tab.id === activeId
        const canClose = !tab.pinned && tabs.length > 1
        return (
          <div
            key={tab.id}
            data-active={isActive}
            className={cn(
              "group/tab relative flex h-full shrink-0 items-stretch",
              "border-transparent border-b-2"
            )}
          >
            <button
              type="button"
              onClick={() => {
                setActive(tab.id)
                router.push(tab.url)
              }}
              className={cn(
                "flex items-center gap-2 px-3 text-sm transition-colors",
                isActive
                  ? "text-foreground"
                  : "text-muted-foreground hover:bg-foreground/[0.04] hover:text-foreground"
              )}
            >
              <tab.Icon className="size-3.5" />
              <span className="max-w-[160px] truncate">{tab.title}</span>
            </button>

            {canClose && (
              <button
                type="button"
                aria-label={`关闭 ${tab.title}`}
                onClick={(e) => {
                  e.stopPropagation()
                  closeTab(tab.id)
                }}
                className={cn(
                  "mr-1 flex w-6 items-center justify-center rounded text-muted-foreground/60",
                  "opacity-0 group-hover/tab:opacity-100",
                  "hover:bg-foreground/[0.08] hover:text-foreground",
                  isActive && "opacity-100"
                )}
              >
                <X className="size-3" />
              </button>
            )}

            {isActive && (
              <span className="pointer-events-none absolute inset-x-0 bottom-0 h-0.5 bg-primary shadow-[0_0_8px_-1px_var(--color-primary)]" />
            )}
          </div>
        )
      })}
    </div>
  )
}
