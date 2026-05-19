/**
 * Field.Textarea——RHF 多行文本控件（支持升级为富文本编辑器）
 * @author AaronZZH & Kiro
 */

"use client"

import { Maximize2, Minimize2 } from "lucide-react"
import { useCallback, useState } from "react"
import { Controller, useFormContext } from "react-hook-form"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
// 依赖方向例外：表单控件消费 feature 层的富文本编辑器（"升级为富文本"场景）
import { RichTextEditor } from "@/features/rich-text-editor"
import { cn } from "@/lib/utils/cn"

export interface FieldTextareaProps {
  name: string
  label?: string
  placeholder?: string
  rows?: number
  className?: string
  disabled?: boolean
  /** 允许升级为富文本编辑器（默认 true） */
  allowRichText?: boolean
}

export function FieldTextarea({
  name,
  label,
  placeholder,
  rows = 3,
  className,
  disabled,
  allowRichText = true
}: FieldTextareaProps) {
  const { control } = useFormContext()
  const [isRichText, setIsRichText] = useState(false)

  const toggle = useCallback(() => setIsRichText((v) => !v), [])

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {/* 标签行 */}
      <div className="flex items-center justify-between">
        {label && <Label htmlFor={name}>{label}</Label>}
        {allowRichText && !disabled && (
          <button
            type="button"
            onClick={toggle}
            title={isRichText ? "切换为纯文本" : "升级为富文本编辑器"}
            className="flex items-center gap-1 rounded px-1.5 py-0.5 text-muted-foreground text-xs hover:bg-muted hover:text-foreground"
          >
            {isRichText ? (
              <>
                <Minimize2 className="h-3 w-3" />
                纯文本
              </>
            ) : (
              <>
                <Maximize2 className="h-3 w-3" />
                富文本
              </>
            )}
          </button>
        )}
      </div>

      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            {isRichText ? (
              <RichTextEditor
                value={field.value ?? ""}
                onChange={field.onChange}
                placeholder={placeholder}
                disabled={disabled}
                error={error?.message}
                minHeight={rows * 24}
                preset="minimal"
              />
            ) : (
              <>
                <Textarea
                  id={name}
                  placeholder={placeholder}
                  disabled={disabled}
                  rows={rows}
                  aria-invalid={!!error}
                  {...field}
                  value={field.value ?? ""}
                />
                {error && <p className="text-destructive text-xs">{error.message}</p>}
              </>
            )}
          </>
        )}
      />
    </div>
  )
}
