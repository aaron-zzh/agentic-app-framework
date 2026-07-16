import { backendApi } from "../backend-client"

/**
 * 访客线索 API 客户端。
 *
 * 端点 {@code /api/public/leads/**} 公开访问，不需要 token；后端按 IP 速率限制。
 *
 * @author AaronZZH & Kiro
 */

export type LeadChannel = "VISIT" | "CHAT" | "NEWSLETTER" | "CONTACT" | "FEEDBACK"
export type LeadStatus = "NEW" | "PROCESSING" | "RESOLVED" | "SPAM" | "CLOSED"

export interface GuestLead {
  id: number
  anonymousId: string
  channel: LeadChannel
  email?: string
  name?: string
  phone?: string
  subject?: string
  content?: string
  threadId?: string
  agentRole?: string
  lastMessageAt?: string
  status: LeadStatus
  contactId?: number
  createTime: string
  updateTime: string
}

export interface CreateLeadParams {
  anonymousId: string
  channel: LeadChannel
  email?: string
  name?: string
  phone?: string
  subject?: string
  content?: string
  threadId?: string
  agentRole?: string
}

export const leadApi = {
  /** 提交线索（4 个 channel 通用） */
  create: (params: CreateLeadParams) => backendApi.post<GuestLead>("/public/leads", params),

  /** 查询访客自己的线索（按 anonymousId 过滤） */
  listMine: (anonymousId: string, channel?: LeadChannel) => {
    const qs = new URLSearchParams({ anonymousId })
    if (channel) qs.set("channel", channel)
    return backendApi.get<GuestLead[]>(`/public/leads/me?${qs.toString()}`)
  },

  /** 查询访客最近一次 CHAT 记录（续聊取 threadId） */
  latestChat: (anonymousId: string) => {
    const qs = new URLSearchParams({ anonymousId })
    return backendApi.get<GuestLead | null>(`/public/leads/me/latest-chat?${qs.toString()}`)
  }
}
