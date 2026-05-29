/**
 * LanguageSwitcher——语言切换器
 * @author AaronZZH & Kiro
 *
 * 放置在 AppHeader 用户菜单区域，通过 DropdownMenu 切换语言。
 * 切换时设置 cookie 并刷新页面以加载新语言消息。
 */

"use client"

import { Globe } from "lucide-react"
import { useLocale, useTranslations } from "next-intl"

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { LOCALE_COOKIE, type Locale, locales } from "@/i18n/config"

/** 语言显示名称映射 */
const localeNames: Record<Locale, string> = {
  zh: "中文",
  en: "English"
}

export function LanguageSwitcher() {
  const currentLocale = useLocale()
  const t = useTranslations("layout")

  function switchLocale(locale: Locale) {
    if (locale === currentLocale) return
    // biome-ignore lint/suspicious/noDocumentCookie: 持久化语言偏好需要直接操作 cookie
    document.cookie = `${LOCALE_COOKIE}=${locale};path=/;max-age=${60 * 60 * 24 * 365}`
    // 刷新页面加载新语言
    window.location.reload()
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
        aria-label={t("switchLanguage")}
        render={<button type="button" />}
      >
        <Globe className="size-4" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" sideOffset={8}>
        {locales.map((locale) => (
          <DropdownMenuItem
            key={locale}
            onClick={() => switchLocale(locale)}
            className={locale === currentLocale ? "font-medium text-primary" : ""}
          >
            {localeNames[locale]}
            {locale === currentLocale && <span className="ml-auto text-xs">✓</span>}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
