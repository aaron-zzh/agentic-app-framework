/**
 * 添加自定义字段弹窗——类型选择 + 配置表单
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import {
  CalendarDays,
  CaseSensitive,
  CheckSquare,
  Hash,
  List,
  Plus,
  X
} from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import {
  customFieldApi,
  type CustomFieldInput,
  type CustomFieldType,
  type FieldOption
} from "@/lib/api/custom-field"

/** 字段类型元数据 */
const FIELD_TYPES: { type: CustomFieldType; label: string; icon: React.ReactNode }[] = [
  { type: "text", label: "文本", icon: <CaseSensitive className="size-4" /> },
  { type: "number", label: "数字", icon: <Hash className="size-4" /> },
  { type: "date", label: "日期", icon: <CalendarDays className="size-4" /> },
  { type: "select", label: "下拉选择", icon: <List className="size-4" /> },
  { type: "boolean", label: "布尔", icon: <CheckSquare className="size-4" /> }
]

interface AddCustomFieldDialogProps {
  /** 实体 slug */
  slug: string
  /** 弹窗开关（受控） */
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** 添加自定义字段弹窗 */
export function AddCustomFieldDialog({ slug, open, onOpenChange }: AddCustomFieldDialogProps) {
  const queryClient = useQueryClient()

  const [selectedType, setSelectedType] = useState<CustomFieldType | null>(null)
  const [name, setName] = useState("")
  const [label, setLabel] = useState("")
  const [options, setOptions] = useState<FieldOption[]>([])
  const [optionInput, setOptionInput] = useState("")

  const resetForm = useCallback(() => {
    setSelectedType(null)
    setName("")
    setLabel("")
    setOptions([])
    setOptionInput("")
  }, [])

  const mutation = useMutation({
    mutationFn: (data: CustomFieldInput) => customFieldApi.create(slug, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["entity-defs"] })
      queryClient.invalidateQueries({ queryKey: ["custom-fields", slug] })
      resetForm()
      onOpenChange(false)
    }
  })

  /** 添加选项 */
  const addOption = useCallback(() => {
    const trimmed = optionInput.trim()
    if (!trimmed) return
    setOptions((prev) => [...prev, { label: trimmed, value: trimmed }])
    setOptionInput("")
  }, [optionInput])

  /** 移除选项 */
  const removeOption = useCallback((index: number) => {
    setOptions((prev) => prev.filter((_, i) => i !== index))
  }, [])

  /** 提交 */
  const handleSubmit = useCallback(() => {
    if (!selectedType || !name.trim() || !label.trim()) return
    const data: CustomFieldInput = {
      name: name.trim(),
      label: label.trim(),
      type: selectedType,
      ...(selectedType === "select" && options.length > 0 ? { options } : {})
    }
    mutation.mutate(data)
  }, [selectedType, name, label, options, mutation])

  const canSubmit =
    selectedType && name.trim() && label.trim() && (selectedType !== "select" || options.length > 0)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>添加自定义字段</DialogTitle>
          <DialogDescription>选择字段类型并填写配置信息</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* 类型选择 */}
          <div className="space-y-2">
            <Label>字段类型</Label>
            <div className="grid grid-cols-3 gap-2">
              {FIELD_TYPES.map(({ type, label: typeLabel, icon }) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => setSelectedType(type)}
                  className={`flex flex-col items-center gap-1 rounded-lg border p-3 text-xs transition-colors hover:bg-muted ${
                    selectedType === type
                      ? "border-primary bg-primary/5 text-primary"
                      : "border-border"
                  }`}
                >
                  {icon}
                  <span>{typeLabel}</span>
                </button>
              ))}
            </div>
          </div>

          {/* 字段名和标签 */}
          {selectedType && (
            <>
              <div className="space-y-2">
                <Label htmlFor="field-name">字段标识</Label>
                <Input
                  id="field-name"
                  placeholder="英文标识，如 custom_phone"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="field-label">显示名称</Label>
                <Input
                  id="field-label"
                  placeholder="如：联系电话"
                  value={label}
                  onChange={(e) => setLabel(e.target.value)}
                />
              </div>
            </>
          )}

          {/* select 类型的选项配置 */}
          {selectedType === "select" && (
            <div className="space-y-2">
              <Label>选项列表</Label>
              <div className="flex gap-2">
                <Input
                  placeholder="输入选项名称"
                  value={optionInput}
                  onChange={(e) => setOptionInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), addOption())}
                />
                <Button type="button" size="sm" variant="outline" onClick={addOption}>
                  <Plus className="size-4" />
                </Button>
              </div>
              {options.length > 0 && (
                <div className="flex flex-wrap gap-1">
                  {options.map((opt, i) => (
                    <Badge key={`${opt.value}-${i}`} variant="secondary" className="gap-1">
                      {opt.label}
                      <button type="button" onClick={() => removeOption(i)}>
                        <X className="size-3" />
                      </button>
                    </Badge>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            onClick={handleSubmit}
            disabled={!canSubmit || mutation.isPending}
          >
            {mutation.isPending ? "添加中..." : "确认添加"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
