/**
 * LanguageSwitcher——语言切换器
 * @author AaronZZH & Kiro
 *
 * 放置在 AppHeader 用户菜单区域，通过 DropdownMenu 切换语言。
 * 切换时调用 setUserLocale server action 写入 cookie，通过 router.refresh() 触发服务端重渲染。
 */

"use client"

import { Globe } from "lucide-react"
import { useRouter } from "next/navigation"
import { useLocale, useTranslations } from "next-intl"
import { useTransition } from "react"

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { type Locale, locales } from "@/i18n/config"
import { setUserLocale } from "@/i18n/locale"

/** 语言显示名称映射 */
const localeNames: Record<Locale, string> = {
  zh: "中文",
  en: "English"
}

export function LanguageSwitcher() {
  const currentLocale = useLocale()
  const t = useTranslations("layout")
  const router = useRouter()
  const [isPending, startTransition] = useTransition()

  function switchLocale(locale: Locale) {
    if (locale === currentLocale) return
    startTransition(async () => {
      await setUserLocale(locale)
      router.refresh()
    })
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground disabled:opacity-50"
        aria-label={t("switchLanguage")}
        disabled={isPending}
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
