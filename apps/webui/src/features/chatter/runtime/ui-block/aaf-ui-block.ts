/**
 * AafUiBlock v1——服务端可安全投影到对话中的结构化展示卡片协议。
 *
 * 设计边界（AAF-114 #11408 第二版收窄）：
 * - 只保留 `INFO_CARD`——纯只读展示，唯一生成点在服务端 `render_ui_block` 工具，前端只做渲染。
 * - 原 `CHOICE`/`FORM` 已移除：需要人类响应的 Clarification 场景改为复用标准 AG-UI
 *   interrupt/resume 协议（见 design.md #11408 详细设计），不再由本协议承载可提交表单，
 *   避免维护两套并行的"需要人类介入"机制。
 * - 本文件是唯一协议真理源：字段新增/变更必须先改这里，再改投影适配器与渲染器。
 *
 * @author AaronZZH & Kiro
 */

/** INFO_CARD 展示条目。 */
export interface AafUiBlockInfoItem {
  label: string
  value: string
}

export interface AafUiBlock {
  version: 1
  id: string
  type: "INFO_CARD"
  title: string
  description?: string
  items?: AafUiBlockInfoItem[]
}

/** 本前端实现已知的最高协议版本；未来升级 v2 时在此处一并放宽解析。 */
const KNOWN_VERSION = 1

/**
 * 运行时校验：把未知输入收窄为 {@link AafUiBlock}，失败时返回 null。
 *
 * 只做结构性校验（字段是否存在、类型是否匹配），不做业务语义校验——业务语义由渲染时读取的
 * 服务端权威状态负责，不在协议校验层重复判断。
 */
export function parseAafUiBlock(value: unknown): AafUiBlock | null {
  if (typeof value !== "object" || value === null) return null
  const v = value as Record<string, unknown>
  if (v.version !== KNOWN_VERSION) return null
  if (typeof v.id !== "string" || !v.id) return null
  if (typeof v.title !== "string") return null
  if (v.type !== "INFO_CARD") return null
  if (!isInfoItemArray(v.items)) return null

  return {
    version: 1,
    id: v.id,
    type: "INFO_CARD",
    title: v.title,
    description: typeof v.description === "string" ? v.description : undefined,
    items: v.items
  }
}

function isInfoItemArray(value: unknown): value is AafUiBlockInfoItem[] {
  if (value === undefined) return true
  if (!Array.isArray(value)) return false
  return value.every(
    (item) =>
      typeof item === "object" &&
      item !== null &&
      typeof (item as AafUiBlockInfoItem).label === "string" &&
      typeof (item as AafUiBlockInfoItem).value === "string"
  )
}
