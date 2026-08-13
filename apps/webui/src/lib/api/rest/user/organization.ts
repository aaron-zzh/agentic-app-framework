/**
 * 组织管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "@/lib/api/types"
import { backendApi } from "../backend-client"

/** 组织信息 */
export interface OrganizationVO {
  id: string
  name: string
  slug: string
  logo?: string
  plan?: "free" | "pro" | "enterprise"
  memberRole?: "owner" | "admin" | "member" | "guest"
}

/** 工作区信息 */
export interface WorkspaceVO {
  id: string
  orgId: string
  name: string
  slug: string
  ownerId: string
  createTime: string
}

/** 当前用户的默认组织与工作区上下文 */
export interface DefaultOrgContextVO {
  orgId: string
  orgName: string
  workspaceId: string
  workspaceName: string
}

/** 组织成员 */
export interface OrgMemberVO {
  userId: string
  username: string
  nickname: string
  avatar?: string
  role: "owner" | "admin" | "member" | "guest"
  joinedAt: string
}

/** 更新组织请求 */
export interface OrgUpdateReq {
  name: string
}

/** 更新工作区请求 */
export interface WorkspaceUpdateReq {
  name: string
}

/** 添加成员请求 */
export interface OrgAddMemberReq {
  userId: string
  role: "admin" | "member" | "guest"
}

export const organizationApi = {
  /** 获取当前用户可切换的组织列表；该引导接口不依赖当前组织上下文。 */
  list: () =>
    backendApi.get<OrganizationVO[]>("/system/orgs", {
      headers: { "X-Org-Id": "", "X-Workspace-Id": "" }
    }),

  /** 获取当前用户的默认组织与工作区；该引导接口不依赖当前组织/工作区 Header。 */
  defaultContext: () =>
    backendApi.get<DefaultOrgContextVO>("/system/orgs/default-context", {
      headers: { "X-Org-Id": "", "X-Workspace-Id": "" },
      showError: false
    }),

  /** 更新组织信息 */
  update: (id: string, data: OrgUpdateReq) =>
    backendApi.put<OrganizationVO>(`/system/orgs/${id}`, data),

  /** 获取组织成员列表 */
  members: (orgId: string) => backendApi.get<OrgMemberVO[]>(`/system/orgs/${orgId}/members`),

  /** 添加成员 */
  addMember: (orgId: string, data: OrgAddMemberReq) =>
    backendApi.post<OrgMemberVO>(`/system/orgs/${orgId}/members`, data),

  /** 移除成员 */
  removeMember: (orgId: string, userId: string) =>
    backendApi.delete<void>(`/system/orgs/${orgId}/members/${userId}`)
}

export const workspaceApi = {
  /** 获取可切换工作区；聚合范围用 all 一次加载全部可访问组织工作区。 */
  list: (orgId: string, allOrganizations: boolean) =>
    backendApi.get<PageResult<WorkspaceVO>>("/system/workspaces", {
      params: { pageNo: 1, pageSize: -1, sort: "id:asc", memberOnly: true },
      headers: {
        "X-Org-Id": allOrganizations ? "all" : orgId,
        "X-Workspace-Id": ""
      }
    }),

  /** 更新工作区显示名称。 */
  update: (id: string, orgId: string, data: WorkspaceUpdateReq) =>
    backendApi.put<WorkspaceVO>(`/system/workspaces/${id}`, data, {
      headers: { "X-Org-Id": orgId, "X-Workspace-Id": id }
    })
}

const KEYS = {
  orgs: ["organizations"] as const,
  workspaces: (orgId: string, allOrganizations: boolean) =>
    ["workspaces", allOrganizations ? "all" : orgId] as const,
  members: (orgId: string) => ["organizations", orgId, "members"] as const
}

/** 查询当前用户可切换的组织列表。 */
export function useOrganizations() {
  return useQuery({
    queryKey: KEYS.orgs,
    queryFn: organizationApi.list
  })
}

/** 查询工作区列表；super_admin 可跨组织一次加载。 */
export function useWorkspaces(orgId: string | null, allOrganizations: boolean) {
  return useQuery({
    queryKey: KEYS.workspaces(orgId ?? "", allOrganizations),
    queryFn: () => workspaceApi.list(orgId ?? "", allOrganizations),
    enabled: allOrganizations || (orgId != null && orgId !== "all")
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

/** 更新工作区显示名称。 */
export function useUpdateWorkspace() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, orgId, data }: { id: string; orgId: string; data: WorkspaceUpdateReq }) =>
      workspaceApi.update(id, orgId, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["workspaces"] })
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
