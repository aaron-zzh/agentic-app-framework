/**
 * Next.js Proxy——路由守卫
 *
 * - 未登录访问 workspace 路由 → 重定向到 /login
 * - 判断依据：cookie 中的 aaf-token 存在性
 *
 * @author AaronZZH & Kiro
 */

import type { NextRequest } from "next/server"
import { NextResponse } from "next/server"

/** 需要登录才能访问的路径前缀 */
const PROTECTED_PATHS = [
  "/dashboard",
  "/module",
  "/settings",
  "/notifications",
  "/todos",
  "/trash",
  "/admin",
  "/aigc"
]

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl
  const token = request.cookies.get("aaf-token")?.value

  const isProtected = PROTECTED_PATHS.some((p) => pathname.startsWith(p))

  // 未登录访问受保护路由 → 跳转登录
  if (isProtected && !token) {
    const loginUrl = new URL("/login", request.url)
    console.log("未登录访问受保护路由 → 跳转登录")
    loginUrl.searchParams.set("redirect", pathname)
    return NextResponse.redirect(loginUrl)
  }

  return NextResponse.next()
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|sw.js|manifest.json).*)"]
}
