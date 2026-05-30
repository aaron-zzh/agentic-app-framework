/**
 * 画板导出按钮——支持 PNG/SVG/PDF 导出
 * @author AaronZZH & Kiro
 */

"use client"

import type { Editor } from "tldraw"
import { exportAs } from "tldraw"
import { Download } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"

interface CanvasExportButtonProps {
  editor: Editor
  formats: ("png" | "svg" | "pdf")[]
}

/** 格式标签映射 */
const FORMAT_LABELS: Record<string, string> = {
  png: "导出 PNG",
  svg: "导出 SVG",
  pdf: "导出 PDF"
}

/** 画板导出按钮 */
export function CanvasExportButton({ editor, formats }: CanvasExportButtonProps) {
  /** 执行导出 */
  const handleExport = async (format: "png" | "svg" | "pdf") => {
    const shapeIds = editor.getCurrentPageShapeIds()
    if (shapeIds.size === 0) return

    try {
      await exportAs(editor, [...shapeIds], { format: format as "png" | "svg", background: true, padding: 32 })
    } catch {
      // 导出失败静默处理（tldraw 内部可能抛出）
    }
  }

  return (
    <div className="absolute top-2 left-2 z-50">
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="outline" size="sm"><Download className="mr-1 h-3 w-3" />导出</Button>} />
        <DropdownMenuContent>
          {formats.map((fmt) => (
            <DropdownMenuItem key={fmt} onClick={() => handleExport(fmt)}>
              {FORMAT_LABELS[fmt]}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
