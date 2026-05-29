/**
 * Next.js Middleware——路由守卫
 *
 * - 未登录访问 workspace 路由 → 重定向到 /auth/login
 * - 已登录访问 auth 路由 → 重定向到 /dashboard
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
  "/admin"
]

/** 已登录后不应访问的路径前缀 */
const AUTH_PATHS = ["/auth/login", "/auth/register", "/auth/forgot-password"]

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl
  const token = request.cookies.get("aaf-token")?.value

  const isProtected = PROTECTED_PATHS.some((p) => pathname.startsWith(p))
  const isAuthPage = AUTH_PATHS.some((p) => pathname.startsWith(p))

  // 未登录访问受保护路由 → 跳转登录
  if (isProtected && !token) {
    const loginUrl = new URL("/auth/login", request.url)
    loginUrl.searchParams.set("redirect", pathname)
    return NextResponse.redirect(loginUrl)
  }

  // 已登录访问认证页 → 跳转仪表盘
  if (isAuthPage && token) {
    return NextResponse.redirect(new URL("/dashboard", request.url))
  }

  return NextResponse.next()
}

export const config = {
  matcher: [
    /*
     * 匹配所有路径，排除：
     * - api 路由
     * - 静态文件（_next/static, _next/image, favicon.ico 等）
     */
    "/((?!api|_next/static|_next/image|favicon.ico|sw.js|manifest.json).*)"
  ]
}
