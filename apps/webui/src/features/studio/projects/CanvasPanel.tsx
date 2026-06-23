/**
 * CanvasPanel——通用 tldraw 无限画布
 *
 * 复用 tldraw 引擎，通过 persistenceKey 持久化到 IndexedDB。
 * 可在项目工作台、Chatter 工具区等多处嵌入。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, Loader2, Wand2 } from "lucide-react"
import { useRouter } from "next/navigation"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import { type Editor, Tldraw } from "tldraw"
import "tldraw/tldraw.css"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { useFileUpload } from "@/lib/hooks/use-file-upload"

interface CanvasPanelProps {
  /** IndexedDB 持久化键，不传则不持久化 */
  persistenceKey?: string
}

export function CanvasPanel({ persistenceKey }: CanvasPanelProps) {
  const [editor, setEditor] = useState<Editor | null>(null)
  const [sendingToAi, setSendingToAi] = useState(false)
  const router = useRouter()
  const { upload } = useFileUpload()

  const handleMount = useCallback((e: Editor) => {
    setEditor(e)
  }, [])

  const handleExport = useCallback(
    async (format: "png" | "svg") => {
      if (!editor) return
      try {
        const ids = [...editor.getCurrentPageShapeIds()]
        if (ids.length === 0) return
        if (format === "svg") {
          const result = await editor.getSvgString(ids, { background: true, padding: 32 })
          if (!result) return
          const blob = new Blob([result.svg], { type: "image/svg+xml" })
          const url = URL.createObjectURL(blob)
          const a = document.createElement("a")
          a.href = url
          a.download = "canvas.svg"
          a.click()
          URL.revokeObjectURL(url)
        } else {
          const result = await editor.toImage(ids, { format: "png", background: true, padding: 32 })
          if (!result) return
          const url = URL.createObjectURL(result.blob)
          const a = document.createElement("a")
          a.href = url
          a.download = "canvas.png"
          a.click()
          URL.revokeObjectURL(url)
        }
      } catch {
        // 导出失败静默处理
      }
    },
    [editor]
  )

  /** 导出当前画布为 PNG → 上传后端 → 跳转图片 AI 编辑页 */
  const handleSendToAi = useCallback(async () => {
    if (!editor) return
    const ids = [...editor.getCurrentPageShapeIds()]
    if (ids.length === 0) {
      toast.warning("画布为空，请先绘制内容")
      return
    }
    setSendingToAi(true)
    try {
      const result = await editor.toImage(ids, { format: "jpeg", background: true, padding: 32 })
      if (!result) throw new Error("export failed")
      const file = new File([result.blob], "canvas.jpg", { type: "image/jpeg" })
      const { url: uploadedUrl } = await upload(file)
      const dest = new URL("/studio/create/image", window.location.origin)
      dest.searchParams.set("refUrl", uploadedUrl)
      dest.searchParams.set("prompt", "按照图片风格和内容进行 AI 编辑创作")
      router.push(dest.pathname + dest.search)
    } catch {
      toast.error("上传失败，请重试")
    } finally {
      setSendingToAi(false)
    }
  }, [editor, upload, router])

  return (
    <div className="relative h-full w-full">
      <Tldraw
        onMount={handleMount}
        persistenceKey={persistenceKey}
        licenseKey={process.env.NEXT_PUBLIC_TLDRAW_LICENSE_KEY}
      />

      {editor && (
        <div className="absolute right-3 bottom-16 z-10 flex gap-2">
          {/* AI 编辑按钮 */}
          <button
            type="button"
            disabled={sendingToAi}
            onClick={handleSendToAi}
            className="flex items-center gap-1.5 rounded-md bg-neutral-800 px-3 py-1.5 text-white text-xs shadow-sm transition-colors hover:bg-neutral-700 disabled:opacity-50"
          >
            {sendingToAi ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <Wand2 className="size-3.5" />
            )}
            AI 编辑
          </button>

          {/* 导出按钮 */}
          <DropdownMenu>
            <DropdownMenuTrigger className="flex items-center gap-1.5 rounded-md bg-neutral-800 px-3 py-1.5 text-white text-xs shadow-sm transition-colors hover:bg-neutral-700">
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
