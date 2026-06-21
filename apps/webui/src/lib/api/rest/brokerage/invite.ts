/**
 * 邀请奖励 API 客户端（user-facing）
 *
 * <p>对接后端 BrokerageMeController：取/生成我的邀请码、我邀请的好友、奖励配置。
 *
 * @author AaronZZH & Kiro
 */

import { type PageResult, request } from "../entity/crud"

/** 我的邀请码 + 链接基础信息 */
export interface BrokerageInviteCodeMeVO {
  code: string
  channel: string | null
  usedCount: number
  maxInvites: number
}

/** 我邀请的好友列表项 */
export interface BrokerageInvitedUserVO {
  contactId: number
  nickname: string | null
  avatar: string | null
  registerTime: string
  isMember: boolean
  rewardCredits: number
}

/** 邀请奖励配置 */
export interface BrokerageInviteRewardConfigVO {
  registerReward: {
    enabled: boolean
    creditAmount: number
    expireDays: number
    maxInvites: number
  }
  subscribeReward: {
    enabled: boolean
    /** 后端 BigDecimal 序列化为 string */
    level1Rate: string | number
    frozenDays: number
  }
}

export const inviteApi = {
  /** 取/生成我的邀请码（一人一码，幂等） */
  getMyInviteCode: (channel?: string) => {
    const qs = channel ? `?channel=${encodeURIComponent(channel)}` : ""
    return request<BrokerageInviteCodeMeVO>(`/brokerage/me/invite-code${qs}`)
  },

  /** 我邀请的好友列表（分页） */
  getInviteHistory: (page = 0, size = 20) =>
    request<PageResult<BrokerageInvitedUserVO>>(
      `/brokerage/me/invite-history?page=${page}&size=${size}`
    ),

  /** 邀请奖励配置（前端展示用） */
  getInviteRewards: () => request<BrokerageInviteRewardConfigVO>("/brokerage/me/invite-rewards")
}
