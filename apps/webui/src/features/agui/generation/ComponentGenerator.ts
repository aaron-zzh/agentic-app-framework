/**
 * AI 动态生成 UI 组件引擎
 * 意图 → 语义注册表查询 → 参数推断 → EntityDef 配置生成
 * @author AaronZZH & Kiro
 */

import type {
  DataFieldDef,
  EntityDef,
  FieldDef,
  FormViewConfig,
  KanbanViewConfig,
  ListViewConfig
} from "@/lib/types/entity"

import { IntentMapper } from "../intent"
import { SemanticRegistry } from "../semantics"

/** 生成意图（从自然语言解析） */
export interface GenerationIntent {
  type: "generate-view" | "generate-form" | "generate-kanban" | "generate-dashboard"
  entity: string
  features: string[]
  fields?: string[]
  title?: string
}

/** 生成结果 */
export interface GenerationResult {
  id: string
  intent: GenerationIntent
  config: Partial<EntityDef>
  timestamp: number
  preview?: boolean
}

/** 视图特性 → 配置映射 */
const featureMapping: Record<string, (config: Partial<ListViewConfig>) => void> = {
  search: (c) => {
    c.searchableFields = c.searchableFields ?? []
  },
  paginate: (c) => {
    c.pageSize = c.pageSize ?? 20
  },
  filter: (c) => {
    c.filterableFields = c.filterableFields ?? []
  },
  sort: (c) => {
    c.defaultSort = c.defaultSort ?? "createdAt:desc"
  },
  "inline-edit": (c) => {
    c.inlineEdit = true
  },
  "batch-action": (c) => {
    c.batchActions = c.batchActions ?? ["delete"]
  },
  drag: (c) => {
    c.draggable = true
  }
}

/** 意图关键词 → 视图类型 */
const viewKeywords: Record<string, GenerationIntent["type"]> = {
  列表: "generate-view",
  表格: "generate-view",
  list: "generate-view",
  table: "generate-view",
  表单: "generate-form",
  编辑: "generate-form",
  form: "generate-form",
  看板: "generate-kanban",
  kanban: "generate-kanban",
  仪表盘: "generate-dashboard",
  dashboard: "generate-dashboard"
}

/** 特性关键词 */
const featureKeywords: Record<string, string> = {
  搜索: "search",
  分页: "paginate",
  筛选: "filter",
  排序: "sort",
  行内编辑: "inline-edit",
  批量操作: "batch-action",
  拖拽: "drag",
  search: "search",
  paginate: "paginate",
  filter: "filter",
  sort: "sort"
}

class ComponentGeneratorImpl {
  /** 从自然语言解析生成意图 */
  parseIntent(text: string): GenerationIntent {
    const normalized = text.toLowerCase()

    // 解析视图类型
    let type: GenerationIntent["type"] = "generate-view"
    for (const [keyword, viewType] of Object.entries(viewKeywords)) {
      if (normalized.includes(keyword)) {
        type = viewType
        break
      }
    }

    // 解析实体名称（取第一个名词性词汇作为实体）
    const entityPatterns = /(?:一个|个)?([\u4e00-\u9fa5\w]+?)(?:列表|表格|表单|看板|页面|管理)/
    const entityMatch = text.match(entityPatterns)
    const entity = entityMatch?.[1] ?? "item"

    // 解析特性
    const features: string[] = []
    for (const [keyword, feature] of Object.entries(featureKeywords)) {
      if (normalized.includes(keyword)) {
        features.push(feature)
      }
    }

    return { type, entity, features }
  }

  /** 根据 EntityDef 推断组件参数 */
  inferParams(intent: GenerationIntent, entityDef?: EntityDef): Partial<EntityDef> {
    const fields = entityDef?.fields ?? this.generateDefaultFields(intent.entity)
    const slug = entityDef?.slug ?? intent.entity
    const label = entityDef?.label ?? intent.entity

    switch (intent.type) {
      case "generate-view":
        return this.buildListConfig(slug, label, fields, intent.features)
      case "generate-form":
        return this.buildFormConfig(slug, label, fields)
      case "generate-kanban":
        return this.buildKanbanConfig(slug, label, fields)
      default:
        return { slug, label, apiPath: `/api/${slug}`, fields }
    }
  }

