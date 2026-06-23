/**
 * Studio 五度空间导航配置
 *
 * 顶级 5 工作区（创作 / 项目 / 资产 / 知识 / 我），每区子菜单 ≤5 类。
 * 详见 docs/design/apps/webui/user-studio-mvp.md
 */

import {
  BookOpen,
  Box,
  CircleDollarSign,
  CircleUser,
  FileText,
  FolderKanban,
  Gift,
  Heart,
  History,
  Home,
  Image as ImageIcon,
  Images,
  Layers,
  type LucideIcon,
  Mic,
  Palette,
  Settings,
  Shapes,
  Sparkles,
  Star,
  Tag,
  TrendingUp,
  User,
  Video,
  Wand2,
  Workflow,
  Wrench,
  Zap
} from "lucide-react"

export type StudioWorkspace = "home" | "create" | "projects" | "assets" | "knowledge" | "me" | "tools"

export interface StudioNavItem {
  /** 子菜单 key（用于路由拼接 + 状态） */
  key: string
  /** 显示名 */
  label: string
  /** 图标 */
  icon: LucideIcon
  /** 实际路由（同一 workspace 下） */
  path: string
  /** 是否是默认子项（点工作区直接打开） */
  default?: boolean
  /** 角标 NeonChip 文案（如 "新"） */
  badge?: string
}

export interface StudioWorkspaceConfig {
  workspace: StudioWorkspace
  /** 顶级名 */
  label: string
  /** 顶级图标 */
  icon: LucideIcon
  /** 顶级路由 */
  path: string
  /** 子菜单列表（每区 ≤5 类） */
  children: StudioNavItem[]
}

/**
 * 五度空间配置——单一真理源
 *
 * 顺序：创作 / 项目 / 资产 / 知识 / 我
 */
export const STUDIO_NAV: StudioWorkspaceConfig[] = [
  {
    workspace: "home",
    label: "首页",
    icon: Home,
    path: "/studio",
    children: [{ key: "home", label: "首页", icon: Home, path: "/studio", default: true }]
  },
  {
    workspace: "create",
    label: "创作",
    icon: Sparkles,
    path: "/studio/create",
    children: [
      { key: "image", label: "图像", icon: ImageIcon, path: "/studio/create/image", default: true },
      { key: "video", label: "视频", icon: Video, path: "/studio/create/video" },
      { key: "copy", label: "文案", icon: Wand2, path: "/studio/create/copy" },
      { key: "viral", label: "爆款", icon: Zap, path: "/studio/create/viral", badge: "热" },
      {
        key: "pipeline",
        label: "工作流",
        icon: Workflow,
        path: "/studio/create/pipeline",
        badge: "新"
      },
      { key: "tools", label: "工具箱", icon: Wrench, path: "/studio/create/tools", badge: "热"  }
    ]
  },
  {
    workspace: "projects",
    label: "项目",
    icon: FolderKanban,
    path: "/studio/projects",
    children: [
      {
        key: "all",
        label: "全部",
        icon: Layers,
        path: "/studio/projects?status=all",
        default: true
      },
      { key: "active", label: "进行中", icon: TrendingUp, path: "/studio/projects?status=active" },
      { key: "draft", label: "草稿", icon: FileText, path: "/studio/projects?status=draft" },
      { key: "done", label: "已完成", icon: Star, path: "/studio/projects?status=done" },
      { key: "templates", label: "模板库", icon: Shapes, path: "/studio/templates" }
    ]
  },
  {
    workspace: "assets",
    label: "资产",
    icon: Images,
    path: "/studio/assets",
    children: [
      { key: "works", label: "作品", icon: Images, path: "/studio/assets/works", default: true },
      { key: "materials", label: "素材", icon: Box, path: "/studio/assets/materials" },
      { key: "prompts", label: "提示词", icon: Tag, path: "/studio/assets/prompts" },
      { key: "history", label: "任务历史", icon: History, path: "/studio/assets/history" }
    ]
  },
  {
    workspace: "knowledge",
    label: "知识",
    icon: BookOpen,
    path: "/studio/knowledge",
    children: [
      { key: "docs", label: "文档", icon: FileText, path: "/studio/knowledge/docs", default: true },
      { key: "bases", label: "知识库", icon: BookOpen, path: "/studio/knowledge/bases" },
      { key: "favorites", label: "收藏", icon: Heart, path: "/studio/knowledge/favorites" }
    ]
  },
  {
    workspace: "tools",
    label: "工具",
    icon: Wrench,
    path: "/studio/create/tools",
    children: [
      { key: "tools", label: "工具", icon: Wrench, path: "/studio/create/tools", default: true }
    ]
  },
  {
    workspace: "me",
    label: "我的",
    icon: User,
    path: "/studio/me",
    children: [
      { key: "account", label: "账号", icon: CircleUser, path: "/studio/me/account", default: true },
      { key: "membership", label: "会员", icon: CircleDollarSign, path: "/studio/me/membership" },
      { key: "credits", label: "积分", icon: Mic, path: "/studio/me/credits" },
      { key: "invite", label: "邀请", icon: Gift, path: "/studio/me/invite" },
      { key: "outfits", label: "装扮", icon: Palette, path: "/studio/me/outfits" },
      { key: "settings", label: "设置", icon: Settings, path: "/studio/me/settings" }
    ]
  }
]

/** 通过 workspace key 取配置 */
export function getWorkspaceConfig(workspace: StudioWorkspace): StudioWorkspaceConfig {
  const found = STUDIO_NAV.find((w) => w.workspace === workspace)
  if (!found) throw new Error(`Unknown studio workspace: ${workspace}`)
  return found
}

/** 通过 pathname 反查 workspace（截 /studio/{workspace}/...） */
export function resolveWorkspaceFromPath(pathname: string): StudioWorkspace | null {
  // /studio 根路径 → home
  if (pathname === "/studio" || pathname === "/studio/") return "home"
  const match = pathname.match(/^\/studio\/([^/?#]+)/)
  if (!match) return null
  const segment = match[1]
  // welcome 动画页归 home
  if (segment === "welcome") return "home"
  // 模板库归项目工作区
  if (segment === "templates") return "projects"
  // 工具箱路径归 tools 工作区
  if (pathname.startsWith("/studio/create/tools") || pathname.startsWith("/studio/create/draw")) return "tools"
  // chat 不归任何工作区（独立全屏）
  if (segment === "chat") return null
  const valid = STUDIO_NAV.find((w) => w.workspace === segment)
  return (valid?.workspace ?? null) as StudioWorkspace | null
}
