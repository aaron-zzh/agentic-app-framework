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

export const newProjectSchema = z.object({
  name: schemaUtils.requiredText({ error: "请输入项目名称", max: 200 }),
  projectTypeCode: schemaUtils.requiredText({ error: "请选择项目类型" }),
  blueprintVersionId: z
    .number({ error: "请选择蓝图模板" })
    .int()
    .positive({ message: "请选择蓝图模板" }),
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

export type NewProjectFormValues = z.infer<typeof newProjectSchema>

export type AigcInitializationFormValue =
  NewProjectFormValues[AigcProjectInitializationFieldKey]

/** 将 canonical Task 的文本结果转换为目标初始化字段的表单值。 */
export function parseAigcInitializationResult(
  field: Pick<AigcProjectInitializationField, "key" | "valueType">,
  result: string
): AigcInitializationFormValue {
  const text = result.trim()
  if (!text) throw new Error("Assistant 尚未返回可填入的文本结果。")
  if (field.valueType === "STRING_LIST") {
    return text
      .split(/\r?\n/)
      .map((item) => item.replace(/^(?:[-*•]|\d+[.)])\s*/, "").trim())
      .filter(Boolean)
  }
  if (field.valueType === "JSON" || field.key === "deliveryRequirements") {
    let parsed: unknown
    try {
      parsed = JSON.parse(text)
    } catch {
      throw new Error("Assistant 结果不是有效的 JSON 对象，无法填入交付要求。")
    }
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("Assistant 结果不是有效的 JSON 对象，无法填入交付要求。")
    }
    return parsed as Record<string, unknown>
  }
  return text
}

/** 判断初始化字段是否会被物化请求视为已提供值。 */
export function hasAigcInitializationValue(value: unknown): boolean {
  if (typeof value === "string") return value.trim().length > 0
  if (Array.isArray(value)) return value.length > 0
  if (value !== null && typeof value === "object") return Object.keys(value).length > 0
  return false
}
