/**
 * I18nClientDemo——客户端 i18n 演示组件
 * 展示 useTranslations / useLocale / useFormatNumberLocale
 */

"use client"

import { useFormatter, useLocale, useNow, useTranslations } from "next-intl"

import { useFormatNumberLocale } from "@/i18n/number-format-locale"

export function I18nClientDemo() {
  const t = useTranslations("i18nExamples")
  const locale = useLocale()
  const format = useFormatter()
  const now = useNow()
  const { code, currency } = useFormatNumberLocale()

  const price = 12345.6789

  return (
    <div className="space-y-6">
      {/* 当前语言 */}
      <section className="rounded-lg border p-6">
        <h2 className="mb-4 font-semibold">{t("basicUsage")} (Client Component)</h2>
        <div className="flex items-center gap-3">
          <span className="text-muted-foreground text-sm">{t("currentLocale")}:</span>
          <code className="rounded bg-muted px-2 py-1 font-medium text-sm">{locale}</code>
        </div>
      </section>

      {/* 数字 & 日期格式化 */}
      <section className="rounded-lg border p-6">
        <h2 className="mb-4 font-semibold">{t("formatsTitle")}</h2>
        <ul className="space-y-2 text-sm">
          <li>
            <span className="text-muted-foreground">{t("numberFormat")}: </span>
            <code className="rounded bg-muted px-1 py-0.5">
              {new Intl.NumberFormat(code).format(price)}
            </code>
          </li>
          <li>
            <span className="text-muted-foreground">{t("currencyFormat")}: </span>
            <code className="rounded bg-muted px-1 py-0.5">
              {new Intl.NumberFormat(code, { style: "currency", currency }).format(price)}
            </code>
          </li>
          <li>
            <span className="text-muted-foreground">{t("dateFormat")}: </span>
            <code className="rounded bg-muted px-1 py-0.5">
              {format.dateTime(now, { dateStyle: "long" })}
            </code>
          </li>
          <li>{t("today", { date: now })}</li>
        </ul>
      </section>
    </div>
  )
}
