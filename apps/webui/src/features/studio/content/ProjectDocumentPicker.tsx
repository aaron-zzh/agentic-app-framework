/**
 * 创建项目时使用的现有文档多选器。
 * @author AaronZZH & Kiro
 */

"use client"

import { FileText } from "lucide-react"
import { useId } from "react"
import { Checkbox } from "@/components/ui/checkbox"
import type { DocListItem } from "@/lib/api/rest/system/document"

interface ProjectDocumentPickerProps {
  values: number[]
  options: DocListItem[]
  loading?: boolean
  disabled?: boolean
  onValueChange: (values: number[]) => void
}

/** 选择创建后立即关联到项目的现有文档。 */
export function ProjectDocumentPicker({
  values,
  options,
  loading = false,
  disabled = false,
  onValueChange
}: ProjectDocumentPickerProps) {
  const id = useId()

  if (loading) {
    return <p className="text-muted-foreground text-sm">正在加载文档…</p>
  }

  if (options.length === 0) {
    return <p className="text-muted-foreground text-sm">暂无可关联文档，不影响创建。</p>
  }

  return (
    <div className="max-h-40 overflow-y-auto rounded-xl border p-2">
      <div className="flex flex-col gap-1">
        {options.map((document) => {
          const checked = values.includes(document.id)
          const inputId = `${id}-${document.id}`
          return (
            <label
              key={document.id}
              htmlFor={inputId}
              className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-2 text-sm hover:bg-muted"
            >
              <Checkbox
                id={inputId}
                checked={checked}
                disabled={disabled}
                onCheckedChange={(nextChecked) =>
                  onValueChange(
                    nextChecked
                      ? [...values, document.id]
                      : values.filter((value) => value !== document.id)
                  )
                }
              />
              <FileText className="shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1 truncate">{document.title}</span>
              <span className="shrink-0 text-muted-foreground text-xs">{document.docType}</span>
            </label>
          )
        })}
      </div>
    </div>
  )
}
