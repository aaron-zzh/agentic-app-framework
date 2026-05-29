/**
 * AGUI 语义化基础设施
 * 提供用户操作感知、组件语义描述、AI 上下文、意图映射、行为分析、组件生成六大能力
 * @author AaronZZH & Kiro
 */

// 类型
export type * from "./types"

// #8101 用户操作感知埋点
export { TrackingProvider, useTracking } from "./tracking"

// #8102 组件语义化元数据
export { SemanticRegistry } from "./semantics"

// #8103 AI 可理解的组件描述
export {
  collectPageSemantics,
  getComponentRelations,
  registerComponent,
  registerRelation,
  resetPageSemantics,
  setPageMeta,
  unregisterComponent,
  updateComponentState,
  usePageSemantics
} from "./ai-context"

// #8104 操作意图映射
export { IntentMapper } from "./intent"

// #8105 行为数据分析
export {
  AnomalyDetector,
  HeatmapCollector,
  OptimizationSuggester,
  PatternDetector
} from "./analytics"

// #8201-#8205 AI 组件生成
export {
  ComponentGenerator,
  ComponentRecommender,
  GenerationHistory,
  LayoutOptimizer,
  useConversationalBuilder,
  type BuilderMessage,
  type GenerationIntent,
  type GenerationResult,
  type GenerationTemplate,
  type HistoryEntry,
  type LayoutProposal,
  type LayoutDescriptor,
  type MarketplaceFilter,
  type Recommendation,
  type ResponsiveConfig,
  type TemplateVersion,
} from "./generation"
