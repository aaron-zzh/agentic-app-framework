/**
 * 顶栏右侧操作按钮组——主题切换 / 对话 / 通知 / 设置 / 头像
 * @author AaronZZH & Kiro
 */

"use client"

import { m } from "framer-motion"
import { MessageSquare, MoonStar, SunMedium } from "lucide-react"
import { useTheme } from "next-themes"
import { AnimateBorder, transitionTap, varHover, varTap } from "@/components/animate"
import { ThemeSettings } from "@/components/common/ThemeSettings"
import { UserAvatarPopover } from "@/components/common/UserAvatarPopover"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger
} from "@/components/ui/sheet"
import { useChatterStore } from "@/lib/store/chatter-store"
import { $url } from "@/lib/utils"
import { NotificationDrawer } from "./notifications"

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

/** 设置按钮（弹出主题设置面板） */
export function SettingsButton() {
  return (
    <Sheet>
      <SheetTrigger
        className="rounded-md p-1.5 text-muted-foreground hover:bg-accent"
        aria-label="主题设置"
        render={<button type="button" />}
      >
        <SettingsIcon />
      </SheetTrigger>
      <SheetContent side="right">
        <SheetHeader>
          <SheetTitle>设置</SheetTitle>
          <SheetDescription>自定义主题色、外观模式和布局</SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-6 py-4">
          <ThemeSettings />
        </div>
      </SheetContent>
    </Sheet>
  )
}

/** 齿轮图标（外圈缓慢旋转） */
function SettingsIcon() {
  return (
    <svg className="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        className="origin-center animate-[spin_8s_linear_infinite]"
        fill="currentColor"
        fillRule="evenodd"
        clipRule="evenodd"
        opacity="0.5"
        d="M14.279 2.152C13.909 2 13.439 2 12.5 2s-1.408 0-1.779.152a2.008 2.008 0 0 0-1.09 1.083c-.094.223-.13.484-.145.863a1.615 1.615 0 0 1-.796 1.353a1.64 1.64 0 0 1-1.579.008c-.338-.178-.583-.276-.825-.308a2.026 2.026 0 0 0-1.49.396c-.318.242-.553.646-1.022 1.453c-.47.807-.704 1.21-.757 1.605c-.07.526.074 1.058.4 1.479c.148.192.357.353.68.555c.477.297.783.803.783 1.361c0 .558-.306 1.064-.782 1.36c-.324.203-.533.364-.682.556a1.99 1.99 0 0 0-.399 1.479c.053.394.287.798.757 1.605c.47.807.704 1.21 1.022 1.453c.424.323.96.465 1.49.396c.242-.032.487-.13.825-.308a1.64 1.64 0 0 1 1.58.008c.486.28.774.795.795 1.353c.015.38.051.64.145.863c.204.49.596.88 1.09 1.083c.37.152.84.152 1.779.152s1.409 0 1.779-.152a2.008 2.008 0 0 0 1.09-1.083c.094-.223.13-.483.145-.863c.02-.558.309-1.074.796-1.353a1.64 1.64 0 0 1 1.579-.008c.338.178.583.276.825.308c.53.07 1.066-.073 1.49-.396c.318-.242.553-.646 1.022-1.453c.47-.807.704-1.21.757-1.605a1.99 1.99 0 0 0-.4-1.479c-.148-.192-.357-.353-.68-.555c-.477-.297-.783-.803-.783-1.361c0-.558.306-1.064.782-1.36c.324-.203.533-.364.682-.556a1.99 1.99 0 0 0 .399-1.479c-.053-.394-.287-.798-.757-1.605c-.47-.807-.704-1.21-1.022-1.453a2.026 2.026 0 0 0-1.49-.396c-.242.032-.487.13-.825.308a1.64 1.64 0 0 1-1.58-.008a1.615 1.615 0 0 1-.795-1.353c-.015-.38-.051-.64-.145-.863a2.007 2.007 0 0 0-1.09-1.083"
      />
      <circle cx="12.5" cy="12" r="3" fill="currentColor" />
    </svg>
  )
}

/** 用户头像菜单 */
export function UserAvatar({ src, displayName }: { src?: string; displayName?: string }) {
  return (
    <m.button
      type="button"
      whileTap={varTap(0.96)}
      whileHover={varHover(1.04)}
      transition={transitionTap()}
      className="inline-flex items-center justify-center border-none bg-transparent p-0"
      aria-label="用户菜单"
    >
      <AnimateBorder rounded="full" borderWidth={1.5} size={40} glowSize={60} duration={8}>
        <Avatar className="!size-[36px] after:hidden">
          <AvatarImage
            src={src || $url.cdn("/assets/avatar/avatar.png")}
            alt={displayName || "用户头像"}
          />
          <AvatarFallback>{displayName?.charAt(0).toUpperCase() || "U"}</AvatarFallback>
        </Avatar>
      </AnimateBorder>
    </m.button>
  )
}

/** 右侧操作按钮组合 */
export function HeaderActions() {
  return (
    <>
      <ThemeToggle />
      <ChatterToggle />
      <NotificationDrawer />
      <SettingsButton />
      <UserAvatarPopover />
    </>
  )
}
