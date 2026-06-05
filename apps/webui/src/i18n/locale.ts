"use server"

import { cookies, headers } from "next/headers"

import { defaultLocale, LOCALE_COOKIE, type Locale, locales } from "./config"

// -------------------------------------------------------

/**
 * 解析 Accept-Language 字符串，返回优先级最高且受支持的语言
 */
function matchLocale(acceptLanguage: string): Locale | undefined {
  const langs = acceptLanguage
    .split(",")
    .map((part) => {
      const [lang, q = "q=1"] = part.trim().split(";")
      return { lang: lang.trim(), quality: parseFloat(q.replace("q=", "")) || 1 }
    })
    .sort((a, b) => b.quality - a.quality)
    .map((item) => item.lang)

  for (const lang of langs) {
    // 精确匹配
    const exact = locales.find((l) => l === lang)
    if (exact) return exact
    // 前缀匹配（如 zh-CN → zh）
    const prefix = locales.find((l) => lang.startsWith(l))
    if (prefix) return prefix
  }
  return undefined
}

/**
 * 检测用户语言偏好
 * 优先级：Cookie > Accept-Language 请求头 > 默认语言
 */
export async function detectLanguage(): Promise<Locale> {
  const cookieStore = await cookies()
  const headerStore = await headers()

  // 1. 从 Cookie 获取
  const cookieLang = cookieStore.get(LOCALE_COOKIE)?.value
  const fromCookie =
    cookieLang && locales.includes(cookieLang as Locale) ? (cookieLang as Locale) : undefined

  // 2. 从 Accept-Language 头获取
  const acceptLanguage = headerStore.get("accept-language") ?? ""
  const fromHeader = matchLocale(acceptLanguage)

  return fromCookie ?? fromHeader ?? defaultLocale
}

/**
 * 获取用户当前语言
 */
export async function getUserLocale(): Promise<Locale> {
  return detectLanguage()
}

/**
 * 设置用户语言偏好（写入 cookie）
 */
export async function setUserLocale(locale: Locale): Promise<void> {
  ;(await cookies()).set(LOCALE_COOKIE, locale, {
    path: "/",
    maxAge: 60 * 60 * 24 * 365
  })
}
