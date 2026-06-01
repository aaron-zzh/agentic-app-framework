/**
 * 审计日志 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { type AuditLogListParams, auditLogApi } from "@/lib/api/rest/admin/audit-log"

const KEYS = {
  list: (params: AuditLogListParams) => ["audit-log", "list", params] as const
}

/** 审计日志分页列表 */
export function useAuditLogList(params: AuditLogListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => auditLogApi.list(params)
  })
}
