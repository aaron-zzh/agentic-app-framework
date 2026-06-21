/**
 * 套餐相关工具函数——金额、折扣、ext 解析
 *
 * @author AaronZZH & Kiro
 */

import type { SubscriptionPlanVO } from "@/lib/api/rest/billing/plans"

/** 套餐 ext JSON 解析结果 */
export interface PlanExt {
  /** 一句话副标题，如"适合个人开发者" */
  tagline?: string
  /** 是否是推荐套餐（前端高亮） */
  recommended?: boolean
}

/** 安全解析 plan.ext JSON 字符串 */
export function parsePlanExt(ext: string | null): PlanExt {
  if (!ext) return {}
  try {
    return JSON.parse(ext) as PlanExt
  } catch {
    return {}
  }
}

/** 分转元格式化（最多 2 位小数，整数省略小数） */
export function formatYuan(fen: number): string {
  const yuan = fen / 100
  return yuan.toLocaleString("zh-CN", {
    minimumFractionDigits: Number.isInteger(yuan) ? 0 : 2,
    maximumFractionDigits: 2
  })
}

/**
 * 按 yearlyPrice 与 price*12 的比例计算实际折扣，返回 [0, 1) 表示折扣率。
 *
 * 如 yearlyPrice=9600 (96 元), price=1000 (10 元/月)，则 9600/12000 = 0.8，即"8 折"。
 * yearlyPrice 为 0（FREE 套餐）或 price 为 0 时返回 null（不显示折扣）。
 */
export function calcYearlyDiscount(plan: SubscriptionPlanVO): number | null {
  if (plan.price <= 0 || plan.yearlyPrice <= 0) return null
  const ratio = plan.yearlyPrice / (plan.price * 12)
  // 没有实际折扣（≥0.99）则不显示
  if (ratio >= 0.99) return null
  return ratio
}

/**
 * 折扣率转中文显示，0.8 → "8 折"，0.85 → "8.5 折"。
 */
export function formatDiscount(ratio: number): string {
  const tenth = Math.round(ratio * 100) / 10
  // 整数折（如 8 折），保留 0 位小数；非整数（如 8.5 折）保留 1 位
  const formatted = Number.isInteger(tenth) ? tenth.toFixed(0) : tenth.toFixed(1)
  return `${formatted} 折`
}

/** 当前月度有效价格（用于积分性价比计算） */
export function effectiveMonthlyPrice(
  plan: SubscriptionPlanVO,
  cycle: "monthly" | "yearly"
): number {
  return cycle === "yearly" ? plan.yearlyPrice / 12 : plan.price
}
