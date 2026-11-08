/**
 * Studio Topbar——驾驶舱顶栏
 *
 * 左：当前功能分区标题（动态，跟随 active tab）
 * 中：搜索/命令面板触发
 * 右：积分余额、通知、用户头像、主题切换
 *
 * 复用现有 UserAvatarPopover 与 Notifications 业务能力
 */

"use client"

import { ChevronLeft, Home, Search } from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { ThemeToggle } from "@/components/common/ThemeToggle"
import { UserAvatarPopover } from "@/components/common/UserAvatarPopover"
import { Button } from "@/components/ui/button"
import { NotificationDrawer } from "@/features/notifications"
import { cn } from "@/lib/utils/index"
import { useStudioHeaderStore } from "./header-store"
import { StudioMobileNav } from "./StudioMobileNav"

/** 左侧导航按钮：页面声明了 back 时显示返回；二级及更深页面在手机端显示首页入口（桌面端侧栏常驻） */
function TopbarNavButtons() {
  const pathname = usePathname()
  const router = useRouter()
  const back = useStudioHeaderStore((s) => s.back)
  // 一级页面（/studio、/studio/xxx）有侧栏/抽屉入口，不显示首页按钮
  const isTopLevel = pathname.split("/").filter(Boolean).length <= 2
  const backLabel = back?.label ?? "返回"

  const handleBack = () => {
    if (back?.href) {
      router.push(back.href)
    } else if (window.history.length > 1) {
      router.back()
    } else {
      router.push("/studio")
    }
  }

  return (
    <>
      {back && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={backLabel}
          title={backLabel}
          onClick={handleBack}
        >
          <ChevronLeft className="size-5" />
        </Button>
      )}
      {!isTopLevel && (
        <Button
          nativeButton={false}
          render={<Link href="/studio" />}
          variant="ghost"
          size="icon-sm"
          aria-label="首页"
          title="首页"
          className="md:hidden"
        >
          <Home />
        </Button>
      )}
    </>
  )
}

export function StudioTopbar() {
  return (
    <header
      className={cn(
        "flex h-14 shrink-0 items-center gap-3 border-foreground/6 border-b bg-background/60 backdrop-blur-md",
        "px-4"
      )}
    >
      {/* 左：小屏汉堡按钮，点击从左侧弹出导航抽屉（桌面端使用常驻侧栏） */}
      <StudioMobileNav />
      <TopbarNavButtons />

      {/* 搜索框（⌘K 命令面板触发）；品牌 logo 由侧栏 / 抽屉显示 */}
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
