/**
 * AGUI 语义化基础设施——核心类型定义
 * @author AaronZZH & Kiro
 */

/** 用户操作事件 */
export interface UserAction {
  type:
    | "click"
    | "input"
    | "drag"
    | "scroll"
    | "navigate"
    | "submit"
    | "select"
    | "edit"
    | "search"
    | "filter"
  target: string
  value?: unknown
  timestamp: number
  semantics: {
    componentId: string
    semanticRole: string
    entitySlug?: string
    fieldName?: string
  }
  context: {
    page: string
    view: string
    entity?: string
    recordId?: string
  }
  sessionId: string
  inferredIntent?: string
}

/** 组件语义描述 */
export interface ComponentSemantics {
  name: string
  description: string
  category: "view" | "form" | "action" | "navigation" | "display"
  capabilities: string[]
  inputs: {
    name: string
    type: string
    description: string
    required: boolean
  }[]
  outputs: {
    name: string
    type: string
    description: string
    trigger: string
  }[]
  actions: {
    name: string
    description: string
    params?: Record<string, string>
    sideEffects: string[]
    reversible: boolean
  }[]
  constraints: {
    requiredPermissions?: string[]
    maxItems?: number
    validStates?: string[]
  }
}

/** 组件实例（运行时） */
export interface ComponentInstance {
  id: string
  semanticsName: string
  props: Record<string, unknown>
  state: Record<string, unknown>
  children: string[]
}

/** 页面语义 */
export interface PageSemantics {
  route: string
  title: string
  description: string
  currentEntity?: string
  currentRecord?: string
  activeView: string
  availableActions: string[]
  pendingChanges: boolean
  components: ComponentInstance[]
}

/** 组件关系 */
export interface ComponentRelation {
  from: string
  to: string
  type: "data-flow" | "event" | "parent-child"
  description: string
}

/** 意图映射规则 */
export interface IntentRule {
  intent: string
  description: string
  patterns: string[]
  actions: IntentAction[]
  contextRequirements?: {
    entity?: string
    view?: string
    hasSelection?: boolean
  }
}

/** 意图对应的操作 */
export interface IntentAction {
  componentId: string
  action: string
  params?: Record<string, string>
}

/** 行为模式 */
export interface BehaviorPattern {
  id: string
  sequence: string[]
  frequency: number
  avgDuration: number
  lastSeen: number
}

/** 异常事件 */
export interface AnomalyEvent {
  type: "error_spike" | "unusual_path" | "repeated_failure" | "slow_interaction"
  severity: "low" | "medium" | "high"
  description: string
  timestamp: number
  context: Record<string, unknown>
}

/** 优化建议 */
export interface OptimizationSuggestion {
  type: "shortcut" | "reorder" | "simplify" | "automate"
  target: string
  description: string
  confidence: number
  evidence: string[]
}
