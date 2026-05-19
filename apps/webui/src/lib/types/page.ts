/**
 * PageEngine 类型定义——配置驱动的营销页/落地页渲染引擎
 * @author AaronZZH & Kiro
 */

import type { ComponentType } from "react"

/** 页面元数据（SEO） */
export interface PageMetadata {
  title: string
  description?: string
  keywords?: string[]
  ogImage?: string
}

/** 页面主题配置 */
export interface PageTheme {
  /** 主色调（CSS 变量或 OKLCH 值） */
  primaryColor?: string
  /** 背景色 */
  backgroundColor?: string
  /** 字体族 */
  fontFamily?: string
  /** 自定义 CSS 类名 */
  className?: string
  /** 深色模式：true=强制深色，false=强制浅色，'system'=跟随系统 */
  darkMode?: boolean | "system"
}

/** Section 样式配置 */
export interface SectionStyle {
  /** 内边距 */
  padding?: string
  /** 背景色 */
  backgroundColor?: string
  /** 最大宽度 */
  maxWidth?: string
  /** 自定义 CSS 类名 */
  className?: string
  /** 是否全宽（忽略 maxWidth） */
  fullWidth?: boolean
  /** 滚动动效 */
  animation?: "fadeIn" | "slideUp" | "slideLeft" | "slideRight" | "scaleIn" | "none"
}

/** Section 定义 */
export interface SectionDef {
  /** Section 唯一 ID（页面内唯一） */
  id: string
  /** Section 类型（对应注册表中的 key） */
  type: string
  /** 传递给 Section 组件的 props */
  props: Record<string, unknown>
  /** 样式配置 */
  style?: SectionStyle
}

/** 页面定义 */
export interface PageDef {
  /** 页面 slug（URL 路径） */
  slug: string
  /** 页面标题 */
  title: string
  /** SEO 元数据 */
  metadata?: PageMetadata
  /** 页面主题 */
  theme?: PageTheme
  /** Section 列表（按顺序渲染） */
  sections: SectionDef[]
}

/** Section 组件 Props 契约 */
export interface SectionComponentProps<T = Record<string, unknown>> {
  /** Section 定义中的 props */
  data: T
  /** Section 样式 */
  style?: SectionStyle
}

/** Section 组件注册信息 */
export interface SectionRegistryEntry {
  /** 组件 */
  component: ComponentType<SectionComponentProps>
  /** 显示名称（编辑器用） */
  label: string
  /** 图标（编辑器用） */
  icon?: string
}

/** 后端存储的 PageDef 记录 */
export interface PageDefRecord {
  id: string
  slug: string
  title: string
  config: PageDef
  status: "draft" | "published"
  version: number
  createdAt: string
  updatedAt: string
}