  /** 生成完整配置 */
  generate(text: string, entityDef?: EntityDef): GenerationResult {
    const intent = this.parseIntent(text)
    const config = this.inferParams(intent, entityDef)

    // 查询语义注册表验证组件能力
    const semantics = SemanticRegistry.findByCapabilities(intent.features)
    if (semantics.length > 0) {
      IntentMapper.recordIntentUsage(intent.type)
    }

    return {
      id: `gen_${Date.now()}`,
      intent,
      config,
      timestamp: Date.now(),
      preview: true
    }
  }

  /** 增量更新配置 */
  update(existing: GenerationResult, modification: string): GenerationResult {
    const newIntent = this.parseIntent(modification)
    const merged: GenerationIntent = {
      ...existing.intent,
      type: newIntent.type !== "generate-view" ? newIntent.type : existing.intent.type,
      features: [...new Set([...existing.intent.features, ...newIntent.features])]
    }

    const config = this.inferParams(merged)
    return {
      id: existing.id,
      intent: merged,
      config: { ...existing.config, ...config },
      timestamp: Date.now(),
      preview: true
    }
  }

  /** 构建列表视图配置 */
  private buildListConfig(
    slug: string,
    label: string,
    fields: FieldDef[],
    features: string[]
  ): Partial<EntityDef> {
    const dataFields = fields.filter((f): f is DataFieldDef => "name" in f)
    const listView: ListViewConfig = {
      columns: dataFields.slice(0, 6).map((f) => f.name)
    }

    // 应用特性
    for (const feature of features) {
      featureMapping[feature]?.(listView)
    }

    // 自动填充搜索字段
    if (listView.searchableFields?.length === 0) {
      listView.searchableFields = dataFields
        .filter((f) => f.type === "text" || f.type === "textarea")
        .map((f) => f.name)
    }

    // 自动填充筛选字段
    if (listView.filterableFields?.length === 0) {
      listView.filterableFields = dataFields
        .filter((f) => f.type === "select" || f.type === "date")
        .map((f) => f.name)
    }

    return {
      slug,
      label,
      apiPath: `/api/${slug}`,
      fields,
      listView
    }
  }

  /** 构建表单视图配置 */
  private buildFormConfig(slug: string, label: string, fields: FieldDef[]): Partial<EntityDef> {
    const dataFields = fields.filter((f): f is DataFieldDef => "name" in f)
    const formView: FormViewConfig = {
      autosave: { enabled: true, debounceMs: 2000 }
    }

    return {
      slug,
      label,
      apiPath: `/api/${slug}`,
      fields,
      listView: { columns: dataFields.slice(0, 4).map((f) => f.name) },
      formView
    }
  }

  /** 构建看板视图配置 */
  private buildKanbanConfig(slug: string, label: string, fields: FieldDef[]): Partial<EntityDef> {
    const dataFields = fields.filter((f): f is DataFieldDef => "name" in f)
    const statusField = dataFields.find((f) => f.type === "select")
    const titleField = dataFields.find((f) => f.type === "text")

    const kanbanView: KanbanViewConfig = {
      statusField: statusField?.name ?? "status",
      cardTitle: titleField?.name ?? "title"
    }

    return {
      slug,
      label,
      apiPath: `/api/${slug}`,
      fields,
      listView: { columns: dataFields.slice(0, 4).map((f) => f.name) },
      kanbanView
    }
  }

  /** 生成默认字段（无 EntityDef 时的 fallback） */
  private generateDefaultFields(_entity: string): FieldDef[] {
    return [
      { name: "title", type: "text", label: "标题", required: true },
      {
        name: "status",
        type: "select",
        label: "状态",
        options: [
          { label: "草稿", value: "draft", color: "gray" },
          { label: "进行中", value: "active", color: "blue" },
          { label: "已完成", value: "done", color: "green" }
        ]
      },
      { name: "description", type: "textarea", label: "描述" },
      { name: "createdAt", type: "date", label: "创建时间", readOnly: true }
    ]
  }
}

/** 全局单例 */
export const ComponentGenerator = new ComponentGeneratorImpl()
