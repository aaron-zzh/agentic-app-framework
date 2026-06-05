import { useLocale } from "next-intl"
import { getLocale } from "next-intl/server"

// -------------------------------------------------------

export type LocaleConfig = {
  code: string
  currency: string
}

const localeConfigMap: Record<string, LocaleConfig> = {
  zh: { code: "zh-CN", currency: "CNY" },
  en: { code: "en-US", currency: "USD" }
}

function getLocaleConfig(locale: string): LocaleConfig {
  return localeConfigMap[locale] ?? localeConfigMap.en
}

/** 客户端组件使用 */
export function useFormatNumberLocale(): LocaleConfig {
  const locale = useLocale()
  return getLocaleConfig(locale)
}

/** 服务端组件 / API 使用 */
export async function formatNumberLocale(): Promise<LocaleConfig> {
  const locale = await getLocale()
  return getLocaleConfig(locale)
}
