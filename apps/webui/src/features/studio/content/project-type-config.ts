/**
 * Content Studio 项目类型与展示配置。
 * @author AaronZZH & Kiro
 */

import {
  BadgePercent,
  Clapperboard,
  type LucideIcon,
  Megaphone,
  Palette,
  Sparkles,
  Store,
  UserRound
} from "lucide-react"
import type {
  ContentChannel,
  ContentProjectStatus,
  ContentProjectTypeCode,
  ContentProjectTypeVO
} from "@/lib/api/rest/content"

export type StudioTone = "neutral" | "violet" | "cyan" | "emerald" | "amber" | "rose"

export interface ProjectTypeDisplayConfig {
  label: string
  icon: LucideIcon
  tone: StudioTone
  placeholder: string
}

export const PROJECT_TYPE_CONFIG: Record<ContentProjectTypeCode, ProjectTypeDisplayConfig> = {
  new_product: {
    label: "新品推广",
    icon: Sparkles,
    tone: "violet",
    placeholder: "产品是什么？核心卖点、价格或活动信息是什么？"
  },
  promotion: {
    label: "活动促销",
    icon: BadgePercent,
    tone: "rose",
    placeholder: "活动时间、优惠、门店/渠道和目标人群是什么？"
  },
  brand_visual: {
    label: "品牌视觉",
    icon: Palette,
    tone: "cyan",
    placeholder: "希望更新哪些视觉元素？哪些必须保持不变？"
  },
  store: {
    label: "门店宣传",
    icon: Store,
    tone: "amber",
    placeholder: "门店位置、主推服务、到店理由和活动信息是什么？"
  },
  social: {
    label: "社媒内容",
    icon: Megaphone,
    tone: "emerald",
    placeholder: "本周想传播什么主题？面向谁？希望用户做什么？"
  },
  personal_ip: {
    label: "个人 IP 内容",
    icon: UserRound,
    tone: "violet",
    placeholder: "这期想表达什么观点或故事？发布到哪里？"
  },
  narrative_series: {
    label: "系列叙事内容",
    icon: Clapperboard,
    tone: "rose",
    placeholder: "这个系列讲什么故事？共几集？每集时长和风格是什么？"
  }
}

export const CHANNEL_LABELS: Record<ContentChannel, string> = {
  xiaohongshu: "小红书",
  douyin: "抖音",
  wechat_channels: "视频号",
  wechat_mp: "公众号",
  offline_poster: "线下海报",
  bilibili: "哔哩哔哩"
}

export const PROJECT_STATUS_CONFIG: Record<
  ContentProjectStatus,
  { label: string; tone: StudioTone }
> = {
  draft: { label: "草稿", tone: "neutral" },
  in_progress: { label: "进行中", tone: "cyan" },
  reviewing: { label: "审核中", tone: "amber" },
  completed: { label: "已完成", tone: "emerald" },
  archived: { label: "已归档", tone: "neutral" }
}

export function getProjectTypeConfig(
  type: Pick<ContentProjectTypeVO, "code" | "name" | "briefPlaceholder">
) {
  const configured = PROJECT_TYPE_CONFIG[type.code]
  return {
    ...configured,
    label: type.name || configured.label,
    placeholder: type.briefPlaceholder || configured.placeholder
  }
}
