/**
 * AI 可理解的组件描述模块
 * @author AaronZZH & Kiro
 */
export {
  collectPageSemantics,
  getComponentRelations,
  registerComponent,
  registerRelation,
  resetPageSemantics,
  setPageMeta,
  unregisterComponent,
  updateComponentState
} from "./PageSemanticsCollector"
export { usePageSemantics } from "./usePageSemantics"
