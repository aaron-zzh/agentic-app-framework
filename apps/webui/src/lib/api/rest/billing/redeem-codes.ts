/**
 * 兑换码（CreditRedeemCode）管理端 API 客户端
 *
 * <p>对应后端 {@code CreditRedeemCodeController}：
 * - POST /billing/credit-redeem-codes/generate          单个生成，返回明文（仅一次可见）
 * - POST /billing/credit-redeem-codes/generate-batch    批量生成，返回 Excel xlsx Blob
 *
 * <p>用户兑换接口（{@code redeem}）已存在于 {@link import("./credits").creditsApi}，此处仅提供管理端能力。
 *
 * @author AaronZZH & Kiro
 */

import { backendClient } from "@/lib/api/rest/backend-client"
import { request } from "../entity/crud"

/** 兑换码类型：CREDIT=积分码，MEMBERSHIP=会员码 */
export type RedeemCodeType = "CREDIT" | "MEMBERSHIP"

/** 积分批次类型，与后端 credit_transaction.batch_type 对齐 */
export type RedeemCodeBatchType = "SUBSCRIPTION" | "TOPUP" | "REWARD" | "WEEKLY" | "MANUAL"

/** 创建参数（与后端 CreditRedeemCodeCreateDTO 对齐） */
export interface RedeemCodeCreateDTO {
  /** 积分数量，>=1 */
  creditAmount: number
  /** 积分类型，默认 REWARD */
  batchType?: RedeemCodeBatchType
  /** 兑换码类型，默认 CREDIT */
  type?: RedeemCodeType
  /** 会员套餐 ID（type=MEMBERSHIP 时必填） */
  planId?: number | null
  /** 过期时间（ISO 字符串，可空表示永不过期） */
  expiresAt?: string | null
  /** 备注 */
  remark?: string | null
}

export const redeemCodesApi = {
  /** 单个生成：返回明文（仅本次响应可见，后端只持久化哈希） */
  generate: (dto: RedeemCodeCreateDTO): Promise<string> =>
    request<string>("/billing/credit-redeem-codes/generate", {
      method: "POST",
      body: JSON.stringify(dto)
    }),

  /**
   * 批量生成：后端直接返回 xlsx 二进制（Content-Disposition 含文件名），
   * 走原始 axios 通道避免被 RestApiClient 的 ApiResult 解析劫持
   */
  generateBatch: async (
    dto: RedeemCodeCreateDTO,
    count: number
  ): Promise<{ blob: Blob; filename: string }> => {
    const response = await backendClient.post<Blob>(
      `/billing/credit-redeem-codes/generate-batch?count=${count}`,
      dto,
      { responseType: "blob" }
    )
    const disposition = response.headers["content-disposition"] ?? ""
    const filename =
      disposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)?.[1] ?? "redeem-codes.xlsx"
    return { blob: response.data, filename: decodeURIComponent(filename) }
  }
}
