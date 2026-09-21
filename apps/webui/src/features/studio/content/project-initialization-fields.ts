/**
 * 项目初始化字段的唯一前端定义。
 *
 * <p>字段键与服务端 `AigcProjectInitializationFieldKey` 一致；`description` 不在此列，它由项目基本信息弹窗编辑，
 * 不重复出现在简报区。生成提议选择目标与简报区共用本定义，避免两处各自维护标签。
 * @author AaronZZH & Kiro
 */

import type { AigcProjectInitializationFieldKey } from "@/lib/api/rest/ai/aigc"

/** 字段的编辑形态：多行文本、每行一项的字符串列表、单行文本、结构化 JSON（只读）。 */
export type ProjectInitializationFieldForm = "TEXTAREA" | "STRING_LIST" | "TEXT" | "JSON"

export interface ProjectInitializationFieldSpec {
  key: AigcProjectInitializationFieldKey
  label: string
  form: ProjectInitializationFieldForm
  hint?: string
}

/** 项目简报字段，按用户阅读顺序排列。 */
export const PROJECT_INITIALIZATION_FIELDS: ProjectInitializationFieldSpec[] = [
  { key: "background", label: "背景信息", form: "TEXTAREA", hint: "项目所处的背景与前情。" },
  { key: "targetAudience", label: "目标受众", form: "TEXTAREA", hint: "内容面向的人群与场景。" },
  { key: "requirements", label: "内容要求", form: "STRING_LIST", hint: "每行一项。" },
  { key: "constraints", label: "约束条件", form: "STRING_LIST", hint: "每行一项。" },
  { key: "keyFacts", label: "关键事实", form: "STRING_LIST", hint: "每行一项，必须准确的事实。" },
  { key: "keyMessages", label: "核心信息", form: "STRING_LIST", hint: "每行一项。" },
  { key: "styleGuidance", label: "风格指导", form: "TEXTAREA" },
  { key: "successCriteria", label: "成功标准", form: "STRING_LIST", hint: "每行一项。" },
  { key: "locale", label: "语言区域", form: "TEXT", hint: "例如 zh-CN。" },
  { key: "additionalContext", label: "补充上下文", form: "TEXTAREA" },
  {
    key: "deliveryRequirements",
    label: "交付要求",
    form: "JSON",
    hint: "结构化配置，来自创建时的模板，此处只读。"
  }
]

/** 生成提议按目标类型列出可选目标时使用的键值对。 */
export const PROJECT_INITIALIZATION_FIELD_OPTIONS: Array<{ value: string; label: string }> =
  PROJECT_INITIALIZATION_FIELDS.map((field) => ({ value: field.key, label: field.label }))
