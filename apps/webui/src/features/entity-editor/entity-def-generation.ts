import type { EntityDefConfig } from "@/features/entity-engine/types"
import type { EntityResourceDescriptor } from "@/lib/api/rest/entity"

interface JsonObject {
  [key: string]: unknown
}

export interface EntityDefGenerationValidationResult {
  config: EntityDefConfig | null
  errors: string[]
}

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0
}

const GENERATED_FIELD_TYPES = new Set([
  "text",
  "textarea",
  "number",
  "email",
  "date",
  "checkbox",
  "select",
  "relationship",
  "richText",
  "json",
  "code",
  "upload",
  "group",
  "tabs",
  "row"
])

function validateFieldReference(
  value: unknown,
  path: string,
  allowedFields: Set<string>,
  errors: string[],
  required = true
): void {
  if (!isNonBlankString(value)) {
    if (required) errors.push(`${path} 必须是非空字段名`)
    return
  }
  if (!allowedFields.has(value)) errors.push(`${path} 引用了未授权字段：${value}`)
}

function validateFieldArray(
  value: unknown,
  path: string,
  allowedFields: Set<string>,
  resources: Set<string>,
  errors: string[]
): void {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`)
    return
  }

  for (const [index, item] of value.entries()) {
    const fieldPath = `${path}[${index}]`
    if (!isObject(item) || !isNonBlankString(item.type)) {
      errors.push(`${fieldPath} 必须包含 type`)
      continue
    }
    if (!GENERATED_FIELD_TYPES.has(item.type)) {
      errors.push(`${fieldPath}.type 不受生成器支持：${item.type}`)
      continue
    }
    if (item.pickerPath !== undefined) errors.push(`${fieldPath} 不允许声明 pickerPath`)

    switch (item.type) {
      case "group":
      case "row":
        validateFieldArray(item.fields, `${fieldPath}.fields`, allowedFields, resources, errors)
        continue
      case "tabs":
        if (!Array.isArray(item.tabs)) {
          errors.push(`${fieldPath}.tabs 必须是数组`)
          continue
        }
        for (const [tabIndex, tab] of item.tabs.entries()) {
          if (!isObject(tab)) {
            errors.push(`${fieldPath}.tabs[${tabIndex}] 必须是对象`)
            continue
          }
          if (!isNonBlankString(tab.label)) {
            errors.push(`${fieldPath}.tabs[${tabIndex}].label 必须是非空字符串`)
          }
          validateFieldArray(
            tab.fields,
            `${fieldPath}.tabs[${tabIndex}].fields`,
            allowedFields,
            resources,
            errors
          )
        }
        continue
      default:
        validateFieldReference(item.name, `${fieldPath}.name`, allowedFields, errors)
    }

    validateFieldReference(item.writeKey, `${fieldPath}.writeKey`, allowedFields, errors, false)
    if (item.type === "relationship") {
      if (!isNonBlankString(item.relationTo)) {
        errors.push(`${fieldPath}.relationTo 必须是规范 resource`)
      } else if (!resources.has(item.relationTo)) {
        errors.push(`${fieldPath}.relationTo 引用了未授权资源：${item.relationTo}`)
      }
    }
    if (item.type === "select") {
      if (!Array.isArray(item.options) || item.options.length === 0) {
        errors.push(`${fieldPath}.options 必须是非空数组`)
      } else if (
        !item.options.every(
          (option) =>
            isObject(option) && isNonBlankString(option.label) && isNonBlankString(option.value)
        )
      ) {
        errors.push(`${fieldPath}.options 每项必须包含 label 和 value`)
      }
    }
  }
}

function validateListFieldArray(
  value: unknown,
  path: string,
  allowedFields: Set<string>,
  errors: string[]
): void {
  if (value === undefined) return
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`)
    return
  }
  for (const [index, field] of value.entries()) {
    validateFieldReference(field, `${path}[${index}]`, allowedFields, errors)
  }
}

