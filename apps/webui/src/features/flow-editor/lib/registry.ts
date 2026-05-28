/**
 * 节点注册表工厂——管理节点类型的注册和查找
 * @author AaronZZH & Kiro
 */

import type { NodeTypeRegistry, NodeTypeDef, NodeCategory, FlowMode } from "../types"

/** 创建空注册表 */
export function createNodeRegistry(): NodeTypeRegistry {
  return {}
}

/** 注册节点类型 */
export function registerNodeType(
  registry: NodeTypeRegistry,
  type: string,
  def: NodeTypeDef
): NodeTypeRegistry {
  return { ...registry, [type]: def }
}

/** 按分类获取节点类型 */
export function getNodesByCategory(
  registry: NodeTypeRegistry,
  category: NodeCategory
): Array<{ type: string; def: NodeTypeDef }> {
  return Object.entries(registry)
    .filter(([, def]) => def.category === category)
    .map(([type, def]) => ({ type, def }))
}

/** 获取所有分类 */
export function getAllCategories(registry: NodeTypeRegistry): NodeCategory[] {
  const categories = new Set<NodeCategory>()
  for (const def of Object.values(registry)) {
    categories.add(def.category)
  }
  return Array.from(categories)
}

/** 分类标签映射 */
export const categoryLabels: Record<NodeCategory, string> = {
  trigger: "触发",
  ai: "AI",
  logic: "逻辑",
  data: "数据",
  tool: "工具",
  output: "输出",
  interact: "交互"
}

/** 根据模式获取默认注册表 */
export function getRegistryForMode(mode: FlowMode): NodeTypeRegistry {
  switch (mode) {
    case "approval":
      return approvalRegistry
    case "workflow":
      return workflowRegistry
    case "chatbot":
      return chatbotRegistry
  }
}

// 延迟导入避免循环依赖，由各节点集注册后填充
export let approvalRegistry: NodeTypeRegistry = {}
export let workflowRegistry: NodeTypeRegistry = {}
export let chatbotRegistry: NodeTypeRegistry = {}

export function setApprovalRegistry(r: NodeTypeRegistry) {
  approvalRegistry = r
}
export function setWorkflowRegistry(r: NodeTypeRegistry) {
  workflowRegistry = r
}
export function setChatbotRegistry(r: NodeTypeRegistry) {
  chatbotRegistry = r
}
