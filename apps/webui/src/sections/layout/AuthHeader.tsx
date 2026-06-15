/**
 * 认证页面专用 Header
 * @author AaronZZH
 */

"use client"

import { Brand } from "@/components/brand/Brand"
import { ThemeToggle } from "./HeaderActions"

export function AuthHeader() {
  return (
    <header className="absolute top-0 z-20 flex h-16 w-full items-center justify-between px-6">
      <Brand size="sm" className="text-foreground" />
      <ThemeToggle />
    </header>
  )
}
