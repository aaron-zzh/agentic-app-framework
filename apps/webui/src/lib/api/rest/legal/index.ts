import { backendApi } from "../backend-client"

/**
 * 法律文档 API——服务条款 / 隐私政策 + 用户同意快照
 * @author AaronZZH & Kiro
 */

/** 法律文档（公开端） */
export interface LegalDocument {
  id: number
  /** 精确发布的不可变 DocumentVersion ID */
  documentVersionId: number
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
    return backendApi.get<LegalDocument>(`/public/legal/${type}`)
  },

  /**
   * 公开端：获取用户实际待同意的精确发布版本。
   *
   * 版本已不是当前发布版本时返回 404，调用方应刷新 pending 列表重新阅读。
   */
  getVersion(type: LegalTypeAlias, documentVersionId: number) {
    return backendApi.get<LegalDocument>(`/public/legal/${type}/versions/${documentVersionId}`)
  },

  /** 查询当前用户尚未同意的法律文档（需登录） */
  pending() {
    return backendApi.get<PendingConsent>("/legal/consent/pending")
  },

  /** 提交对某文档实际阅读的精确版本的同意（需登录） */
  submit(documentId: number, documentVersionId: number) {
    return backendApi.post<void>("/legal/consent", { documentId, documentVersionId })
  }
}
