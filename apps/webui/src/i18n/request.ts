/**
 * next-intl 服务端请求配置
 * @author AaronZZH & Kiro
 *
 * 通过 cookie 读取用户语言偏好，加载对应消息文件。
 * 需在 next.config.js 中配置：
 *   const withNextIntl = require('next-intl/plugin')('./src/i18n/request.ts')
 *
 * 集成步骤（不修改现有 layout.tsx，在 providers/ 中包裹）：
 * 1. 安装依赖：pnpm add next-intl@^4.1.0
 * 2. 在 next.config.js 中添加 next-intl 插件
 * 3. 在 layout.tsx 中用 NextIntlClientProvider 包裹 children
 */

import { cookies } from "next/headers"
import { getRequestConfig } from "next-intl/server"

import { defaultLocale, LOCALE_COOKIE, type Locale, locales } from "./config"

export default getRequestConfig(async () => {
  const cookieStore = await cookies()
  const cookieLocale = cookieStore.get(LOCALE_COOKIE)?.value

  // 校验 cookie 值是否为支持的语言
  const locale: Locale = locales.includes(cookieLocale as Locale)
    ? (cookieLocale as Locale)
    : defaultLocale

  return {
    locale,
    messages: (await import(`./messages/${locale}.json`)).default
  }
})
