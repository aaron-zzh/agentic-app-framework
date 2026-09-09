/**
 * 新建项目表单校验 schema。
 *
 * 封面不在此校验：coverMode 默认 AI_GENERATE，未选择时天然有值，不需要提交前拦截。
 *
 * @author AaronZZH & Kiro
 */

import { z } from "zod"
import { schemaUtils } from "@/components/form/schema-utils"

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
  documentVersionIds: z.array(z.number()),
  coverMode: z.enum(["UPLOAD", "AI_GENERATE"]),
  coverUpload: z.object({ fileId: z.number(), url: z.string(), name: z.string() }).optional(),
  coverPrompt: z.string().optional(),
  brief: z.string().optional()
})

export type NewProjectFormValues = z.infer<typeof newProjectSchema>
