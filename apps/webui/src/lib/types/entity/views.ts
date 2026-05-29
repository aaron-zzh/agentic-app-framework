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

/** 快捷筛选项 */
export interface QuickFilter {
  /** 显示名称 */
  label: string
  /** 筛选字段 */
  field: string
  /** 操作符 */
  operator: string
  /** 筛选值 */
  value: string
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
  /** 快捷筛选（显示在搜索栏旁，点击直接添加条件） */
  quickFilters?: QuickFilter[]
  /** 行内编辑 */
  inlineEdit?: boolean
  /** 批量操作 */
  batchActions?: string[]
  /** 每页条数 */
  pageSize?: number
  /** 允许行拖拽排序 */
  draggable?: boolean
  /** 拖拽排序字段（integer，拖拽完成后更新此字段）；有此字段时才允许开启拖拽 */
  orderField?: string
  /** 分组字段 */
  groupBy?: string
  /** Tab 快捷筛选配置 */
  tabs?: {
    /** 按哪个字段生成 Tab */
    field: string
    /** 显示计数 */
    showCount?: boolean
    /** 自定义 Tab 项（不配则从字段 options 自动生成） */
    items?: { value: string; label: string }[]
  }
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
  /** label 位置：top=上方（默认）| left=左侧（Odoo 风格，紧凑） */
  labelLayout?: "top" | "left"
}

/** 卡片模板配置 */
export interface KanbanCardTemplate {
  /** 展示的字段列表 */
  displayFields?: string[]
  /** 布局模式 */
  layout?: "compact" | "standard" | "detailed"
  /** 封面图片字段 */
  coverField?: string
  /** 条件样式规则 */
  conditionalStyles?: KanbanConditionalStyle[]
  /** 快捷操作按钮 */
  quickActions?: string[]
}

/** 条件样式规则 */
export interface KanbanConditionalStyle {
  /** 匹配字段 */
  field: string
  /** 匹配值 */
  value: string
  /** 应用的 CSS 类名或颜色 */
  color?: string
  /** 边框颜色 */
  borderColor?: string
}

/** WIP 限制模式 */
export type WipLimitMode = "soft" | "hard"

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
  /** 排序字段（integer，拖拽完成后持久化排序值） */
  orderField?: string
  /** 泳道分组字段 */
  swimlaneField?: string
  /** WIP 限制：列值 → 最大卡片数 */
  wipLimits?: Record<string, number>
  /** WIP 限制模式：soft=允许但警告，hard=禁止拖入 */
  wipLimitMode?: WipLimitMode
  /** 自定义卡片模板 */
  cardTemplate?: KanbanCardTemplate
}

/** 布局字段类型 */
export type LayoutField = GroupField | TabsField | RowField

/** 日历视图配置 */
export interface CalendarViewConfig {
  /** 开始时间字段 */
  startField: string
  /** 结束时间字段（可选，无则视为全天事件） */
  endField?: string
  /** 事件标题字段 */
  titleField: string
  /** 事件颜色字段（select 类型，取 option.color） */
  colorField?: string
  /** 是否全天事件字段（boolean 类型） */
  allDayField?: string
  /** 重复规则字段（存储 RRULE 字符串） */
  rruleField?: string
  /** 默认视图 */
  defaultView?: "dayGridMonth" | "timeGridWeek" | "timeGridDay" | "listWeek"
}

/** 透视视图配置 */
export interface PivotViewConfig {
  enabled: boolean
  /** 可用作维度的字段 */
  dimensions: string[]
  /** 可用指标 */
  measures: PivotMeasure[]
  /** 默认透视配置 */
  defaultConfig?: PivotConfig
}

export interface PivotMeasure {
  field: string
  aggregations: ("count" | "sum" | "avg" | "min" | "max")[]
  label?: string
}

export interface PivotConfig {
  rows: string[]
  columns?: string[]
  values: { field: string; aggregation: string }[]
}

/** 画板视图模板类型 */
export type CanvasTemplate = "brainstorm" | "project-plan" | "user-journey" | "blank"

/** 画板视图配置 */
export interface CanvasViewConfig {
  /** 是否启用协作 */
  collaboration?: boolean
  /** 默认模板 */
  defaultTemplate?: CanvasTemplate
  /** 是否启用 AI 辅助 */
  aiAssist?: boolean
  /** 是否显示网格 */
  showGrid?: boolean
  /** 导出格式 */
  exportFormats?: ("png" | "svg" | "pdf")[]
  /** 嵌入的实体字段（用于嵌入实体数据卡片） */
  embedFields?: string[]
}
