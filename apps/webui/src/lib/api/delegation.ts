/**
 * 审批委托 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

/** 委托范围类型 */
export type DelegationScope = "all" | "specific"

/** 委托状态 */
export type DelegationStatus = "active" | "expired" | "cancelled"

/** 委托记录 */
export interface DelegationVO {
  id: string
  delegateTo: string
  delegateToName: string
  startTime: string
  endTime: string
  scope: DelegationScope
  processKeys?: string[]
  status: DelegationStatus
  createdAt: string
}

/** 创建委托请求 */
export interface DelegationCreateReq {
  delegateTo: string
  startTime: string
  endTime: string
  scope: DelegationScope
  processKeys?: string[]
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

export const delegationApi = {
  /** 获取委托列表 */
  list: () => req<DelegationVO[]>("/delegations"),

  /** 创建委托 */
  create: (data: DelegationCreateReq) =>
    req<DelegationVO>("/delegations", { method: "POST", body: JSON.stringify(data) }),

  /** 取消委托 */
  cancel: (id: string) => req<void>(`/delegations/${id}`, { method: "DELETE" })
}
