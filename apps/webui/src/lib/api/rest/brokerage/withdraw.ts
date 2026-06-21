/**
 * 提现 API 客户端 + hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "@/lib/api/types"
import { backendApi } from "../backend-client"

export type WithdrawType = "WECHAT" | "ALIPAY" | "BANK"
export type WithdrawStatus = "PENDING" | "APPROVED" | "REJECTED" | "TRANSFERRED"

export interface WithdrawVO {
  id: string
  contactId: number
  amount: number
  fee: number
  type: WithdrawType
  accountName: string
  accountNo: string
  status: WithdrawStatus
  auditReason: string | null
  auditTime: string | null
  createTime: string
}

export interface WithdrawApplyReq {
  amount: number
  type: WithdrawType
  accountName: string
  accountNo: string
}

const withdrawApi = {
  getBalance: () => backendApi.get<number>("/brokerage/me/balance"),
  apply: (data: WithdrawApplyReq) => backendApi.post<WithdrawVO>("/brokerage/me/withdraw", data),
  list: (page = 0, size = 20) =>
    backendApi.get<PageResult<WithdrawVO>>(`/brokerage/me/withdraws?page=${page}&size=${size}`)
}

const BALANCE_KEY = ["brokerage", "me", "balance"]
const WITHDRAWS_KEY = ["brokerage", "me", "withdraws"]

export function useMyBalance() {
  return useQuery({
    queryKey: BALANCE_KEY,
    queryFn: withdrawApi.getBalance,
    staleTime: 30_000
  })
}

export function useMyWithdraws(page = 0) {
  return useQuery({
    queryKey: [...WITHDRAWS_KEY, page],
    queryFn: () => withdrawApi.list(page)
  })
}

export function useApplyWithdraw() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: withdrawApi.apply,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: BALANCE_KEY })
      qc.invalidateQueries({ queryKey: WITHDRAWS_KEY })
    }
  })
}
