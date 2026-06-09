/**
 * DevHeader——开发调试页顶栏
 * Brand + 导航菜单 + 主题切换 + 登录状态
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown } from "lucide-react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Brand } from "@/components/brand/Brand"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Separator } from "@/components/ui/separator"
import { paths } from "@/lib/constants/paths"
import { useScrollOffset } from "@/lib/hooks/use-scroll-offset"
import { useAuthStore } from "@/lib/store/auth-store"
import { cn } from "@/lib/utils/cn"
import { ThemeToggle } from "./HeaderActions"

const devPages = [
  { label: "工作区", href: "/dashboard" },
  { label: "全部", href: "/components" },
  { label: "基础", href: "/components/ui" },
  { label: "表单", href: "/components/form" },
  { label: "反馈", href: "/components/feedback" },
  { label: "编辑器", href: "/components/editor" },
  { label: "动画", href: "/components/animate" }
]

const threejsPages = [
  { label: "总览", href: "/examples/threejs" },
  { label: "Demo（Logo + Blob）", href: "/examples/threejs/demo" },
  { label: "Blob", href: "/examples/threejs/demo/blob" },
  { label: "Fiber", href: "/examples/threejs/fiber" },
  { label: "Meshline", href: "/examples/threejs/meshline" },
  { label: "Test", href: "/examples/threejs/test" },
  { label: "视频纹理", href: "/examples/threejs/video" },
  { label: "粒子对比", href: "/examples/threejs/particles" },
  { label: "GLTF 模型", href: "/examples/threejs/gltf" }
]

const examplePages = [
  { label: "ASR", href: "/examples/asr" },
  { label: "Assistant UI", href: "/examples/assistant-ui" },
  { label: "GraphQL", href: "/examples/graphql" },
  { label: "i18n", href: "/examples/i18n" },
  { label: "Image", href: "/examples/image" },
  { label: "Lottie", href: "/examples/lottie" },
  { label: "Next.js Features", href: "/examples/nextjs-features" },
  { label: "Omni Realtime", href: "/examples/omni-realtime" },
  { label: "PDF", href: "/examples/pdf" },
  { label: "Style Showcase", href: "/examples/style-showcase" },
  { label: "Tech Style", href: "/examples/tech-style" }
]

const zustandPages = [
  { label: "总览", href: "/examples/zustand" },
  { label: "Bear", href: "/examples/zustand/bear" },
  { label: "Clock", href: "/examples/zustand/clock" },
  { label: "Counter", href: "/examples/zustand/counter" }
]

function AuthButton() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (isAuthenticated) {
    return (
      <Link href={paths.workspace.root}>
        <Button variant="outline" size="sm">
          进入工作区
        </Button>
      </Link>
    )
  }
  return (
    <Link href={paths.auth.login}>
      <Button variant="outline" size="sm">
        登录
      </Button>
    </Link>
  )
}

export function DevHeader() {
  const pathname = usePathname()
  const isOffset = useScrollOffset()
  const isExamplesActive =
    pathname.startsWith("/examples") && !pathname.startsWith("/examples/threejs")

  return (
    <header
      className={cn(
        "sticky top-0 z-50 flex h-12 items-center transition-all duration-200",
        isOffset ? "bg-background/80 shadow-sm backdrop-blur-md" : "bg-background/95 backdrop-blur"
      )}
    >
      <div className="flex w-full items-center gap-4 px-4">
        {/* Brand */}
        <Brand size="sm" />
        <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-muted-foreground text-xs">
          dev
        </span>

        <Separator orientation="vertical" className="h-4" />

        {/* 导航 */}
        <nav className="flex items-center gap-1">
          {devPages.map((p) => {
            // 索引页精确匹配，子页面前缀匹配
            const active =
              p.href === "/components" ? pathname === "/components" : pathname.startsWith(p.href)
            return (
              <Link
                key={p.href}
                href={p.href}
                className={cn(
                  "px-2 py-1 text-sm transition-colors",
                  active
                    ? "font-medium text-foreground underline underline-offset-4"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                {p.label}
              </Link>
            )
          })}

          {/* Examples 下拉菜单 */}
          <DropdownMenu>
            <DropdownMenuTrigger
              className={cn(
                "flex items-center gap-1 px-2 py-1 text-sm transition-colors",
                isExamplesActive
                  ? "font-medium text-foreground underline underline-offset-4"
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              Examples
              <ChevronDown className="h-3 w-3" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="min-w-44">
              <DropdownMenuGroup>
                <DropdownMenuLabel>基础示例</DropdownMenuLabel>
                {examplePages.map((p) => (
                  <DropdownMenuItem key={p.href}>
                    <Link href={p.href} className="w-full cursor-pointer">
                      {p.label}
                    </Link>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuLabel>Zustand</DropdownMenuLabel>
                {zustandPages.map((p) => (
                  <DropdownMenuItem key={p.href}>
                    <Link href={p.href} className="w-full cursor-pointer">
                      {p.label}
                    </Link>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* Three.js 下拉菜单 */}
          <DropdownMenu>
            <DropdownMenuTrigger
              className={cn(
                "flex items-center gap-1 px-2 py-1 text-sm transition-colors",
                pathname.startsWith("/examples/threejs")
                  ? "font-medium text-foreground underline underline-offset-4"
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              Three.js
              <ChevronDown className="h-3 w-3" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              {threejsPages.map((p) => (
                <DropdownMenuItem key={p.href}>
                  <Link href={p.href} className="w-full cursor-pointer">
                    {p.label}
                  </Link>
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </nav>

        <div className="flex-1" />

        {/* 主题切换 */}
        <ThemeToggle />

        {/* 登录状态 */}
        <AuthButton />
      </div>
    </header>
  )
}
