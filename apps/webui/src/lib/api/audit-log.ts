/**
 * 审计日志 API 客户端
 * @author AaronZZH & Kiro
 */

import { type ListParams, fetchList, type PageResult } from "./client"

/** 字段变更记录 */
export interface FieldChange {
  field: string
  oldValue: string
  newValue: string
}

/** 审计日志记录 */
export interface AuditLogVO {
  id: string
  entityType: string
  entityId: string
  action: "create" | "update" | "delete"
  userId: string
  changes: FieldChange[]
  ip: string
  createdAt: string
}

/** 审计日志查询参数 */
export interface AuditLogListParams extends ListParams {
  entityType?: string
  userId?: string
  action?: string
  startTime?: string
  endTime?: string
}

export const auditLogApi = {
  /** 审计日志分页列表 */
  list: (params: AuditLogListParams = {}) =>
    fetchList<AuditLogVO>("/admin/audit-log", params)
}
