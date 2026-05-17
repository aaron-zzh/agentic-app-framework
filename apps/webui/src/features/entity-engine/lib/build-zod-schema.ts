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
  const base = buildBaseSchema(field)
  return field.required ? base : base.optional()
}

function buildBaseSchema(field: DataFieldDef): z.ZodTypeAny {
  switch (field.type) {
    case "email":
      return z.string().email({ message: "邮箱格式无效" })
    case "text": {
      let s = z.string()
      if (field.minLength) s = s.min(field.minLength)
      if (field.maxLength) s = s.max(field.maxLength)
      return s
    }
    case "textarea":
      return field.maxLength ? z.string().max(field.maxLength) : z.string()
    case "richText":
    case "code":
    case "json":
    case "date":
      return z.string()
    case "number": {
      let s = z.number()
      if (field.min !== undefined) s = s.min(field.min)
      if (field.max !== undefined) s = s.max(field.max)
      return s
    }
    case "checkbox":
      return z.boolean()
    case "select":
      if (field.options.length > 0) {
        const values = field.options.map((o) => o.value) as [string, ...string[]]
        return field.multiple ? z.array(z.enum(values)) : z.enum(values)
      }
      return z.string()
    case "relationship":
      return field.hasMany ? z.array(z.string()) : z.string()
    case "upload":
      return field.multiple ? z.array(z.string()) : z.string()
    default:
      return z.unknown()
  }
}
