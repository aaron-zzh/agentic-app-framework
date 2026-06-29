/**
 * Studio Topbar——驾驶舱顶栏
 *
 * 左：当前 workspace 标题（动态，跟随 active tab）
 * 中：搜索/命令面板触发
 * 右：积分余额、通知、用户头像、主题切换
 *
 * 复用现有 UserAvatarPopover、NotificationsBell（在 sections/layout/notifications/）
 */

"use client"

import { Search } from "lucide-react"
import { Brand } from "@/components/brand/Brand"
import { UserAvatarPopover } from "@/components/common/UserAvatarPopover"
import { cn } from "@/lib/utils/index"
import { ThemeToggle } from "@/sections/layout/HeaderActions"
import { NotificationDrawer } from "@/sections/layout/notifications"

export function StudioTopbar() {
  return (
    <header
      className={cn(
        "flex h-14 shrink-0 items-center gap-3 border-foreground/6 border-b bg-background/60 backdrop-blur-md",
        "px-4"
      )}
    >
      {/* 左：仅在小屏显示品牌（侧栏隐藏时） */}
      <div className="md:hidden">
        <Brand collapsed size="sm" href="/" />
      </div>

      {/* 中：搜索框（⌘K 命令面板触发） */}
      <button
        type="button"
        onClick={() => {
          // 通过 keyboard event 模拟触发 ⌘K，与 useCommandPalette 中的监听器联动
          document.dispatchEvent(
            new KeyboardEvent("keydown", { key: "k", metaKey: true, bubbles: true })
          )
        }}
        className={cn(
          "ml-auto flex h-9 w-full max-w-md items-center gap-2 rounded-lg border border-foreground/8",
          "bg-foreground/2 px-3 text-muted-foreground text-sm",
          "hover:border-foreground/15 hover:bg-foreground/4"
        )}
      >
        <Search className="size-4" />
        <span className="flex-1 text-left">搜索...</span>
        <kbd className="hidden rounded bg-foreground/6 px-1.5 py-0.5 font-mono text-[10px] sm:inline">
          ⌘K
        </kbd>
      </button>

      {/* 右：主题切换 / 通知 / 头像 */}
      <div className="ml-auto flex items-center gap-2">
        <ThemeToggle />
        <NotificationDrawer notificationsUrl="/studio/notifications" />
        <UserAvatarPopover />
      </div>
    </header>
  )
}
