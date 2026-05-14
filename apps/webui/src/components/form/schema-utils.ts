/**
 * Zod schema 工厂函数——统一校验规则
 * @author AaronZZH & Kiro
 *
 * @example
 * ```ts
 * const schema = z.object({
 *   email: schemaUtils.email(),
 *   phone: schemaUtils.phone(),
 *   avatar: schemaUtils.file(),
 * })
 * ```
 */

import { z } from "zod"

interface ErrorMessages {
  required?: string
  invalid?: string
}

export const schemaUtils = {
  /** 邮箱 */
  email: (props?: { error?: ErrorMessages }) =>
    z
      .string()
      .min(1, { message: props?.error?.required ?? "邮箱不能为空" })
      .email({ message: props?.error?.invalid ?? "邮箱格式无效" }),

  /** 手机号 */
  phone: (props?: { error?: ErrorMessages; pattern?: RegExp }) =>
    z
      .string()
      .min(1, { message: props?.error?.required ?? "手机号不能为空" })
      .regex(props?.pattern ?? /^1[3-9]\d{9}$/, {
        message: props?.error?.invalid ?? "手机号格式无效"
      }),

  /** 日期（字符串格式） */
  date: (props?: { error?: ErrorMessages }) =>
    z
      .string()
      .min(1, { message: props?.error?.required ?? "日期不能为空" }),

  /** 单文件（string URL 或空） */
  file: (props?: { error?: string }) =>
    z
      .string()
      .min(1, { message: props?.error ?? "请上传文件" }),

  /** 多文件 */
  files: (props?: { error?: string; min?: number }) =>
    z
      .array(z.string())
      .min(props?.min ?? 1, { message: props?.error ?? "请上传文件" }),

  /** 必填文本 */
  requiredText: (props?: { error?: string; min?: number; max?: number }) => {
    let s = z.string().min(props?.min ?? 1, { message: props?.error ?? "此字段不能为空" })
    if (props?.max) s = s.max(props.max)
    return s
  },

  /** 可空输入（空字符串转 null） */
  nullable: <T extends z.ZodTypeAny>(schema: T, props?: { error?: string }) =>
    z.preprocess(
      (val) => (val === "" || val === undefined ? null : val),
      schema.nullable()
    )
}
