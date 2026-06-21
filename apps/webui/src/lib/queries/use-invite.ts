/**
 * 邀请奖励 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { inviteApi } from "@/lib/api/rest/brokerage/invite"
import { useAuthStore } from "@/lib/store/auth-store"

const INVITE_CODE_KEY = ["brokerage", "me", "invite-code"]
const INVITE_HISTORY_KEY = ["brokerage", "me", "invite-history"]
const INVITE_REWARDS_KEY = ["brokerage", "me", "invite-rewards"]

/** 我的邀请码（一人一码，登录后自动取/生成） */
export function useMyInviteCode(channel?: string) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: [...INVITE_CODE_KEY, channel ?? "default"],
    queryFn: () => inviteApi.getMyInviteCode(channel),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000
  })
}

/** 我邀请的好友列表（分页） */
export function useMyInviteHistory(page: number, size = 20) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: [...INVITE_HISTORY_KEY, page, size],
    queryFn: () => inviteApi.getInviteHistory(page, size),
    enabled: isAuthenticated
  })
}

/** 邀请奖励配置（运营改后台 / 改 SQL 即时生效） */
export function useInviteRewards() {
  return useQuery({
    queryKey: INVITE_REWARDS_KEY,
    queryFn: inviteApi.getInviteRewards,
    staleTime: 60 * 1000
  })
}
