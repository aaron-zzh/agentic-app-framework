/**
 * i18n 模块公开 API
 * @author AaronZZH & Kiro
 */

export { defaultLocale, LOCALE_COOKIE, type Locale, locales } from "./config"
export { detectLanguage, getUserLocale, setUserLocale } from "./locale"
export {
  formatNumberLocale,
  type LocaleConfig,
  useFormatNumberLocale
} from "./number-format-locale"
export { initZodErrorMap } from "./zod-error-map"
