/**
 * 前端运行时配置——读取 NEXT_PUBLIC_* 环境变量的统一出口。
 *
 * 纯静态常量（paths 等）放 lib/constants/；
 * 需要按部署环境覆盖的配置集中在此文件。
 *
 * @example
 * ```ts
 * import { APP, CONTACT } from "@/lib/config"
 * ```
 */

/** 应用基础信息 */
export const APP = {
  /** 应用名称，用于页面标题、描述文案等 */
  name: process.env.NEXT_PUBLIC_APP_NAME || "AAF",
  /** 默认页面标题 */
  defaultTitle: process.env.NEXT_PUBLIC_APP_DEFAULT_TITLE || "AAF - Agentic App Framework",
  /** 页面标题模板（%s 替换为页面名） */
  titleTemplate: process.env.NEXT_PUBLIC_APP_TITLE_TEMPLATE || "%s - AAF",
  /** 应用描述 */
  description: process.env.NEXT_PUBLIC_APP_DESCRIPTION || "多智能体应用开发框架",
  /** 站点 URL */
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL || "https://aaron.cn"
} as const

/** 联系方式 */
export const CONTACT = {
  /** 客服/合作邮箱 */
  email: process.env.NEXT_PUBLIC_CONTACT_EMAIL || "service@xuejiai.com",
  /** 微信号 */
  wechatId: process.env.NEXT_PUBLIC_CONTACT_WECHAT_ID || "Aaron-ZZH"
} as const
