/**
 * CanvasPanel——通用 tldraw 无限画布
 *
 * 复用 tldraw 引擎，通过 persistenceKey 持久化到 IndexedDB。
 * 可在项目工作台、Chatter 工具区等多处嵌入。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Download } from "lucide-react"
import { useCallback, useState } from "react"
import { type Editor, exportAs, Tldraw } from "tldraw"
import "tldraw/tldraw.css"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"

interface CanvasPanelProps {
  /** IndexedDB 持久化键，不传则不持久化 */
  persistenceKey?: string
}

export function CanvasPanel({ persistenceKey }: CanvasPanelProps) {
  const [editor, setEditor] = useState<Editor | null>(null)

  const handleMount = useCallback((e: Editor) => {
    setEditor(e)
  }, [])

  const handleExport = useCallback(
    async (format: "png" | "svg") => {
      if (!editor) return
      try {
        const ids = Array.from(editor.getCurrentPageShapeIds())
        if (ids.length === 0) return
        await exportAs(editor, ids, { format, background: true, padding: 32 })
      } catch {
        // 导出失败静默处理
      }
    },
    [editor]
  )

  return (
    <div className="relative h-full w-full">
      <Tldraw onMount={handleMount} persistenceKey={persistenceKey} licenseKey={process.env.NEXT_PUBLIC_TLDRAW_LICENSE_KEY} />

      {/* 导出按钮 */}
      {editor && (
        <div className="absolute top-3 right-3 z-10">
          <DropdownMenu>
            <DropdownMenuTrigger className="flex items-center gap-1.5 rounded-md border border-foreground/[0.08] bg-background/90 px-3 py-1.5 text-xs shadow-sm backdrop-blur transition-colors hover:bg-foreground/[0.04]">
              <Download className="size-3.5" />
              导出
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => handleExport("png")}>导出 PNG</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleExport("svg")}>导出 SVG</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      )}
    </div>
  )
}
