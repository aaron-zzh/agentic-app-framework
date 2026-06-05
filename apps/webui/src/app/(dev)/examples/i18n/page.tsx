/**
 * i18n Examples 页面
 * 展示 next-intl 国际化功能：语言切换、翻译、数字/日期格式化
 */

import { getTranslations } from "next-intl/server"

import { LanguageSwitcher } from "@/sections/layout/LanguageSwitcher"

import { I18nClientDemo } from "./_components/I18nClientDemo"

export default async function I18nExamplesPage() {
  const t = await getTranslations("i18nExamples")

  return (
    <div className="container mx-auto max-w-3xl space-y-8 p-8">
      {/* 页头 */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="font-bold text-2xl">{t("title")}</h1>
          <p className="mt-1 text-muted-foreground">{t("subtitle")}</p>
        </div>
        <LanguageSwitcher />
      </div>

      {/* 服务端翻译 */}
      <section className="rounded-lg border p-6">
        <h2 className="mb-4 font-semibold">{t("basicUsage")} (Server Component)</h2>
        <ul className="space-y-2 text-sm">
          <li>
            <span className="text-muted-foreground">greeting: </span>
            {t("greeting", { name: "AAF" })}
          </li>
          <li>
            <span className="text-muted-foreground">itemCount(0): </span>
            {t("itemCount", { count: 0 })}
          </li>
          <li>
            <span className="text-muted-foreground">itemCount(1): </span>
            {t("itemCount", { count: 1 })}
          </li>
          <li>
            <span className="text-muted-foreground">itemCount(5): </span>
            {t("itemCount", { count: 5 })}
          </li>
        </ul>
      </section>

      {/* Server Action 说明 */}
      <section className="rounded-lg border border-blue-200 bg-blue-50 p-6 dark:border-blue-800 dark:bg-blue-950">
        <h2 className="mb-2 font-semibold">{t("serverActionTitle")}</h2>
        <p className="text-muted-foreground text-sm">{t("serverActionDesc")}</p>
        <ul className="mt-3 space-y-1 text-muted-foreground text-xs">
          <li>
            • Cookie: <code className="rounded bg-muted px-1 py-0.5">NEXT_LOCALE</code>
          </li>
          <li>• 检测优先级: Cookie → Accept-Language → 默认语言(zh)</li>
          <li>
            • 切换后调用 <code className="rounded bg-muted px-1 py-0.5">router.refresh()</code>{" "}
            重渲染
          </li>
        </ul>
      </section>

      {/* 客户端组件 Demo（useTranslations / useLocale / 数字日期格式化） */}
      <I18nClientDemo />
    </div>
  )
}
