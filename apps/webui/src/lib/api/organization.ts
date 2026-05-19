/**
 * 组织管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

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

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const organizationApi = {
  /** 获取当前用户的组织列表 */
  list: () => req<OrganizationVO[]>("/organizations"),

  /** 更新组织信息 */
  update: (id: string, data: OrgUpdateReq) =>
    req<OrganizationVO>(`/organizations/${id}`, { method: "PUT", body: JSON.stringify(data) }),

  /** 获取组织成员列表 */
  members: (orgId: string) => req<OrgMemberVO[]>(`/organizations/${orgId}/members`),

  /** 添加成员 */
  addMember: (orgId: string, data: OrgAddMemberReq) =>
    req<OrgMemberVO>(`/organizations/${orgId}/members`, {
      method: "POST",
      body: JSON.stringify(data)
    }),

  /** 移除成员 */
  removeMember: (orgId: string, userId: string) =>
    req<void>(`/organizations/${orgId}/members/${userId}`, { method: "DELETE" })
}
