/**
 * 文案生成面板——从底部弹起，支持口播/小红书类型、模板库、翻译、改写、长度设置
 * @author AaronZZH & Kiro
 */

"use client"

import { AnimatePresence, m } from "framer-motion"
import { FileText, RefreshCw, X } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { copywritingApi } from "@/lib/api/rest/ai"
import { PromptTemplateDialog } from "../generation/PromptTemplateDialog"
import { RoleSelector } from "../generation/RoleSelector"
import { useAigcStore } from "../store"

const TEMPLATES = [
  { value: "", label: "无模板" },
  { value: "product-launch", label: "新品上市" },
  { value: "promotion", label: "促销活动" },
  { value: "brand-story", label: "品牌故事" },
  { value: "tutorial", label: "教程攻略" },
  { value: "review", label: "测评分享" }
]

const TRANSLATE_OPTIONS = [
  { value: "", label: "不翻译" },
  { value: "en", label: "英文" },
  { value: "ja", label: "日文" },
  { value: "ko", label: "韩文" },
  { value: "fr", label: "法文" },
  { value: "es", label: "西班牙文" }
]

export function CopywritingPanel() {
  const open = useAigcStore((s) => s.copywritingPanelOpen)
  const setOpen = useAigcStore((s) => s.setCopywritingPanelOpen)
  const content = useAigcStore((s) => s.copywritingContent)
  const setContent = useAigcStore((s) => s.setCopywritingContent)
  const type = useAigcStore((s) => s.copywritingType)
  const setType = useAigcStore((s) => s.setCopywritingType)
  const template = useAigcStore((s) => s.copywritingTemplate)
  const setTemplate = useAigcStore((s) => s.setCopywritingTemplate)
  const translateTo = useAigcStore((s) => s.copywritingTranslateTo)
  const setTranslateTo = useAigcStore((s) => s.setCopywritingTranslateTo)
  const length = useAigcStore((s) => s.copywritingLength)
  const setLength = useAigcStore((s) => s.setCopywritingLength)
  const model = useAigcStore((s) => s.copywritingModel)
  const setModel = useAigcStore((s) => s.setCopywritingModel)
  const agentRole = useAigcStore((s) => s.agentRole)
  const setAgentRole = useAigcStore((s) => s.setAgentRole)

  const [generating, setGenerating] = useState(false)
  const [panelHeight, setPanelHeight] = useState<number>(() =>
    typeof window !== "undefined" ? Math.round(window.innerHeight * 0.6) : 400
  )
  const resizeStart = useRef<{ my: number; h: number } | null>(null)

  const handleResizeDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    resizeStart.current = {
      my: e.clientY,
      h: e.currentTarget.closest("[data-panel]")?.clientHeight ?? 400
    }
  }, [])

  const handleResizeMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!resizeStart.current) return
    const delta = resizeStart.current.my - e.clientY
    setPanelHeight(Math.max(200, resizeStart.current.h + delta))
  }, [])

  const handleResizeUp = useCallback(() => {
    resizeStart.current = null
  }, [])

  async function handleGenerate() {
    setGenerating(true)
    setContent("")
    let acc = ""
    await copywritingApi.generate(
      { topic: content || "新品发布", type, template, length },
      {
        onChunk: (chunk) => {
          acc += chunk
          setContent(acc)
        },
        onDone: () => setGenerating(false),
        onError: () => setGenerating(false)
      }
    )
  }

  async function handleRewrite() {
    if (!content.trim()) return
    const original = content
    setGenerating(true)
    setContent("")
    let acc = ""
    await copywritingApi.rewrite(
      { content: original },
      {
        onChunk: (chunk) => {
          acc += chunk
          setContent(acc)
        },
        onDone: () => setGenerating(false),
        onError: () => setGenerating(false)
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
            <div className="flex items-center gap-3">
              <Label className="shrink-0 text-muted-foreground text-xs">类型</Label>
              <Tabs value={type} onValueChange={(v) => setType(v as "oral" | "xiaohongshu")}>
                <TabsList className="h-7">
                  <TabsTrigger value="oral" className="h-6 px-3 text-xs">
                    口播
                  </TabsTrigger>
                  <TabsTrigger value="xiaohongshu" className="h-6 px-3 text-xs">
                    小红书
                  </TabsTrigger>
                </TabsList>
              </Tabs>
            </div>

            <div className="flex min-h-[120px] flex-1 flex-col gap-1">
              <div className="flex items-center justify-between">
                <Label className="text-muted-foreground text-xs">文案内容</Label>
                <PromptTemplateDialog type="COPYWRITING" onSelect={(p) => setContent(p)} />
              </div>
              <Textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="在此输入主题或关键词，或直接粘贴需要改写的文案..."
                className="flex-1 resize-none text-sm"
              />
            </div>
          </div>

          <div className="shrink-0 border-t px-4 py-3">
            <div className="flex flex-wrap items-center justify-center gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <Select value={model} onValueChange={(v) => setModel(v ?? "GPT-4o")}>
                  <SelectTrigger className="h-8 w-[130px] text-xs">
                    <span className="shrink-0 text-muted-foreground">模型</span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="GPT-4o">GPT-4o</SelectItem>
                    <SelectItem value="GPT-4.1">GPT-4.1</SelectItem>
                    <SelectItem value="Claude 4">Claude 4</SelectItem>
                    <SelectItem value="DeepSeek R2">DeepSeek R2</SelectItem>
                    <SelectItem value="Qwen Max">Qwen Max</SelectItem>
                  </SelectContent>
                </Select>
                <Select value={template} onValueChange={(v) => setTemplate(v ?? "")}>
                  <SelectTrigger className="h-8 w-[130px] text-xs">
                    <span className="shrink-0 text-muted-foreground">模板</span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TEMPLATES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select
                  value={length}
                  onValueChange={(v) => setLength(v as "short" | "medium" | "long")}
                >
                  <SelectTrigger className="h-8 w-[110px] text-xs">
                    <span className="shrink-0 text-muted-foreground">长度</span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="short">短篇（≤200字）</SelectItem>
                    <SelectItem value="medium">中篇（200-500字）</SelectItem>
                    <SelectItem value="long">长篇（500+字）</SelectItem>
                  </SelectContent>
                </Select>
                <Select value={translateTo} onValueChange={(v) => setTranslateTo(v ?? "")}>
                  <SelectTrigger className="h-8 w-[110px] text-xs">
                    <span className="shrink-0 text-muted-foreground">翻译</span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TRANSLATE_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <RoleSelector value={agentRole} onChange={setAgentRole} />

              <Button
                size="sm"
                variant="outline"
                disabled={generating || !content.trim()}
                onClick={handleRewrite}
                className="h-8 gap-1 text-xs"
              >
                <RefreshCw className="size-3" />
                改写
              </Button>

              <Separator orientation="vertical" className="h-5" />

              <Button
                size="sm"
                disabled={generating}
                onClick={handleGenerate}
                className="h-8 bg-gradient-to-r from-emerald-500 to-teal-500 text-white hover:from-emerald-600 hover:to-teal-600"
              >
                <FileText className="mr-1 size-3" />
                {generating ? "生成中..." : "生成"}
              </Button>
            </div>
          </div>
        </m.div>
      )}
    </AnimatePresence>
  )
}
