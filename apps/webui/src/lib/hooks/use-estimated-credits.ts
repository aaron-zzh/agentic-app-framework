/**
 * useEstimatedCredits——根据模型/能力/参数估算积分消耗
 * 依赖 useTokenRules（GET /credit-token-rules）
 *
 * v0.2 (N5): 加入 multiplier 计算
 *   - 图像 N 张 = rule.creditAmount × count
 *   - 视频按时长倍增（基准 5s，10s=2x，15s=3x，等）
 *
 * 返回 { credits, sufficient }
 *   - credits: number | null  null 表示未找到规则，显示"费用以后台为准"
 *   - sufficient: boolean      true = 余额充足
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useCreditBalance, useTokenRules } from "@/lib/queries/use-credits"

/** 视频积分基准时长（秒）：rule.creditAmount 对应 5s 一段视频的扣费 */
const VIDEO_BASE_DURATION_SECONDS = 5

export interface EstimatedCreditsParams {
  modelId: string | null
  /** 能力标识（如 IMAGE_GEN / VIDEO_GEN），用于模糊匹配规则名称 */
  capability?: string
  /** 生成数量（如 imageCount / videoCount），线性倍增 */
  count?: number
  /** 视频时长（秒），用于按 5s 基准线性倍增；图像不传 */
  durationSeconds?: number
}

export interface EstimatedCreditsResult {
  /** 预估积分消耗，null 表示无规则配置 */
  credits: number | null
  /** 余额是否充足 */
  sufficient: boolean
}

export function useEstimatedCredits({
  modelId,
  capability,
  count = 1,
  durationSeconds
}: EstimatedCreditsParams): EstimatedCreditsResult {
  const { data: rules } = useTokenRules()
  const { data: balance } = useCreditBalance()

  if (!modelId || !rules || rules.length === 0) {
    return { credits: null, sufficient: true }
  }

  // 匹配规则：先精确匹配规则 name 含 modelId，再按 capability 模糊匹配
  const lowerModel = modelId.toLowerCase()
  const lowerCap = capability?.toLowerCase() ?? ""

  const matched =
    rules.find((r) => r.status === "ENABLED" && r.name.toLowerCase().includes(lowerModel)) ??
    rules.find((r) => r.status === "ENABLED" && lowerCap && r.name.toLowerCase().includes(lowerCap))

  if (!matched) {
    return { credits: null, sufficient: true }
  }

  // 数量倍率
  const countMultiplier = Math.max(1, count)
  // 视频时长倍率（无时长按 1 倍，5s 基准）
  const durationMultiplier =
    durationSeconds && durationSeconds > 0
      ? Math.max(1, Math.ceil(durationSeconds / VIDEO_BASE_DURATION_SECONDS))
      : 1

  const credits = matched.creditAmount * countMultiplier * durationMultiplier
  const currentBalance = balance?.balance ?? 0

  return { credits, sufficient: currentBalance >= credits }
}
