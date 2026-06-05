/**
 * next-intl 服务端请求配置
 * @author AaronZZH & Kiro
 *
 * 通过 cookie / Accept-Language 检测用户语言，加载对应消息文件。
 */

import { getRequestConfig } from "next-intl/server"

import { getUserLocale } from "./locale"

export default getRequestConfig(async () => {
  const locale = await getUserLocale()

  return {
    locale,
    messages: (await import(`./messages/${locale}.json`)).default,
    formats: {
      dateTime: {
        short: {
          day: "numeric",
          month: "short",
          year: "numeric"
        },
        long: {
          day: "numeric",
          month: "short",
          year: "numeric",
          hour: "numeric",
          minute: "numeric"
        }
      },
      number: {
        precise: {
          maximumFractionDigits: 5
        }
      }
    }
  }
})
