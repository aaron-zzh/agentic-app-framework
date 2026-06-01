/**
 * 审批委托 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

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

export const delegationApi = {
  /** 获取委托列表 */
  list: () => backendApi.get<DelegationVO[]>("/delegations"),

  /** 创建委托 */
  create: (data: DelegationCreateReq) =>
    backendApi.post<DelegationVO>("/delegations", data),

  /** 取消委托 */
  cancel: (id: string) => backendApi.delete<void>(`/delegations/${id}`)
}
