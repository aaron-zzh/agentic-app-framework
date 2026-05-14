/**
 * buildZodSchema——根据 FieldDef[] 自动生成 Zod 校验 schema
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```ts
 * const schema = buildZodSchema(entity.fields)
 * const form = useForm({ resolver: zodResolver(schema) })
 * ```
 */

import { z } from "zod"

import type { DataFieldDef, FieldDef } from "../types"

/** 根据字段定义数组生成 Zod object schema */
export function buildZodSchema(fields: FieldDef[]): z.ZodObject<Record<string, z.ZodTypeAny>> {
  const shape: Record<string, z.ZodTypeAny> = {}

  for (const field of fields) {
    // 跳过布局字段
    if (field.type === "group" || field.type === "tabs" || field.type === "row") continue

    const dataField = field as DataFieldDef
    shape[dataField.name] = buildFieldSchema(dataField)
  }

  return z.object(shape)
}

/** 根据单个字段定义生成对应的 Zod schema */
function buildFieldSchema(field: DataFieldDef): z.ZodTypeAny {
  let schema: z.ZodTypeAny

  switch (field.type) {
    case "text":
    case "textarea":
    case "email":
    case "richText":
    case "code":
    case "json":
      schema = z.string()
      if (field.type === "email") {
        schema = z.string().email({ message: "邮箱格式无效" })
      }
      if (field.type === "text" && field.maxLength) {
        schema = (schema as z.ZodString).max(field.maxLength)
      }
      if (field.type === "text" && field.minLength) {
        schema = (schema as z.ZodString).min(field.minLength)
      }
      if (field.type === "textarea" && field.maxLength) {
        schema = (schema as z.ZodString).max(field.maxLength)
      }
      break

    case "number":
      schema = z.number()
      if (field.min !== undefined) schema = (schema as z.ZodNumber).min(field.min)
      if (field.max !== undefined) schema = (schema as z.ZodNumber).max(field.max)
      break

    case "checkbox":
      schema = z.boolean()
      break

    case "date":
      schema = z.string()
      break

    case "select":
      if (field.options.length > 0) {
        const values = field.options.map((o) => o.value) as [string, ...string[]]
        schema = field.multiple ? z.array(z.enum(values)) : z.enum(values)
      } else {
        schema = z.string()
      }
      break

    case "relationship":
      schema = field.hasMany ? z.array(z.string()) : z.string()
      break

    case "upload":
      schema = field.multiple ? z.array(z.string()) : z.string()
      break

    default:
      schema = z.unknown()
  }

  // required 处理：非必填字段允许空值
  if (!field.required) {
    schema = schema.optional()
  }

  return schema
}
