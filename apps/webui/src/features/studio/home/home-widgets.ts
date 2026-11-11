/**
 * Studio 首页内置默认布局：按区域列出各面板的 key、标题、所在区域、默认是否显示与能否折叠。
 *
 * 中央画布不在列表中，始终显示。结构保持为纯数据，以后可直接改成由后端预设下发。
 *
 * @example
 * const visibility = resolveHomeWidgetVisibility(hiddenKeys)
 * const rightWidgets = widgetsInRegion("right", visibility)
 * @author AaronZZH & Kiro
 */

/** 首页面板 key */
export type HomeWidgetKey = "resources" | "credits" | "agent-runs" | "create"

/** 面板所在区域：左栏 / 右栏 / 底部区 */
export type HomeWidgetRegion = "left" | "right" | "bottom"

/** 首页面板定义 */
export interface HomeWidgetDef {
  key: HomeWidgetKey
  title: string
  region: HomeWidgetRegion
  defaultVisible: boolean
  /** 能否折叠成一行摘要（右栏面板） */
  collapsible: boolean
}

/** 首页内置默认布局，同一区域内按数组顺序渲染 */
export const HOME_WIDGETS: readonly HomeWidgetDef[] = [
  { key: "resources", title: "我的资源", region: "left", defaultVisible: true, collapsible: false },
  { key: "credits", title: "积分", region: "right", defaultVisible: true, collapsible: true },
  {
    key: "agent-runs",
    title: "智能体运行统计",
    region: "right",
    defaultVisible: true,
    collapsible: true
  },
  // 最近生成条属于创作区，随创作一起显示或隐藏，不单独开关
  { key: "create", title: "创作", region: "bottom", defaultVisible: true, collapsible: false }
]

/** 区域在自定义面板中的名称 */
export const HOME_WIDGET_REGION_LABELS: Record<HomeWidgetRegion, string> = {
  left: "左栏",
  right: "右栏",
  bottom: "底部"
}

/** 首页面板显示状态 */
export type HomeWidgetVisibility = Record<HomeWidgetKey, boolean>

/**
 * 根据用户隐藏的面板 key 计算每个面板是否显示；不在 HOME_WIDGETS 中的 key 被忽略。
 */
export function resolveHomeWidgetVisibility(hiddenKeys: readonly string[]): HomeWidgetVisibility {
  const hidden = new Set(hiddenKeys)
  const visibility = {} as HomeWidgetVisibility
  for (const widget of HOME_WIDGETS) {
    visibility[widget.key] = widget.defaultVisible && !hidden.has(widget.key)
  }
  return visibility
}

/** 某个区域内当前显示的面板，按定义顺序 */
export function widgetsInRegion(
  region: HomeWidgetRegion,
  visibility: HomeWidgetVisibility
): HomeWidgetDef[] {
  return HOME_WIDGETS.filter((widget) => widget.region === region && visibility[widget.key])
}
