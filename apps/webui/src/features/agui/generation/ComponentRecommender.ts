/**
 * 组件推荐引擎
 * 根据上下文、历史使用、常见组合模式推荐组件配置
 * @author AaronZZH & Kiro
 */

import type { EntityDef } from "@/lib/types/entity"

import { SemanticRegistry } from "../semantics"

/** 推荐结果 */
export interface Recommendation {
  id: string
  /** 推荐的视图/组件类型 */
  type: "list" | "form" | "kanban" | "calendar" | "pivot" | "combination"
  /** 推荐标题 */
  title: string
  /** 推荐理由 */
  reason: string
  /** 置信度 0-1 */
  confidence: number
  /** 推荐的特性列表 */
  features: string[]
  /** 关联的实体 slug */
  entity?: string
}

/** 使用历史记录 */
interface UsageRecord {
  entity: string
  viewType: string
  features: string[]
  timestamp: number
}

/** 常见组合模式 */
const combinationPatterns: { name: string; views: string[]; features: string[]; scenario: string }[] = [
  {
    name: "CRUD 标准套件",
    views: ["list", "form"],
    features: ["search", "paginate", "filter", "batch-action"],
    scenario: "标准数据管理场景",
  },
  {
    name: "状态流转套件",
    views: ["list", "kanban", "form"],
    features: ["search", "filter", "drag"],
    scenario: "有状态流转的业务（工单、审批）",
  },
  {
    name: "时间线套件",
    views: ["list", "calendar"],
    features: ["search", "filter", "sort"],
    scenario: "时间维度管理（日程、计划）",
  },
  {
    name: "数据分析套件",
    views: ["list", "pivot"],
    features: ["filter", "sort", "paginate"],
    scenario: "需要聚合统计的数据",
  },
]

/** 使用历史存储 */
const usageHistory: UsageRecord[] = []

class ComponentRecommenderImpl {
  /** 上下文感知推荐 */
  recommendByContext(context: {
    entity?: EntityDef
    currentView?: string
    currentAction?: string
  }): Recommendation[] {
    const recommendations: Recommendation[] = []
    const { entity, currentView, currentAction } = context

    if (!entity) return recommendations

    // 根据字段类型推荐视图
    const hasStatusField = entity.fields.some((f) => f.type === "select")
    const hasDateField = entity.fields.some((f) => f.type === "date")
    const hasNumberField = entity.fields.some((f) => f.type === "number")

    if (hasStatusField && currentView !== "kanban") {
      recommendations.push({
        id: `rec_kanban_${entity.slug}`,
        type: "kanban",
        title: "看板视图",
        reason: `「${entity.label}」包含状态字段，适合用看板管理流转`,
        confidence: 0.8,
        features: ["drag", "filter"],
        entity: entity.slug,
      })
    }

    if (hasDateField && currentView !== "calendar") {
      recommendations.push({
        id: `rec_calendar_${entity.slug}`,
        type: "calendar",
        title: "日历视图",
        reason: `「${entity.label}」包含日期字段，可按时间维度浏览`,
        confidence: 0.7,
        features: ["filter"],
        entity: entity.slug,
      })
    }

    if (hasNumberField) {
      recommendations.push({
        id: `rec_pivot_${entity.slug}`,
        type: "pivot",
        title: "透视分析",
        reason: `「${entity.label}」包含数值字段，可进行聚合统计`,
        confidence: 0.6,
        features: ["filter", "sort"],
        entity: entity.slug,
      })
    }

    // 根据当前操作推荐
    if (currentAction === "create") {
      recommendations.push({
        id: `rec_form_${entity.slug}`,
        type: "form",
        title: "表单视图",
        reason: "当前正在创建记录，推荐使用表单视图",
        confidence: 0.9,
        features: [],
        entity: entity.slug,
      })
    }

    return recommendations.sort((a, b) => b.confidence - a.confidence)
  }

  /** 基于历史使用推荐 */
  recommendByHistory(entity: string): Recommendation[] {
    const entityHistory = usageHistory.filter((r) => r.entity === entity)
    if (entityHistory.length === 0) return []

    // 统计最常用的视图和特性
    const viewCounts = new Map<string, number>()
    const featureCounts = new Map<string, number>()

    for (const record of entityHistory) {
      viewCounts.set(record.viewType, (viewCounts.get(record.viewType) ?? 0) + 1)
      for (const f of record.features) {
        featureCounts.set(f, (featureCounts.get(f) ?? 0) + 1)
      }
    }

    const topView = [...viewCounts.entries()].sort((a, b) => b[1] - a[1])[0]
    const topFeatures = [...featureCounts.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([f]) => f)

    if (!topView) return []

    return [{
      id: `rec_history_${entity}`,
      type: topView[0] as Recommendation["type"],
      title: `常用配置：${topView[0]}视图`,
      reason: `基于历史使用 ${topView[1]} 次，推荐此配置`,
      confidence: Math.min(0.5 + topView[1] * 0.1, 0.95),
      features: topFeatures,
      entity,
    }]
  }

  /** 组合模式推荐 */
  recommendCombinations(entity: EntityDef): Recommendation[] {
    return combinationPatterns
      .filter((pattern) => {
        // 检查语义注册表是否支持
        const semantics = SemanticRegistry.findByCapabilities(pattern.features)
        return semantics.length > 0
      })
      .map((pattern) => ({
        id: `rec_combo_${pattern.name}_${entity.slug}`,
        type: "combination" as const,
        title: pattern.name,
        reason: pattern.scenario,
        confidence: 0.7,
        features: pattern.features,
        entity: entity.slug,
      }))
  }

  /** 记录使用（用于学习） */
  recordUsage(entity: string, viewType: string, features: string[]): void {
    usageHistory.push({ entity, viewType, features, timestamp: Date.now() })
    // 保留最近 100 条
    if (usageHistory.length > 100) usageHistory.shift()
  }
}

/** 全局单例 */
export const ComponentRecommender = new ComponentRecommenderImpl()
