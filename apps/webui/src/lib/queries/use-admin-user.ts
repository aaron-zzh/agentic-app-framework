/**
 * 用户管理 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { adminUserApi, type UserListParams } from "@/lib/api/rest/admin/user"

const KEYS = {
  list: (params: UserListParams) => ["admin", "users", "list", params] as const
}

export function useAdminUserList(params: UserListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => adminUserApi.list(params)
  })
}
