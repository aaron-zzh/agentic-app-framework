import type { ComponentType } from "react"

import type { EntityAccess } from "./access"
import type { FieldDef } from "./field"
import type { FormViewConfig, KanbanViewConfig, ListViewConfig } from "./views"

/** 实体完整定义——配置驱动视图引擎的核心契约 */
export interface EntityDef {
  /** URL 路径 + 唯一标识 */
  slug: string
  /** 显示名称 */
  label: string
  /** 复数名称 */
  labelPlural?: string
  /** 后端 API 路径 */
  apiPath: string
  /** 侧边栏图标（lucide 图标名） */
  icon?: string
  /** 侧边栏分组 */
  group?: string
  /** 描述 */
  description?: string

  /** 字段定义 */
  fields: FieldDef[]

  /** 列表视图配置 */
  listView: ListViewConfig
  /** 表单视图配置 */
  formView?: FormViewConfig
  /** 看板视图配置 */
  kanbanView?: KanbanViewConfig

  /** 权限配置 */
  access?: EntityAccess

  /** 实体级动作 */
  actions?: EntityAction[]

  /** 生命周期钩子 */
  hooks?: EntityHooks

  /** 自定义视图覆盖 */
  overrides?: {
    listView?: ComponentType
    formView?: ComponentType
    kanbanView?: ComponentType
  }

  /** Mixin 名称列表 */
  mixins?: string[]
  /** 继承的父实体 slug */
  extends?: string
}

/** 实体动作（Server Action 触发） */
export interface EntityAction {
  key: string
  label: string
  icon?: string
  type: "single" | "batch"
  /** POST /api/{entity}/actions/{key} */
  endpoint: string
  confirmMessage?: string
  position: "formHeader" | "listToolbar" | "rowAction" | "contextMenu"
}

/** 生命周期钩子 */
export interface EntityHooks {
  beforeCreate?: (
    data: Record<string, unknown>
  ) => Record<string, unknown> | Promise<Record<string, unknown>>
  afterCreate?: (record: Record<string, unknown>) => void
  beforeUpdate?: (id: string, data: Record<string, unknown>) => Record<string, unknown>
  afterUpdate?: (record: Record<string, unknown>) => void
  beforeDelete?: (ids: string[]) => boolean
  afterDelete?: (ids: string[]) => void
  beforeView?: (record: Record<string, unknown>) => Record<string, unknown>
}
