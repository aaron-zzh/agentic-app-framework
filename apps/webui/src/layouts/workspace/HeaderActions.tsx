/**
 * 顶栏右侧操作按钮组——主题切换 / 对话 / 通知 / 设置 / 头像
 * @author AaronZZH & Kiro
 */

"use client"

import { MessageSquare, Users } from "lucide-react"
import { ThemeToggle } from "@/components/common/ThemeToggle"
import { UserAvatarPopover } from "@/components/common/UserAvatarPopover"
import { NotificationDrawer } from "@/features/notifications"
import { useChatterStore } from "@/lib/store/chatter-store"
import { ContactsPanel } from "./ContactsPanel"
import { SettingsButton } from "./SettingsButton"

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
