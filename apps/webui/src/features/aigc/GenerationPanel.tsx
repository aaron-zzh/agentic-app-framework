/**
 * 生成面板——从底部弹起，包含参考素材区、Prompt 输入、参数栏
 * @author AaronZZH & Kiro
 */

"use client"

import { useRef } from "react"
import { AnimatePresence, motion } from "framer-motion"
import { ChevronDown, Upload, X } from "lucide-react"
import { useDroppable } from "@dnd-kit/core"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useGenerateImage } from "@/lib/queries/use-image-generation"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "./store"
import { ReferenceRow } from "./ReferenceRow"
import { AtMention } from "./AtMention"

function ReferenceDropZone() {
  const { isOver, setNodeRef } = useDroppable({ id: "generation-drop-zone" })
  const referenceAssets = useAigcStore((s) => s.referenceAssets)
  const removeReferenceAsset = useAigcStore((s) => s.removeReferenceAsset)

  return (
    <div
      ref={setNodeRef}
      className={cn(
        "flex min-h-[72px] flex-wrap items-center gap-2 rounded-lg border border-dashed border-border/50 p-2 transition-colors",
        isOver && "border-primary bg-primary/5"
      )}
    >
      {referenceAssets.map((asset) => (
        <div key={asset.id} className="group relative size-14 overflow-hidden rounded-md bg-muted">
          {/* biome-ignore lint/performance/noImgElement: 动态参考素材缩略图 */}
          <img src={asset.thumbnail} alt={asset.name} className="size-full object-cover" />
          <button
            type="button"
            onClick={() => removeReferenceAsset(asset.id)}
            className="absolute -right-1 -top-1 hidden size-4 items-center justify-center rounded-full bg-destructive text-destructive-foreground group-hover:flex"
          >
            <X className="size-3" />
          </button>
        </div>
      ))}
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <Upload className="size-4" />
        <span>{referenceAssets.length}/16</span>
      </div>
    </div>
  )
}

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
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const generateImage = useGenerateImage()

  function handleGenerate() {
    if (!prompt.trim()) return
    generateImage.mutate({ prompt, model })
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ y: "100%" }}
          animate={{ y: 0 }}
          exit={{ y: "100%" }}
          transition={{ type: "spring", damping: 25, stiffness: 300 }}
          className="absolute inset-x-0 bottom-0 z-50 rounded-t-xl border-t border-border bg-card shadow-2xl"
        >
          <div className="flex flex-col gap-3 p-4">
            {/* 参考素材拖入区 */}
            <ReferenceDropZone />

            {/* 参考引用行 */}
            <ReferenceRow />

            {/* Prompt 输入 + @提及 */}
            <div className="relative">
              <Textarea
                ref={textareaRef}
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="描述你想生成的图像，输入 @ 引用素材..."
                className="min-h-[80px] resize-y bg-background"
              />
              <AtMention value={prompt} onChange={setPrompt} textareaRef={textareaRef} />
            </div>

            {/* 底部参数栏 */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                {/* 模型选择 */}
                <Select value={model} onValueChange={(v) => setModel(v ?? "GPT Image 2")}>
                  <SelectTrigger className="h-8 w-[140px] text-xs">
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
                  <SelectTrigger className="h-8 w-[80px] text-xs">
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
                  <SelectTrigger className="h-8 w-[80px] text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="1:1">1:1</SelectItem>
                    <SelectItem value="9:16">9:16</SelectItem>
                    <SelectItem value="16:9">16:9</SelectItem>
                    <SelectItem value="4:3">4:3</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="flex items-center gap-2">
                {/* 收起 */}
                <Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
                  <ChevronDown className="mr-1 size-4" />
                  收起
                </Button>
                {/* 生成按钮 */}
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
        </motion.div>
      )}
    </AnimatePresence>
  )
}
