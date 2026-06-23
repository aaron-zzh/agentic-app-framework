/**
 * Next.js Proxy——路由守卫 + 语言检测
 *
 * - 未登录访问 workspace 路由 → 重定向到 /login
 * - 首次访问且无语言 cookie → 从 Accept-Language 头检测并写入 cookie
 *
 * @author AaronZZH & Kiro
 */

import type { NextRequest } from "next/server"
import { NextResponse } from "next/server"

import { defaultLocale, LOCALE_COOKIE, type Locale, locales } from "./i18n/config"

/** 需要登录才能访问的路径前缀 */
const PROTECTED_PATHS = [
  "/studio",
  "/dashboard",
  "/module",
  "/settings",
  "/notifications",
  "/todos",
  "/trash",
  "/admin",
  "/aigc",
  "/examples/ocr",
  "/examples/image"
]

/**
 * 从 Accept-Language 头检测最优语言
 */
function detectLocaleFromHeader(acceptLanguage: string): Locale {
  const langs = acceptLanguage
    .split(",")
    .map((part) => {
      const [lang, q = "q=1"] = part.trim().split(";")
      return { lang: lang.trim(), quality: parseFloat(q.replace("q=", "")) || 1 }
    })
    .sort((a, b) => b.quality - a.quality)
    .map((item) => item.lang)

  for (const lang of langs) {
    const exact = locales.find((l) => l === lang)
    if (exact) return exact
    const prefix = locales.find((l) => lang.startsWith(l))
    if (prefix) return prefix
  }
  return defaultLocale
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl
  const token = request.cookies.get("aaf-token")?.value

  const isProtected = PROTECTED_PATHS.some((p) => pathname.startsWith(p))

  // 未登录访问受保护路由 → 跳转登录
  if (isProtected && !token) {
    const loginUrl = new URL("/login", request.url)
    loginUrl.searchParams.set("redirect", pathname)
    return NextResponse.redirect(loginUrl)
  }

  // 首次访问无语言 cookie → 从 Accept-Language 检测并写入
  const localeCookie = request.cookies.get(LOCALE_COOKIE)?.value
  if (!localeCookie || !locales.includes(localeCookie as Locale)) {
    const acceptLanguage = request.headers.get("accept-language") ?? ""
    const detectedLocale = detectLocaleFromHeader(acceptLanguage)
    const response = NextResponse.next()
    response.cookies.set(LOCALE_COOKIE, detectedLocale, {
      path: "/",
      maxAge: 60 * 60 * 24 * 365
    })
    return response
  }

  return NextResponse.next()
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|sw.js|manifest.json).*)"]
}
