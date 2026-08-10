/**
 * Studio 产品导航配置
 *
 * 七个顶级工作区：首页 / 创作 / 作品 / 项目 / 知识 / 工具 / 我的。
 * 子菜单按当前产品信息架构配置，不设固定数量上限。
 * 详见 docs/design/apps/content-studio/content-studio-capability-concept-map.md
 */

import {
  BookOpen,
  Box,
  CircleDollarSign,
  CircleUser,
  FileText,
  FolderKanban,
  FolderTree,
  Gift,
  Heart,
  History,
  Home,
  Image as ImageIcon,
  Images,
  Layers,
  LayoutGrid,
  type LucideIcon,
  Mic,
  Palette,
  Scissors,
  Settings,
  Shapes,
  Sparkles,
  Star,
  Tag,
  TrendingUp,
  User,
  Wand2,
  Wrench,
  Zap
} from "lucide-react"

export type StudioWorkspace =
  | "home"
  | "create"
  | "projects"
  | "assets"
  | "knowledge"
  | "me"
  | "tools"

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
  /** 当前工作区的子菜单列表 */
  children: StudioNavItem[]
}

/**
 * Studio 导航配置——单一真理源
 *
 * 顺序：首页 / 创作 / 作品 / 项目 / 知识 / 工具 / 我的
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
      { key: "overview", label: "素材生成", icon: Sparkles, path: "/studio/create", default: true },
      { key: "copy", label: "文案", icon: Wand2, path: "/studio/create/copy" },
      { key: "viral", label: "爆款", icon: Zap, path: "/studio/create/viral", badge: "热" },
      // { key: "pipeline", label: "工作流", icon: Workflow, path: "/studio/create/pipeline", badge: "新" },
      { key: "matting", label: "抠图", icon: Scissors, path: "/studio/create/matting" },
      { key: "tools", label: "工具箱", icon: Wrench, path: "/studio/create/tools", badge: "新" }
    ]
  },
  {
    workspace: "assets",
    label: "作品",
    icon: Images,
    path: "/studio/assets",
    children: [
      { key: "works", label: "作品", icon: Images, path: "/studio/assets/works", default: true },
      { key: "materials", label: "素材", icon: ImageIcon, path: "/studio/assets/materials" },
      { key: "library", label: "资产", icon: Box, path: "/studio/assets/library" },
      {
        key: "categories",
        label: "资产分类",
        icon: FolderTree,
        path: "/studio/assets/categories"
      },
      { key: "tags", label: "资产标签", icon: Tag, path: "/studio/assets/tags" },
      {
        key: "collections",
        label: "资产集合",
        icon: LayoutGrid,
        path: "/studio/assets/collections"
      },
      { key: "prompts", label: "提示词", icon: Tag, path: "/studio/assets/prompts" },
      { key: "history", label: "任务历史", icon: History, path: "/studio/assets/history" }
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
        path: "/studio/projects",
        default: true
      },
      {
        key: "active",
        label: "进行中",
        icon: TrendingUp,
        path: "/studio/projects?status=in_progress"
      },
      {
        key: "done",
        label: "已完成",
        icon: Star,
        path: "/studio/projects?status=completed"
      },
      { key: "brands", label: "品牌 · IP", icon: CircleUser, path: "/studio/brands" },
      { key: "templates", label: "模板库", icon: Shapes, path: "/studio/templates" }
    ]
  },
  {
    workspace: "knowledge",
    label: "知识",
    icon: BookOpen,
    path: "/studio/knowledge",
    children: [
      { key: "bases", label: "知识库", icon: BookOpen, path: "/studio/knowledge", default: true },
      { key: "docs", label: "文档", icon: FileText, path: "/studio/knowledge/docs" },
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
      {
        key: "account",
        label: "账号",
        icon: CircleUser,
        path: "/studio/me/account",
        default: true
      },
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
  // 模板库与品牌资料归项目工作区
  if (segment === "templates" || segment === "brands") return "projects"
  // 工具箱路径归 tools 工作区
  if (pathname.startsWith("/studio/create/tools") || pathname.startsWith("/studio/create/draw"))
    return "tools"
  // chat 不归任何工作区（独立全屏）
  if (segment === "chat") return null
  const valid = STUDIO_NAV.find((w) => w.workspace === segment)
  return (valid?.workspace ?? null) as StudioWorkspace | null
}
