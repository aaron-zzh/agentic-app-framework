/**
 * 操作意图映射器
 * 正向：自然语言意图 → 组件操作序列
 * 反向：用户操作序列 → 推断意图
 * @author AaronZZH & Kiro
 */

import type { IntentAction, IntentRule, UserAction } from "../types"

/** 内置意图规则 */
const intentRules: IntentRule[] = [
  {
    intent: "create_record",
    description: "创建新记录",
    patterns: ["新建", "创建", "添加", "新增", "create", "add", "new"],
    actions: [{ componentId: "list-toolbar", action: "create", params: {} }],
    contextRequirements: { view: "list" }
  },
  {
    intent: "delete_record",
    description: "删除记录",
    patterns: ["删除", "移除", "清除", "delete", "remove"],
    actions: [{ componentId: "list-toolbar", action: "delete", params: {} }],
    contextRequirements: { hasSelection: true }
  },
  {
    intent: "search",
    description: "搜索记录",
    patterns: ["搜索", "查找", "找", "search", "find", "look for"],
    actions: [{ componentId: "search-bar", action: "focus", params: {} }]
  },
  {
    intent: "filter",
    description: "筛选数据",
    patterns: ["筛选", "过滤", "只看", "filter", "show only"],
    actions: [{ componentId: "filter-builder", action: "open", params: {} }]
  },
  {
    intent: "export",
    description: "导出数据",
    patterns: ["导出", "下载", "export", "download"],
    actions: [{ componentId: "list-toolbar", action: "export", params: {} }]
  },
  {
    intent: "save",
    description: "保存当前编辑",
    patterns: ["保存", "提交", "save", "submit"],
    actions: [{ componentId: "form-header", action: "save", params: {} }],
    contextRequirements: { view: "form" }
  },
  {
    intent: "navigate_back",
    description: "返回列表",
    patterns: ["返回", "回去", "back", "go back"],
    actions: [{ componentId: "breadcrumb", action: "navigateBack", params: {} }]
  },
  {
    intent: "switch_view",
    description: "切换视图",
    patterns: ["切换到", "看板", "列表", "日历", "switch to", "kanban", "calendar"],
    actions: [{ componentId: "view-switcher", action: "switch", params: {} }]
  }
]

/** 操作序列 → 意图的反向映射模式 */
const reversePatterns: { sequence: UserAction["type"][]; intent: string }[] = [
  { sequence: ["click", "input", "submit"], intent: "create_record" },
  { sequence: ["select", "click"], intent: "delete_record" },
  { sequence: ["input", "filter"], intent: "search" },
  { sequence: ["click", "navigate"], intent: "navigate_detail" },
  { sequence: ["edit", "submit"], intent: "save" },
  { sequence: ["search", "click"], intent: "find_and_open" }
]

/**
 * 历史意图频率（用于消歧和学习）
 * 注意：模块级 Map，无上限控制。实际场景中意图种类有限（<50），不会无限增长。
 */
const intentHistory = new Map<string, number>()

class IntentMapperImpl {
  /** 正向映射：自然语言 → 操作序列 */
  mapIntentToActions(
    text: string,
    context?: { entity?: string; view?: string; hasSelection?: boolean }
  ): { intent: string; actions: IntentAction[]; confidence: number } | null {
    const normalized = text.toLowerCase().trim()

    const matches = intentRules
      .filter((rule) => {
        // 检查上下文要求
        if (rule.contextRequirements) {
          if (rule.contextRequirements.view && context?.view !== rule.contextRequirements.view)
            return false
          if (rule.contextRequirements.hasSelection && !context?.hasSelection) return false
        }
        return rule.patterns.some((p) => normalized.includes(p))
      })
      .map((rule) => {
        // 计算匹配置信度
        const patternMatch = rule.patterns.filter((p) => normalized.includes(p)).length
        const historyBoost = (intentHistory.get(rule.intent) ?? 0) * 0.01
        return {
          intent: rule.intent,
          actions: rule.actions,
          confidence: Math.min(0.5 + patternMatch * 0.2 + historyBoost, 1.0)
        }
      })
      .sort((a, b) => b.confidence - a.confidence)

    return matches[0] ?? null
  }

  /** 反向映射：操作序列 → 推断意图 */
  inferIntent(actions: UserAction[]): { intent: string; confidence: number } | null {
    if (actions.length === 0) return null

    const types = actions.map((a) => a.type)

    for (const pattern of reversePatterns) {
      if (this.matchSequence(types, pattern.sequence)) {
        return { intent: pattern.intent, confidence: 0.7 }
      }
    }

    // 单操作推断
    const last = actions[actions.length - 1]
    if (last.type === "navigate") return { intent: "navigate", confidence: 0.9 }
    if (last.type === "search") return { intent: "search", confidence: 0.9 }
    if (last.type === "filter") return { intent: "filter", confidence: 0.9 }

    return null
  }

  /** 记录意图使用（用于学习优化） */
  recordIntentUsage(intent: string): void {
    intentHistory.set(intent, (intentHistory.get(intent) ?? 0) + 1)
  }

  /** 获取所有已注册意图规则 */
  getRules(): IntentRule[] {
    return intentRules
  }

  /** 添加自定义意图规则 */
  addRule(rule: IntentRule): void {
    intentRules.push(rule)
  }

  /** 序列匹配（子序列包含） */
  private matchSequence(actual: string[], pattern: string[]): boolean {
    let pi = 0
    for (const item of actual) {
      if (item === pattern[pi]) pi++
      if (pi === pattern.length) return true
    }
    return false
  }
}

/** 全局单例 */
export const IntentMapper = new IntentMapperImpl()
