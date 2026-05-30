/**
 * AGUI 组件生成模块
 * 提供 AI 动态生成 UI、对话式搭建、组件推荐、布局优化、历史模板五大能力
 * @author AaronZZH & Kiro
 */

export {
  ComponentGenerator,
  ComponentGeneratorImpl,
  type GenerationIntent,
  type GenerationResult
} from "./ComponentGenerator"
export { ComponentRecommender, type Recommendation } from "./ComponentRecommender"
export { type BuilderMessage, useConversationalBuilder } from "./ConversationalBuilder"
export {
  GenerationHistory,
  type GenerationTemplate,
  type HistoryEntry,
  type MarketplaceFilter,
  type TemplateVersion
} from "./GenerationHistory"
export {
  type LayoutDescriptor,
  LayoutOptimizer,
  type LayoutProposal,
  type ResponsiveConfig
} from "./LayoutOptimizer"
