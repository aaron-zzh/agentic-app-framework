/**
 * next-intl 国际化配置
 * @author AaronZZH & Kiro
 *
 * 支持语言：zh（中文，默认）、en（英文）
 * 语言切换通过 cookie（NEXT_LOCALE）实现，不改变 URL 结构
 */

export const locales = ["zh", "en"] as const

export type Locale = (typeof locales)[number]

export const defaultLocale: Locale = "zh"

/** cookie 名称，用于持久化用户语言偏好 */
export const LOCALE_COOKIE = "NEXT_LOCALE"
