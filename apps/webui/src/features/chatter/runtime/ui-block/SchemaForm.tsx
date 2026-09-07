/**
 * SchemaForm——通用 JSON Schema 驱动表单渲染器（AAF-114 #11408 第二版）。
 *
 * 只覆盖 {@link ClarificationResponseSchema}（后端 `questions()` 派生）实际产出的两种字段形态：
 * - `{type:"string"}` → 文本输入
 * - `{type:"string", enum:[...]}` → 单选下拉
 *
 * 不预先支持 JSON Schema 全部特性；未来扩展 number/checkbox/多选需要先扩展后端
 * `ClarificationRequest.Question` 领域模型，再回来扩展本组件（design.md #11408 已标注为已知限制）。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"

interface SchemaFormFieldSpec {
  type?: string
  title?: string
  enum?: string[]
}

export interface JsonSchemaObject {
  type?: string
  properties?: Record<string, SchemaFormFieldSpec>
  required?: string[]
}

interface SchemaFormProps {
  schema: JsonSchemaObject
  onSubmit: (values: Record<string, string>) => void
  submitting?: boolean
}

/** 按 schema.properties 顺序渲染字段；required 字段未填满时禁用提交按钮（本地校验，不阻断用户输入）。 */
export function SchemaForm({ schema, onSubmit, submitting }: SchemaFormProps) {
  const properties = schema.properties ?? {}
  const required = schema.required ?? []
  const fieldNames = Object.keys(properties)
  const [values, setValues] = useState<Record<string, string>>({})

  const allRequiredFilled = required.every((name) => (values[name] ?? "").trim().length > 0)

  return (
    <div className="my-1 space-y-2.5 rounded-lg border bg-card p-3">
      {fieldNames.map((name) => {
        const field = properties[name]
        const isRequired = required.includes(name)
        return (
          <div key={name}>
            <Label className="text-xs">
              {field?.title ?? name}
              {isRequired && <span className="ml-0.5 text-destructive">*</span>}
            </Label>
            <div className="mt-1">
              {field?.enum && field.enum.length > 0 ? (
                <Select
                  value={values[name] ?? ""}
                  onValueChange={(next) => setValues((prev) => ({ ...prev, [name]: next ?? "" }))}
                >
                  <SelectTrigger className="h-8 text-sm">
                    <SelectValue placeholder="请选择" />
                  </SelectTrigger>
                  <SelectContent>
                    {field.enum.map((option) => (
                      <SelectItem key={option} value={option}>
                        {option}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  className="h-8 text-sm"
                  value={values[name] ?? ""}
                  onChange={(e) => setValues((prev) => ({ ...prev, [name]: e.target.value }))}
                />
              )}
            </div>
          </div>
        )
      })}
      <Button
        size="sm"
        className="w-full"
        disabled={!allRequiredFilled || submitting}
        onClick={() => onSubmit(values)}
      >
        {submitting ? "提交中…" : "提交"}
      </Button>
    </div>
  )
}
