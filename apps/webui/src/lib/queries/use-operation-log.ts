/**
 * 操作日志 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { type OperationLogListParams, operationLogApi } from "@/lib/api/rest/admin/operation-log"

const KEYS = {
  list: (params: OperationLogListParams) => ["operation-log", "list", params] as const
}

export function useOperationLogList(params: OperationLogListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => operationLogApi.list(params)
  })
}
