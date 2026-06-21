/**
 * 法律文档 API——服务条款 / 隐私政策 + 用户同意快照
 * @author AaronZZH & Kiro
 */

import { request } from "@/lib/api/rest/entity/crud"

/** 法律文档（公开端） */
export interface LegalDocument {
  id: number
  /** legal-terms / legal-privacy */
  type: "legal-terms" | "legal-privacy"
  title: string
  content: string
  /** 版本号（front_matter.version） */
  version: string
  /** 生效日期（ISO-8601 字符串） */
  effectiveDate: string | null
  /** 最后更新时间 */
  updateTime: string
}

/** 待同意文档列表 */
export interface PendingConsent {
  count: number
  items: LegalDocument[]
}

/** 简称：terms / privacy */
export type LegalTypeAlias = "terms" | "privacy"

/**
 * 公开端：获取最新已发布的法律文档
 *
 * 接口走 /api/public/legal/{type}，未登录也可访问，由 SecurityConfig.PUBLIC_PATHS 放行。
 */
export const legalApi = {
  getLatest(type: LegalTypeAlias) {
    return request<LegalDocument>(`/public/legal/${type}`)
  },

  /** 查询当前用户尚未同意的法律文档（需登录） */
  pending() {
    return request<PendingConsent>("/legal/consent/pending")
  },

  /** 提交对某文档的同意（需登录） */
  submit(documentId: number) {
    return request<void>("/legal/consent", {
      method: "POST",
      body: JSON.stringify({ documentId })
    })
  }
}
