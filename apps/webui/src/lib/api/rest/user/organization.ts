/**
 * 组织管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

/** 组织信息 */
export interface OrganizationVO {
  id: string
  name: string
  slug: string
  logo?: string
  plan?: "free" | "pro" | "enterprise"
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
  name?: string
  slug?: string
  logo?: string
}

/** 添加成员请求 */
export interface OrgAddMemberReq {
  userId: string
  role: "admin" | "member" | "guest"
}

export const organizationApi = {
  /** 获取当前用户的组织列表 */
  list: () => backendApi.get<OrganizationVO[]>("/organizations"),

  /** 更新组织信息 */
  update: (id: string, data: OrgUpdateReq) =>
    backendApi.put<OrganizationVO>(`/organizations/${id}`, data),

  /** 获取组织成员列表 */
  members: (orgId: string) => backendApi.get<OrgMemberVO[]>(`/organizations/${orgId}/members`),

  /** 添加成员 */
  addMember: (orgId: string, data: OrgAddMemberReq) =>
    backendApi.post<OrgMemberVO>(`/organizations/${orgId}/members`, data),

  /** 移除成员 */
  removeMember: (orgId: string, userId: string) =>
    backendApi.delete<void>(`/organizations/${orgId}/members/${userId}`)
}
