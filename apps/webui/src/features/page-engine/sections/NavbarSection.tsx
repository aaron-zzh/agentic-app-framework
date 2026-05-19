/**
 * NavbarSection — 顶部导航栏（logo + 导航链接 + CTA + sticky + 移动端汉堡菜单）
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Menu, X } from "lucide-react"
import { motion, AnimatePresence } from "framer-motion"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"

import type { SectionComponentProps } from "../types"

interface NavLink {
  label: string
  href: string
}

interface NavbarProps {
  logo?: string
  logoText?: string
  links?: NavLink[]
  cta?: { label: string; href: string }
  sticky?: boolean
}

/** 顶部导航栏 Section */
export function NavbarSection({ data }: SectionComponentProps) {
  const { logo, logoText = "AAF", links = [], cta, sticky = true } = data as NavbarProps
  const [open, setOpen] = useState(false)

  return (
    <header
      className={cn(
        "z-50 w-full border-b bg-background/80 backdrop-blur-md",
        sticky && "sticky top-0"
      )}
    >
      <nav className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
        {/* Logo */}
        <a href="/" className="flex items-center gap-2 font-bold text-lg">
          {logo && <img src={logo} alt={logoText} className="h-8 w-8" />}
          <span>{logoText}</span>
        </a>

        {/* 桌面端导航链接 */}
        <ul className="hidden items-center gap-6 md:flex">
          {(links as NavLink[]).map((link) => (
            <li key={link.href}>
              <a
                href={link.href}
                className="text-muted-foreground text-sm transition-colors hover:text-foreground"
              >
                {link.label}
              </a>
            </li>
          ))}
        </ul>

        {/* 桌面端 CTA */}
        <div className="hidden md:block">
          {cta && (
            <Button asChild>
              <a href={cta.href}>{cta.label}</a>
            </Button>
          )}
        </div>

        {/* 移动端汉堡按钮 */}
        <button
          type="button"
          className="md:hidden"
          onClick={() => setOpen(!open)}
          aria-label={open ? "关闭菜单" : "打开菜单"}
        >
          {open ? <X className="size-5" /> : <Menu className="size-5" />}
        </button>
      </nav>

      {/* 移动端菜单 */}
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden border-t md:hidden"
          >
            <ul className="flex flex-col gap-2 px-6 py-4">
              {(links as NavLink[]).map((link) => (
                <li key={link.href}>
                  <a href={link.href} className="block py-2 text-sm">
                    {link.label}
                  </a>
                </li>
              ))}
              {cta && (
                <li className="pt-2">
                  <Button asChild className="w-full">
                    <a href={cta.href}>{cta.label}</a>
                  </Button>
                </li>
              )}
            </ul>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  )
}
