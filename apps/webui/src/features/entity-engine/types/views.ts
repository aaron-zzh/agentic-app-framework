/**
 * 视图配置类型——列表/表单/看板视图的配置接口
 * @author AaronZZH & Kiro
 */

import type { GroupField, RowField, TabsField } from "./field"

/** 列定义 */
export interface ColumnDef {
  /** 字段 name */
  name: string
  /** 列宽（px 或百分比） */
  width?: string
  /** 固定列（left/right） */
  fixed?: "left" | "right"
  /** 可排序 */
  sortable?: boolean
  /** 可调整宽度 */
  resizable?: boolean
  /** 默认隐藏 */
  hidden?: boolean
}

/** 列表视图配置 */
export interface ListViewConfig {
  /** 显示的列（字段 name 或完整列定义） */
  columns: (string | ColumnDef)[]
  /** 默认排序："fieldName:asc|desc" */
  defaultSort?: string
  /** 可搜索字段 */
  searchableFields?: string[]
  /** 可筛选字段 */
  filterableFields?: string[]
  /** 行内编辑 */
  inlineEdit?: boolean
  /** 批量操作 */
  batchActions?: string[]
  /** 每页条数 */
  pageSize?: number
  /** 允许行拖拽排序 */
  draggable?: boolean
  /** 分组字段 */
  groupBy?: string
}

/** 表单视图配置 */
export interface FormViewConfig {
  /** 布局（tabs/group/row） */
  layout?: LayoutField[]
  /** 自动保存 */
  autosave?: {
    enabled: boolean
    debounceMs?: number
  }
}

/** 看板视图配置 */
export interface KanbanViewConfig {
  /** 用于分列的字段 name */
  statusField: string
  /** 卡片标题字段 */
  cardTitle: string
  /** 卡片描述字段 */
  cardDescription?: string
  /** 卡片头像字段 */
  cardAvatar?: string
  /** 列显示顺序（用户可拖拽调整，不影响 options 定义） */
  columnOrder?: string[]
}

/** 布局字段类型 */
export type LayoutField = GroupField | TabsField | RowField
