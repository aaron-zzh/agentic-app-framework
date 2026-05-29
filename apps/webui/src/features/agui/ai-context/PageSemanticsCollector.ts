/**
 * 页面语义收集器
 * 收集当前页面的完整语义状态，供 AI 感知层消费
 * @author AaronZZH & Kiro
 */

import type { ComponentInstance, ComponentRelation, PageSemantics } from "../types"

/** 已注册的组件实例（运行时动态维护） */
const componentInstances = new Map<string, ComponentInstance>()

/** 组件关系图 */
const componentRelations: ComponentRelation[] = []

/** 页面元数据（由路由层设置） */
let currentPageMeta: {
  route: string
  title: string
  description: string
  entity?: string
  recordId?: string
  view: string
} = { route: "", title: "", description: "", view: "list" }

/** 设置当前页面元数据 */
export function setPageMeta(meta: typeof currentPageMeta): void {
  currentPageMeta = meta
}

/** 注册组件实例 */
export function registerComponent(instance: ComponentInstance): void {
  componentInstances.set(instance.id, instance)
}

/** 注销组件实例 */
export function unregisterComponent(id: string): void {
  componentInstances.delete(id)
}

/** 注册组件关系 */
export function registerRelation(relation: ComponentRelation): void {
  componentRelations.push(relation)
}

/** 更新组件状态 */
export function updateComponentState(id: string, state: Record<string, unknown>): void {
  const instance = componentInstances.get(id)
  if (instance) {
    instance.state = { ...instance.state, ...state }
  }
}

/** 收集当前页面完整语义 */
export function collectPageSemantics(): PageSemantics {
  const components = Array.from(componentInstances.values())

  // 从组件状态推断可用操作
  const availableActions: string[] = []
  for (const comp of components) {
    if (comp.state["canCreate"]) availableActions.push("create")
    if (comp.state["canDelete"]) availableActions.push("delete")
    if (comp.state["canEdit"]) availableActions.push("edit")
    if (comp.state["canExport"]) availableActions.push("export")
  }

  // 检测是否有未保存变更
  const pendingChanges = components.some((c) => c.state["isDirty"] === true)

  return {
    route: currentPageMeta.route,
    title: currentPageMeta.title,
    description: currentPageMeta.description,
    currentEntity: currentPageMeta.entity,
    currentRecord: currentPageMeta.recordId,
    activeView: currentPageMeta.view,
    availableActions: [...new Set(availableActions)],
    pendingChanges,
    components
  }
}

/** 获取组件关系图 */
export function getComponentRelations(): ComponentRelation[] {
  return componentRelations
}

/** 清空（页面切换时调用） */
export function resetPageSemantics(): void {
  componentInstances.clear()
  componentRelations.length = 0
}
