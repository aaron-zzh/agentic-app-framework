/**
 * Content Studio 项目类型、对象与生命周期展示配置。
 * @author AaronZZH & Kiro
 */

import {
  BadgePercent,
  BookOpenText,
  Box,
  Clapperboard,
  FileImage,
  FileSearch,
  FileText,
  Image,
  LayoutTemplate,
  ListTree,
  type LucideIcon,
  Megaphone,
  NotebookPen,
  Palette,
  Search,
  Share2,
  Sparkles,
  Store,
  UserRound
} from "lucide-react"
import type {
  AigcChannelCode,
  AigcObjectType,
  AigcProjectStatus,
  AigcProjectType
} from "@/lib/api/rest/ai/aigc"

export type StudioTone = "neutral" | "violet" | "cyan" | "emerald" | "amber" | "rose"

export interface ProjectTypeDisplayConfig {
  label: string
  icon: LucideIcon
  tone: StudioTone
  placeholder: string
}

const FALLBACK_PROJECT_TYPE: ProjectTypeDisplayConfig = {
  label: "内容项目",
  icon: LayoutTemplate,
  tone: "neutral",
  placeholder: "描述本次内容目标、受众、渠道与期望交付物。"
}

export const PROJECT_TYPE_CONFIG: Record<string, ProjectTypeDisplayConfig> = {
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
  blog_content: {
    label: "AI 博客",
    icon: NotebookPen,
    tone: "cyan",
    placeholder: "博客主题、目标读者、关键词、语气和参考来源是什么？"
  },
  narrative_series: {
    label: "系列叙事内容",
    icon: Clapperboard,
    tone: "rose",
    placeholder: "这个系列讲什么故事？共几集？每集时长和风格是什么？"
  }
}

export const OBJECT_TYPE_CONFIG: Record<AigcObjectType, { label: string; icon: LucideIcon }> = {
  brief: { label: "简报", icon: FileText },
  creative_concept: { label: "创意方向", icon: Sparkles },
  deliverable_set: { label: "内容包", icon: LayoutTemplate },
  image_deliverable: { label: "图片交付物", icon: Image },
  video_deliverable: { label: "视频交付物", icon: Clapperboard },
  copy_deliverable: { label: "文案交付物", icon: FileText },
  article_deliverable: { label: "博客文章", icon: BookOpenText },
  topic: { label: "文章选题", icon: NotebookPen },
  source_material_set: { label: "来源资料", icon: FileSearch },
  article_outline: { label: "文章提纲", icon: ListTree },
  seo_metadata: { label: "SEO 元数据", icon: Search },
  distribution_variant: { label: "渠道分发变体", icon: Share2 },
  episode: { label: "分集", icon: Clapperboard },
  scene: { label: "场次", icon: LayoutTemplate },
  shot: { label: "镜头", icon: Clapperboard },
  shot_keyframe: { label: "镜头关键帧", icon: FileImage },
  character: { label: "角色", icon: UserRound },
  prop: { label: "道具", icon: Box },
  review: { label: "审核", icon: FileSearch },
  canvas_board: { label: "画布节点", icon: Palette },
  property_subject: { label: "楼盘资料", icon: Store },
  claim_evidence: { label: "主张证据", icon: FileSearch }
}

export const CHANNEL_LABELS: Record<string, string> = {
  xiaohongshu: "小红书",
  douyin: "抖音",
  wechat_channels: "视频号",
  wechat_mp: "公众号",
  offline_poster: "线下海报",
  bilibili: "哔哩哔哩",
  website: "网站/博客"
}

export function getChannelLabel(channel: AigcChannelCode): string {
  return CHANNEL_LABELS[channel] ?? channel
}

export const PROJECT_STATUS_CONFIG: Record<AigcProjectStatus, { label: string; tone: StudioTone }> =
  {
    CONFIGURING: { label: "配置中", tone: "neutral" },
    MATERIALIZED: { label: "已物化", tone: "cyan" },
    CREATING: { label: "创作中", tone: "cyan" },
    EXECUTING: { label: "生成中", tone: "violet" },
    ADOPTING: { label: "待采用", tone: "amber" },
    REVIEWING: { label: "审核中", tone: "amber" },
    DELIVERING: { label: "交付中", tone: "violet" },
    COMPLETED: { label: "已完成", tone: "emerald" },
    ARCHIVED: { label: "已归档", tone: "neutral" }
  }

export const PROJECT_CREATION_STAGES: readonly AigcProjectStatus[] = [
  "CREATING",
  "EXECUTING",
  "ADOPTING"
]

export function isProjectContentWritable(status: AigcProjectStatus): boolean {
  return PROJECT_CREATION_STAGES.includes(status)
}

export function getProjectTypeConfig(
  type: Pick<AigcProjectType, "code" | "name" | "briefPlaceholder">
): ProjectTypeDisplayConfig {
  const configured = PROJECT_TYPE_CONFIG[type.code] ?? FALLBACK_PROJECT_TYPE
  return {
    ...configured,
    label: type.name || configured.label,
    placeholder: type.briefPlaceholder || configured.placeholder
  }
}

export function getObjectTypeConfig(type: AigcObjectType) {
  return OBJECT_TYPE_CONFIG[type]
}