function validateListView(value: unknown, allowedFields: Set<string>, errors: string[]): void {
  if (!isObject(value)) {
    errors.push("listView 必须是对象")
    return
  }
  if (!Array.isArray(value.columns) || value.columns.length === 0) {
    errors.push("listView.columns 必须是非空数组")
  } else {
    for (const [index, column] of value.columns.entries()) {
      const name = isObject(column) ? column.name : column
      validateFieldReference(name, `listView.columns[${index}]`, allowedFields, errors)
    }
  }
  validateListFieldArray(value.searchableFields, "listView.searchableFields", allowedFields, errors)
  validateListFieldArray(value.filterableFields, "listView.filterableFields", allowedFields, errors)
  validateListFieldArray(value.filterFields, "listView.filterFields", allowedFields, errors)
  if (value.defaultSort !== undefined) {
    if (!isNonBlankString(value.defaultSort)) {
      errors.push("listView.defaultSort 必须是 field:asc 或 field:desc")
    } else {
      const [field, direction, ...rest] = value.defaultSort.split(":")
      if (!field || (direction !== "asc" && direction !== "desc") || rest.length > 0) {
        errors.push("listView.defaultSort 必须是 field:asc 或 field:desc")
      } else {
        validateFieldReference(field, "listView.defaultSort", allowedFields, errors)
      }
    }
  }
}

/** 从模型响应中提取一个可解析的 JSON 配置。 */
export function extractGeneratedEntityDefJson(text: string): string | null {
  const codeBlock = text.match(/```json\s*([\s\S]*?)```/i)
  const candidate = codeBlock?.[1]?.trim() ?? text.trim()
  try {
    JSON.parse(candidate)
    return candidate
  } catch {
    return null
  }
}

/** 校验 AI 草稿只引用 bootstrap 中的资源与字段，后端仍是最终权威。 */
export function validateGeneratedEntityDef(
  value: unknown,
  resources: EntityResourceDescriptor[]
): EntityDefGenerationValidationResult {
  const errors: string[] = []
  if (!isObject(value)) return { config: null, errors: ["生成结果必须是 JSON 对象"] }
  if (value.slug !== undefined || value.apiPath !== undefined) {
    errors.push("配置不得包含 slug 或 apiPath")
  }
  if (value.pickerPath !== undefined) errors.push("配置不得包含 pickerPath")
  if (value.kind !== "code") errors.push('kind 必须为 "code"')
  if (!isNonBlankString(value.resource)) errors.push("resource 必须是非空规范资源 ID")
  if (!isNonBlankString(value.label)) errors.push("label 必须是非空字符串")

  const descriptor = isNonBlankString(value.resource)
    ? resources.find((resource) => resource.resource === value.resource)
    : undefined
  if (!descriptor) {
    errors.push(`resource 未出现在可信资源目录：${String(value.resource ?? "")}`)
  }
  const allowedFields = new Set(descriptor?.fields ?? [])
  const resourceIds = new Set(resources.map((resource) => resource.resource))
  validateFieldArray(value.fields, "fields", allowedFields, resourceIds, errors)
  validateListView(value.listView, allowedFields, errors)

  return { config: errors.length === 0 ? (value as EntityDefConfig) : null, errors }
}

/** 构造包含可信资源字段白名单和当前草稿的系统提示。 */
export function buildEntityDefGeneratorSystemPrompt(
  resources: EntityResourceDescriptor[],
  currentDraft: string | null
): string {
  const resourceDirectory = resources
    .map(({ resource, fields }) => ({ resource, fields }))
    .sort((left, right) => left.resource.localeCompare(right.resource))

  return `你是 AAF 框架的 EntityDef 配置生成助手。你只生成已实现代码资源的 UI 元数据，绝不声称会创建数据表、接口或业务实体。

可信资源目录（唯一允许使用的 resource、relationTo 和字段白名单）：
${JSON.stringify(resourceDirectory)}

硬性规则：
1. 只能从可信资源目录选择一个 resource；若用户需求没有对应 resource，简短说明需要先实现并注册 Controller 资源，且不要输出 JSON 配置。
2. fields[].name、fields[].writeKey、listView.columns、searchableFields、filterableFields、filterFields 和 defaultSort 的字段部分只能使用所选 resource 的 fields。
3. relationship 必须使用目录中的 relationTo；禁止输出 pickerPath。select 必须提供至少一个 { label, value } option。
4. 必须包含 kind: "code"、resource、label、fields 和 listView.columns。defaultSort 仅在存在合适字段时输出，格式必须为 field:asc 或 field:desc。
5. 禁止输出 slug、apiPath、任何 endpoint、SQL、Markdown 注释或额外包装对象。
6. 仅使用以下字段 type：text、textarea、number、email、date、checkbox、select、relationship、richText、json、code、upload、group、tabs、row。
7. 只输出一个完整且合法的 \`\`\`json 代码块，不输出代码块外的解释。

${currentDraft ? `当前已校验草稿如下。用户要求修改时，以它为基础输出完整替换配置：\n\`\`\`json\n${currentDraft}\n\`\`\`` : "当前没有已校验草稿。"}`
}
