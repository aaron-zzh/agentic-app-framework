/**
 * 套餐相关工具函数——金额、折扣、ext 解析
 *
 * @author AaronZZH & Kiro
 */

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
