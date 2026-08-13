/**
 * 明暗主题快捷切换。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MoonStar, SunMedium } from "lucide-react"
import { useTheme } from "next-themes"

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
