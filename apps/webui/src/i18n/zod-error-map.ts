/**
 * Zod 国际化 error map
 * @author AaronZZH & Kiro
 *
 * 将 Zod 校验错误消息映射为 next-intl 翻译 key。
 * 在应用初始化时调用 initZodErrorMap(t) 注入翻译函数。
 *
 * 用法：
 * ```tsx
 * "use client"
 * import { useTranslations } from "next-intl"
 * import { initZodErrorMap } from "@/i18n/zod-error-map"
 *
 * // 在 Provider 组件中调用一次
 * const t = useTranslations("validation")
 * initZodErrorMap(t)
 * ```
 */

import { z } from "zod"

/** 翻译函数类型（兼容 next-intl useTranslations 返回值） */
type TranslateFunction = (key: string, params?: Record<string, string | number>) => string

/**
 * 初始化 Zod 全局 error map，注入翻译函数
 * 应在客户端 Provider 中调用一次
 */
export function initZodErrorMap(t: TranslateFunction): void {
  z.setErrorMap((issue, ctx) => {
    switch (issue.code) {
      case z.ZodIssueCode.too_small:
        if (issue.type === "string") {
          return { message: t("string.min", { min: issue.minimum as number }) }
        }
        if (issue.type === "number") {
          return { message: t("number.min", { min: issue.minimum as number }) }
        }
        return { message: ctx.defaultError }

      case z.ZodIssueCode.too_big:
        if (issue.type === "string") {
          return { message: t("string.max", { max: issue.maximum as number }) }
        }
        if (issue.type === "number") {
          return { message: t("number.max", { max: issue.maximum as number }) }
        }
        return { message: ctx.defaultError }

      case z.ZodIssueCode.invalid_string:
        if (issue.validation === "email") {
          return { message: t("string.email") }
        }
        return { message: ctx.defaultError }

      case z.ZodIssueCode.invalid_type:
        if (issue.received === "undefined" || issue.received === "null") {
          return { message: t("required") }
        }
        return { message: t("invalid_type") }

      case z.ZodIssueCode.invalid_enum_value:
        return { message: t("invalid_enum") }

      default:
        return { message: ctx.defaultError }
    }
  })
}
