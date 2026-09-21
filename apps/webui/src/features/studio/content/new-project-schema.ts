/**
 * 新建项目表单校验 schema。
 *
 * 封面不在此校验：coverMode 默认 AI_GENERATE，未选择时天然有值，不需要提交前拦截。
 *
 * @author AaronZZH & Kiro
 */

import { z } from "zod"
import { schemaUtils } from "@/components/form/schema-utils"
import type {
  AigcProjectInitializationField,
  AigcProjectInitializationFieldKey
} from "@/lib/api/rest/ai/aigc"

export const newProjectSchema = z
  .object({
    name: schemaUtils.requiredText({ error: "请输入项目名称", max: 200 }),
    projectTypeCode: schemaUtils.requiredText({ error: "请选择项目类型" }),
    structureSource: z.enum(["CATALOG", "ASSISTANT_GENERATED"]),
    blueprintVersionId: z.number({ error: "请选择蓝图模板" }).int().min(0).optional(),
    brandProfileId: z.number().optional(),
    productionMode: z.enum(["standard", "short_drama", "motion_comic"], {
      error: "请选择生产模式"
    }),
    channelCodes: z.array(z.string()),
    documents: z.array(
      z.object({
        fieldKey: z.string(),
        documentId: z.number().optional(),
        documentVersionId: z.number().optional(),
        contextNote: z.string().optional()
      })
    ),
    coverMode: z.enum(["UPLOAD", "AI_GENERATE"]),
    coverUpload: z.object({ fileId: z.number(), url: z.string(), name: z.string() }).optional(),
    coverPrompt: z.string().optional(),
    description: z.string().max(500).optional(),
    background: z.string().optional(),
    targetAudience: z.string().optional(),
    requirements: z.array(z.string()),
    constraints: z.array(z.string()),
    keyFacts: z.array(z.string()),
    keyMessages: z.array(z.string()),
    styleGuidance: z.string().optional(),
    successCriteria: z.array(z.string()),
    deliveryRequirements: z.record(z.string(), z.unknown()),
    locale: z.string().max(32).optional(),
    additionalContext: z.string().optional()
  })
  .superRefine((values, context) => {
    if (
      values.structureSource === "CATALOG" &&
      (!Number.isInteger(values.blueprintVersionId) || (values.blueprintVersionId ?? 0) < 1)
    ) {
      context.addIssue({
        code: "custom",
        path: ["blueprintVersionId"],
        message: "请选择蓝图模板"
      })
    }
  })

export type NewProjectFormValues = z.infer<typeof newProjectSchema>

export type AigcInitializationFormValue = NewProjectFormValues[AigcProjectInitializationFieldKey]

export interface AigcInitializationResultEntry {
  key: AigcProjectInitializationFieldKey
  value: AigcInitializationFormValue
}

export interface AigcInitializationResultParse {
  /** 类型校验通过、可直接填入表单的字段。 */
  applied: AigcInitializationResultEntry[]
  /** 助理给出了键但值类型不符合字段要求，未填入。 */
  rejected: AigcProjectInitializationFieldKey[]
}

/**
 * 将 canonical Task 的文本结果转换为初始化字段表单值。
 *
 * <p>答复契约是单个 JSON 对象，键为本次已确认的字段键，值按字段类型给出：字符串列表用字符串数组，
 * 交付要求用 JSON 对象，其余用字符串。单字段与多字段共用同一形状，不存在第二种解析路径；
 * 未给出的键视为该字段未产出，保持表单原值。
 */
export function parseAigcInitializationResults(
  fields: Array<Pick<AigcProjectInitializationField, "key" | "valueType">>,
  result: string
): AigcInitializationResultParse {
  const text = result.trim()
  if (!text) throw new Error("Assistant 尚未返回可填入的结果。")
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error(
      "Assistant 结果不是 JSON 对象；要求只输出以字段键为键的 JSON 对象，不要附加说明文字。"
    )
  }
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Assistant 结果不是 JSON 对象；要求只输出以字段键为键的 JSON 对象。")
  }
  const source = parsed as Record<string, unknown>
  const applied: AigcInitializationResultEntry[] = []
  const rejected: AigcProjectInitializationFieldKey[] = []
  for (const field of fields) {
    if (!(field.key in source)) continue
    const raw = source[field.key]
    if (field.valueType === "STRING_LIST") {
      const items = Array.isArray(raw)
        ? raw.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
        : []
      if (!Array.isArray(raw) || items.length !== raw.length || items.length === 0) {
        rejected.push(field.key)
        continue
      }
      applied.push({ key: field.key, value: items.map((item) => item.trim()) })
      continue
    }
    if (field.valueType === "JSON" || field.key === "deliveryRequirements") {
      if (raw === null || typeof raw !== "object" || Array.isArray(raw)) {
        rejected.push(field.key)
        continue
      }
      applied.push({ key: field.key, value: raw as Record<string, unknown> })
      continue
    }
    if (typeof raw !== "string" || !raw.trim()) {
      rejected.push(field.key)
      continue
    }
    applied.push({ key: field.key, value: raw.trim() })
  }
  if (applied.length === 0) {
    throw new Error(
      rejected.length > 0
        ? `Assistant 给出的 ${rejected.join("、")} 值类型不符合字段要求，未填入。`
        : "Assistant 结果里没有本次已确认的字段键。"
    )
  }
  return { applied, rejected }
}

/** 判断初始化字段是否会被物化请求视为已提供值。 */
export function hasAigcInitializationValue(value: unknown): boolean {
  if (typeof value === "string") return value.trim().length > 0
  if (Array.isArray(value)) return value.length > 0
  if (value !== null && typeof value === "object") return Object.keys(value).length > 0
  return false
}
