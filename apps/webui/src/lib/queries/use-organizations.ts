/**
 * 组织管理 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type OrgAddMemberReq, type OrgUpdateReq, organizationApi } from "@/lib/api/organization"

const KEYS = {
  orgs: ["organizations"] as const,
  members: (orgId: string) => ["organizations", orgId, "members"] as const
}

/** 查询当前用户的组织列表 */
export function useOrganizations() {
  return useQuery({
    queryKey: KEYS.orgs,
    queryFn: organizationApi.list
  })
}

/** 更新组织信息 */
export function useUpdateOrganization() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: OrgUpdateReq }) =>
      organizationApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.orgs })
  })
}

/** 查询组织成员列表 */
export function useOrgMembers(orgId: string) {
  return useQuery({
    queryKey: KEYS.members(orgId),
    queryFn: () => organizationApi.members(orgId),
    enabled: !!orgId
  })
}

/** 添加成员 */
export function useAddOrgMember(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: OrgAddMemberReq) => organizationApi.addMember(orgId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.members(orgId) })
  })
}

/** 移除成员 */
export function useRemoveOrgMember(orgId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => organizationApi.removeMember(orgId, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.members(orgId) })
  })
}
