"use client"

import { useQuery } from "@tanstack/react-query"
import { Sparkles } from "lucide-react"
import { useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { GenerationTemplateVO } from "@/lib/api/rest/ai/generation-template"
import { listPublicTemplates, markTemplateUsed } from "@/lib/api/rest/ai/generation-template"
import { cn } from "@/lib/utils/cn"

interface PromptTemplateDialogProps {
  type: string
  onSelect: (prompt: string) => void
  hasReferenceImages?: boolean
  /** 使用场景：GENERATION（默认，单次生成）| PROJECT（项目级） */
  scope?: "GENERATION" | "PROJECT"
  /** 触发按钮自定义样式，未传则使用默认样式 */
  triggerClassName?: string
}

export function PromptTemplateDialog({
  type,
  onSelect,
  hasReferenceImages,
  scope = "GENERATION",
  triggerClassName
}: PromptTemplateDialogProps) {
  const [open, setOpen] = useState(false)
  const [activeCategory, setActiveCategory] = useState<string>("全部")

  function handleOpenChange(v: boolean) {
    if (v) setActiveCategory(hasReferenceImages ? "图像编辑" : "全部")
    setOpen(v)
  }

  // 一次拉全量，前端分组
  const { data } = useQuery({
    queryKey: ["aigc", "templates", "public", type, scope],
    queryFn: () => listPublicTemplates({ type, scope, size: 100 }),
    enabled: open,
    staleTime: 5 * 60 * 1000
  })

  const allTemplates = data?.list ?? []

  // 按 category 分组，生成侧边栏列表
  const categories = useMemo(() => {
    const cats = Array.from(new Set(allTemplates.map((t) => t.category).filter(Boolean)))
    return ["全部", ...cats]
  }, [allTemplates])

  const filtered = useMemo(
    () =>
      activeCategory === "全部"
        ? allTemplates
        : allTemplates.filter((t) => t.category === activeCategory),
    [allTemplates, activeCategory]
  )

  async function handleSelect(t: GenerationTemplateVO) {
    onSelect(t.prompt)
    setOpen(false)
    markTemplateUsed(t.id).catch(() => {})
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger
        className={
          triggerClassName ??
          "inline-flex h-7 items-center gap-1 rounded-md px-2 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
        }
      >
        <Sparkles className="size-3" />
        模板库
      </DialogTrigger>
      <DialogContent className="max-w-2xl! p-0">
        <DialogHeader className="border-b px-4 py-3">
          <DialogTitle className="text-sm">选择提示词模板</DialogTitle>
        </DialogHeader>
        <div className="flex min-h-0" style={{ height: "60vh" }}>
          {/* 左侧分类 */}
          <aside className="w-32 shrink-0 overflow-y-auto border-r p-2">
            {categories.map((cat) => (
              <button
                key={cat}
                type="button"
                onClick={() => setActiveCategory(cat)}
                className={cn(
                  "w-full rounded-md px-2 py-1.5 text-left text-xs transition-colors hover:bg-muted",
                  activeCategory === cat && "bg-muted font-medium text-foreground"
                )}
              >
                {cat}
              </button>
            ))}
          </aside>

          {/* 右侧模板列表 */}
          <ScrollArea className="flex-1">
            {filtered.length === 0 ? (
              <p className="py-10 text-center text-muted-foreground text-sm">暂无模板</p>
            ) : (
              <div className="flex flex-col gap-2 p-3">
                {filtered.map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => handleSelect(t)}
                    className="flex flex-col gap-1 rounded-lg border border-border/50 p-3 text-left transition-colors hover:bg-muted/50"
                  >
                    <span className="flex items-center gap-2">
                      <span className="font-medium text-sm">{t.name}</span>
                      {t.category && (
                        <Badge variant="secondary" className="h-4 px-1.5 text-[10px]">
                          {t.category}
                        </Badge>
                      )}
                    </span>
                    <p className="line-clamp-2 text-muted-foreground text-xs">{t.prompt}</p>
                  </button>
                ))}
              </div>
            )}
          </ScrollArea>
        </div>
      </DialogContent>
    </Dialog>
  )
}
