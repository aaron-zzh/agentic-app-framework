/**
 * 认证页面专用 Header
 * @author Kiro
 */

"use client"

import { useTheme } from "next-themes"
import { Brand } from "@/components/brand/Brand"
import { Button } from "@/components/ui/button"

export function AuthHeader() {
  const { theme, setTheme } = useTheme()

  return (
    <header className="absolute top-0 z-20 flex h-16 w-full items-center justify-between px-6">
      <Brand size="sm" className="text-foreground" />
      <Button
        variant="ghost"
        size="icon"
        onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
        className="text-foreground hover:bg-accent"
        aria-label="切换主题"
      >
        <span className="dark:hidden">🌙</span>
        <span className="hidden dark:inline">☀️</span>
      </Button>
    </header>
  )
}
