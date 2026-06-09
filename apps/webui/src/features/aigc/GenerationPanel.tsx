/**
 * 生成面板——从底部弹起，包含参考素材区、Prompt 输入、参数栏
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useRef, useState } from "react"
import { AnimatePresence, m } from "framer-motion"
import { X } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { RichTextEditor } from "@/features/rich-text-editor"
import { useGenerateImage } from "@/lib/queries/use-image-generation"
import { ReferenceDropZone } from "./ReferenceDropZone"
import { ReferenceRow } from "./ReferenceRow"
import { useAigcStore } from "./store"

export function GenerationPanel() {
  const open = useAigcStore((s) => s.generationPanelOpen)
  const setOpen = useAigcStore((s) => s.setGenerationPanelOpen)
  const prompt = useAigcStore((s) => s.prompt)
  const setPrompt = useAigcStore((s) => s.setPrompt)
  const model = useAigcStore((s) => s.model)
  const setModel = useAigcStore((s) => s.setModel)
  const resolution = useAigcStore((s) => s.resolution)
  const setResolution = useAigcStore((s) => s.setResolution)
  const aspectRatio = useAigcStore((s) => s.aspectRatio)
  const setAspectRatio = useAigcStore((s) => s.setAspectRatio)
  const generateImage = useGenerateImage()
  const addPendingTask = useAigcStore((s) => s.addPendingTask)
  const [panelHeight, setPanelHeight] = useState<number>(() =>
    typeof window !== "undefined" ? Math.round(window.innerHeight * 0.6) : 400
  )
  const resizeStart = useRef<{ my: number; h: number } | null>(null)

  const handleResizeDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    resizeStart.current = { my: e.clientY, h: e.currentTarget.closest("[data-panel]")?.clientHeight ?? 400 }
  }, [])

  const handleResizeMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!resizeStart.current) return
    const delta = resizeStart.current.my - e.clientY // 向上拖为正
    setPanelHeight(Math.max(200, resizeStart.current.h + delta))
  }, [])

  const handleResizeUp = useCallback(() => { resizeStart.current = null }, [])

  function handleGenerate() {
    if (!prompt.trim()) return
    generateImage.mutate(
      { prompt, model },
      {
        onSuccess: (taskId) => {
          addPendingTask({ id: taskId, prompt, type: "IMAGE" })
          setPrompt("")
          setOpen(false)
        }
      }
    )
  }

  return (
    <AnimatePresence>
      {open && (
        <m.div
          data-panel
          initial={{ y: "100%" }}
          animate={{ y: 0 }}
          exit={{ y: "100%" }}
          transition={{ type: "spring", damping: 25, stiffness: 300 }}
          className="absolute inset-x-0 bottom-0 z-50 flex flex-col rounded-t-xl outline-hidden [background:linear-gradient(135deg,color-mix(in_oklch,var(--color-violet-500)_6%,transparent),transparent_50%,color-mix(in_oklch,var(--color-indigo-500)_6%,transparent)),var(--color-popover)] [box-shadow:0_-8px_32px_-4px_rgba(0,0,0,0.15),0_-2px_8px_-2px_rgba(0,0,0,0.1)]"
          style={{ height: panelHeight }}
        >
          {/* 顶部：拖拽手柄 + 收起按钮 */}
          <div className="relative flex items-center justify-center py-1.5">
            <div
              className="flex flex-1 cursor-ns-resize justify-center opacity-40 hover:opacity-80"
              onPointerDown={handleResizeDown}
              onPointerMove={handleResizeMove}
              onPointerUp={handleResizeUp}
            >
              <div className="h-1 w-10 rounded-full bg-muted-foreground" />
            </div>
            <Button
              variant="ghost"
              size="icon-sm"
              className="absolute top-0.5 right-1 opacity-60 hover:opacity-100"
              onClick={() => setOpen(false)}
              aria-label="关闭"
            >
              <X className="size-4" />
            </Button>
          </div>

          <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-4 pt-2">
            {/* 参考素材拖入区 */}
            <ReferenceDropZone />

            {/* 参考引用行 */}
            <ReferenceRow />

            {/* Prompt 输入：flex-1 撑满剩余空间 */}
            <div className="flex min-h-[120px] flex-1 flex-col">
              <RichTextEditor
                value={prompt}
                onChange={setPrompt}
                placeholder="描述你想生成的图像..."
                mode="plaintext"
                preset="minimal"
                fill
              />
            </div>
          </div>

          {/* 底部参数栏：固定在面板底部 */}
          <div className="shrink-0 border-t px-4 py-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                {/* 模型选择 */}
                <Select value={model} onValueChange={(v) => setModel(v ?? "GPT Image 2")}>
                  <SelectTrigger className="h-8 w-[160px] text-xs">
                    <span className="shrink-0 text-muted-foreground">模型</span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="GPT Image 2">GPT Image 2</SelectItem>
                    <SelectItem value="DALL·E 3">DALL·E 3</SelectItem>
                    <SelectItem value="Midjourney">Midjourney</SelectItem>
                  </SelectContent>
                </Select>

                {/* 分辨率 */}
                <Select value={resolution} onValueChange={(v) => setResolution(v ?? "2K")}>
                  <SelectTrigger className="h-8 w-[110px] text-xs">
                    <span className="shrink-0 text-muted-foreground">分辨率</span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="1K">1K</SelectItem>
                    <SelectItem value="2K">2K</SelectItem>
                    <SelectItem value="4K">4K</SelectItem>
                  </SelectContent>
                </Select>

                {/* 比例 */}
                <Select value={aspectRatio} onValueChange={(v) => setAspectRatio(v ?? "9:16")}>
                  <SelectTrigger className="h-8 w-[115px] text-xs">
                    <span className="shrink-0 text-muted-foreground">比例</span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {([
                      { value: "1:1",  rw: 12, rh: 12 },
                      { value: "9:16", rw: 8,  rh: 14 },
                      { value: "16:9", rw: 14, rh: 8  },
                      { value: "4:3",  rw: 12, rh: 9  },
                    ] as const).map(({ value, rw, rh }) => (
                      <SelectItem key={value} value={value}>
                        <span className="flex items-center gap-1.5">
                          {/* 外框固定 16×16，内部矩形居中按比例 */}
                          <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden className="shrink-0 text-muted-foreground">
                            <rect
                              x={(16 - rw) / 2 + 0.5}
                              y={(16 - rh) / 2 + 0.5}
                              width={rw - 1}
                              height={rh - 1}
                              rx="1"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="1.5"
                            />
                          </svg>
                          {value}
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="flex items-center gap-2">
                {/* 生成图像 */}
                <Button
                  size="sm"
                  disabled={generateImage.isPending || !prompt.trim()}
                  onClick={handleGenerate}
                  className="bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white hover:from-violet-600 hover:to-fuchsia-600"
                >
                  {generateImage.isPending ? "生成中..." : "生成"}
                </Button>
              </div>
            </div>
          </div>
        </m.div>
      )}
    </AnimatePresence>
  )
}
