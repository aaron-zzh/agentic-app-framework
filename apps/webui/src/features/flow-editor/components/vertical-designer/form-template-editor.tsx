/**
 * 审批表单模板编辑器——字段列表管理 + 预览
 * @author AaronZZH
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import type { FormFieldDef, FormFieldType } from "./types"

interface FormTemplateEditorProps {
  fields: FormFieldDef[]
  onChange: (fields: FormFieldDef[]) => void
}

/** 字段类型选项 */
const FIELD_TYPES: { value: FormFieldType; label: string }[] = [
  { value: "text", label: "文本" },
  { value: "number", label: "数字" },
  { value: "date", label: "日期" },
  { value: "select", label: "选择" },
  { value: "textarea", label: "文本域" },
  { value: "file", label: "文件" }
]

/** 生成字段 ID */
function genFieldId(): string {
  return `field_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

export function FormTemplateEditor({ fields, onChange }: FormTemplateEditorProps) {
  const [showPreview, setShowPreview] = useState(false)

  /** 添加字段 */
  function addField() {
    onChange([
      ...fields,
      {
        id: genFieldId(),
        name: "",
        label: "",
        type: "text",
        required: false
      }
    ])
  }

  /** 更新字段 */
  function updateField(index: number, patch: Partial<FormFieldDef>) {
    onChange(fields.map((f, i) => (i === index ? { ...f, ...patch } : f)))
  }

  /** 删除字段 */
  function removeField(index: number) {
    onChange(fields.filter((_, i) => i !== index))
  }

  /** 上移字段 */
  function moveUp(index: number) {
    if (index === 0) return
    const arr = [...fields]
    ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
    onChange(arr)
  }

  /** 下移字段 */
  function moveDown(index: number) {
    if (index === fields.length - 1) return
    const arr = [...fields]
    ;[arr[index], arr[index + 1]] = [arr[index + 1], arr[index]]
    onChange(arr)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-sm">表单字段</h3>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setShowPreview(!showPreview)}>
            {showPreview ? "编辑" : "预览"}
          </Button>
          <Button size="sm" onClick={addField}>
            + 添加字段
          </Button>
        </div>
      </div>

      {showPreview ? (
        /* 表单预览 */
        <div className="space-y-3 rounded-md border p-4">
          {fields.length === 0 && <p className="text-muted-foreground text-sm">暂无字段</p>}
          {fields.map((field) => (
            <div key={field.id}>
              <span className="font-medium text-sm">
                {field.label || field.name || "未命名"}
                {field.required && <span className="ml-1 text-destructive">*</span>}
              </span>
              {field.type === "textarea" ? (
                <div className="mt-1 h-16 rounded-md border border-input bg-muted/30" />
              ) : field.type === "select" ? (
                <div className="mt-1 h-9 rounded-md border border-input bg-muted/30 px-3 py-1.5 text-muted-foreground text-xs">
                  请选择...
                </div>
              ) : (
                <div className="mt-1 h-9 rounded-md border border-input bg-muted/30" />
              )}
            </div>
          ))}
        </div>
      ) : (
        /* 字段编辑列表 */
        <div className="space-y-3">
          {fields.map((field, idx) => (
            <div key={field.id} className="flex items-start gap-2 rounded-md border p-3">
              {/* 排序按钮 */}
              <div className="flex flex-col gap-0.5 pt-1">
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-4 w-4 p-0 text-[10px]"
                  onClick={() => moveUp(idx)}
                  disabled={idx === 0}
                >
                  ↑
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-4 w-4 p-0 text-[10px]"
                  onClick={() => moveDown(idx)}
                  disabled={idx === fields.length - 1}
                >
                  ↓
                </Button>
              </div>

              {/* 字段配置 */}
              <div className="flex-1 space-y-2">
                <div className="flex gap-2">
                  <input
                    className="h-8 flex-1 rounded-md border border-input px-2 text-xs"
                    value={field.name}
                    onChange={(e) => updateField(idx, { name: e.target.value })}
                    placeholder="字段名（英文）"
                  />
                  <input
                    className="h-8 flex-1 rounded-md border border-input px-2 text-xs"
                    value={field.label}
                    onChange={(e) => updateField(idx, { label: e.target.value })}
                    placeholder="显示标签"
                  />
                </div>
                <div className="flex items-center gap-2">
                  <Select
                    value={field.type}
                    onValueChange={(v) => updateField(idx, { type: v as FormFieldType })}
                  >
                    <SelectTrigger className="h-7 w-24 text-xs">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {FIELD_TYPES.map((t) => (
                        <SelectItem key={t.value} value={t.value}>
                          {t.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <span className="flex items-center gap-1 text-xs">
                    <Checkbox
                      checked={field.required}
                      aria-label="必填"
                      onCheckedChange={(checked) =>
                        updateField(idx, { required: checked === true })
                      }
                    />
                    必填
                  </span>
                </div>
              </div>

              {/* 删除 */}
              <Button
                variant="ghost"
                size="sm"
                className="h-6 w-6 p-0 text-muted-foreground hover:text-destructive"
                onClick={() => removeField(idx)}
              >
                ×
              </Button>
            </div>
          ))}
          {fields.length === 0 && (
            <p className="py-4 text-center text-muted-foreground text-sm">
              点击"添加字段"开始构建表单
            </p>
          )}
        </div>
      )}

      <Separator />
      <p className="text-muted-foreground text-xs">
        共 {fields.length} 个字段，{fields.filter((f) => f.required).length} 个必填
      </p>
    </div>
  )
}
