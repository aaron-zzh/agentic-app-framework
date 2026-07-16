/**
 * 顶栏右侧操作按钮组——主题切换 / 对话 / 通知 / 设置 / 头像
 * @author AaronZZH & Kiro
 */

"use client"

import { MessageSquare, MoonStar, SunMedium, Users } from "lucide-react"
import { useTheme } from "next-themes"
import { UserAvatarPopover } from "@/components/common/UserAvatarPopover"
import { useChatterStore } from "@/lib/store/chatter-store"
import { ContactsPanel } from "./ContactsPanel"
import { NotificationDrawer } from "./notifications"
import { SettingsButton } from "./SettingsButton"

/** 明暗主题快捷切换 */
export function ThemeToggle() {
  const { theme, setTheme } = useTheme()

  return (
    <button
      type="button"
      className="rounded-md p-1.5 text-muted-foreground hover:bg-accent"
      aria-label="切换主题"
      onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
    >
      <SunMedium className="size-5 dark:hidden" aria-hidden="true" />
      <MoonStar className="hidden size-5 dark:block" aria-hidden="true" />
    </button>
  )
}

/** Chatter 全局触发按钮 */
export function ChatterToggle() {
  const open = useChatterStore((s) => s.open)
  const setOpen = useChatterStore((s) => s.setOpen)

  return (
    <button
      type="button"
      className={`flex size-8 items-center justify-center rounded-md hover:bg-accent ${open ? "text-primary" : "text-muted-foreground hover:text-foreground"}`}
      aria-label="打开对话"
      onClick={() => setOpen(!open)}
    >
      <MessageSquare className="size-4" />
    </button>
  )
}

/** 右侧操作按钮组合 */
export function HeaderActions() {
  return (
    <>
      <ThemeToggle />
      <ContactsPanel>
        <button
          type="button"
          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
          aria-label="联系人"
        >
          <Users className="size-4" />
        </button>
      </ContactsPanel>
      <ChatterToggle />
      <NotificationDrawer />
      <SettingsButton />
      <UserAvatarPopover />
    </>
  )
}
