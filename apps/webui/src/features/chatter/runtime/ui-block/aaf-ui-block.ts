/**
 * AafUiBlock v1——服务端可安全投影到对话中的结构化展示卡片协议。
 *
 * 设计边界（AAF-114 #11407 design.md）：
 * - `INFO_CARD` 只读展示，唯一生成点在服务端转换器，前端只做渲染，不二次编造字段。
 * - `CHOICE`/`FORM` 是既有 `ClarificationRequest` 的只读投影快照，不是独立生命周期实体；
 *   `clarificationId` 对应服务端 `ClarificationRequest.requestId`。
 * - 本文件是唯一协议真理源：字段新增/变更必须先改这里，再改投影适配器与渲染器。
 *
 * @author AaronZZH & Kiro
 */

/** INFO_CARD 展示条目。 */
export interface AafUiBlockInfoItem {
  label: string
  value: string
}

/** CHOICE 可选项。 */
export interface AafUiBlockChoiceOption {
  id: string
  label: string
  description?: string
}

/**
 * FORM 字段定义。
 *
 * `type` 取值对齐后端 Clarification `Question.FieldType`（#11408 落地后一一对应）；
 * 本轮 #11407 只消费现有 `Map<String,String>` 澄清数据，`constraints` 允许为空。
 */
export interface AafUiBlockFormField {
  key: string
  label: string
  type: "text" | "textarea" | "number" | "select" | "checkbox"
  required?: boolean
  /** type=select 时必填。 */
  options?: AafUiBlockChoiceOption[]
  constraints?: { min?: number; max?: number; maxLength?: number }
}

export type AafUiBlock =
  | {
      version: 1
      id: string
      type: "INFO_CARD"
      title: string
      description?: string
      items?: AafUiBlockInfoItem[]
    }
  | {
      version: 1
      id: string
      type: "CHOICE"
      clarificationId: string
      title: string
      options: AafUiBlockChoiceOption[]
      multiple?: boolean
    }
  | {
      version: 1
      id: string
      type: "FORM"
      clarificationId: string
      title: string
      fields: AafUiBlockFormField[]
    }

/** 本前端实现已知的最高协议版本；未来升级 v2 时在此处一并放宽解析。 */
const KNOWN_VERSION = 1

/**
 * 运行时校验：把未知输入收窄为 {@link AafUiBlock}，失败时返回 null。
 *
 * 只做结构性校验（字段是否存在、类型是否匹配），不做业务语义校验（如 clarificationId 是否仍有效）——
 * 业务语义由渲染时读取的服务端权威状态负责，不在协议校验层重复判断。
 */
export function parseAafUiBlock(value: unknown): AafUiBlock | null {
  if (typeof value !== "object" || value === null) return null
  const v = value as Record<string, unknown>
  if (v.version !== KNOWN_VERSION) return null
  if (typeof v.id !== "string" || !v.id) return null
  if (typeof v.title !== "string") return null

  switch (v.type) {
    case "INFO_CARD":
      return isInfoItemArray(v.items)
        ? {
            version: 1,
            id: v.id,
            type: "INFO_CARD",
            title: v.title,
            description: typeof v.description === "string" ? v.description : undefined,
            items: v.items
          }
        : null
    case "CHOICE":
      return typeof v.clarificationId === "string" && isChoiceOptionArray(v.options)
        ? {
            version: 1,
            id: v.id,
            type: "CHOICE",
            clarificationId: v.clarificationId,
            title: v.title,
            options: v.options,
            multiple: v.multiple === true
          }
        : null
    case "FORM":
      return typeof v.clarificationId === "string" && isFormFieldArray(v.fields)
        ? {
            version: 1,
            id: v.id,
            type: "FORM",
            clarificationId: v.clarificationId,
            title: v.title,
            fields: v.fields
          }
        : null
    default:
      return null
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

function isChoiceOptionArray(value: unknown): value is AafUiBlockChoiceOption[] {
  if (!Array.isArray(value)) return false
  return value.every(
    (item) =>
      typeof item === "object" &&
      item !== null &&
      typeof (item as AafUiBlockChoiceOption).id === "string" &&
      typeof (item as AafUiBlockChoiceOption).label === "string"
  )
}

const FORM_FIELD_TYPES = new Set(["text", "textarea", "number", "select", "checkbox"])

function isFormFieldArray(value: unknown): value is AafUiBlockFormField[] {
  if (!Array.isArray(value)) return false
  return value.every((item) => {
    if (typeof item !== "object" || item === null) return false
    const f = item as AafUiBlockFormField
    return (
      typeof f.key === "string" &&
      typeof f.label === "string" &&
      typeof f.type === "string" &&
      FORM_FIELD_TYPES.has(f.type)
    )
  })
}
