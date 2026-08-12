/**
 * 提示词模板选择器：按通用/我的、分类和关键词筛选并应用模板。
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { Search, Sparkles } from "lucide-react"
import { useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import type { GenerationTemplateVO } from "@/lib/api/rest/ai"
import { generationTemplateApi, listPublicTemplates, markTemplateUsed } from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils/cn"

type TemplateSource = "COMMON" | "MINE"

interface PromptTemplateDialogProps {
  type: string
  onSelect: (prompt: string) => void
  hasReferenceImages?: boolean
  /** 使用场景：GENERATION（默认，单次生成）| PROJECT（项目级） */
  scope?: "GENERATION" | "PROJECT"
  /** 触发按钮自定义样式，未传则使用默认样式 */
  triggerClassName?: string
}

/** 以技能选择器同款 Popover 展示并应用提示词模板。 */
export function PromptTemplateDialog({
  type,
  onSelect,
  hasReferenceImages,
  scope = "GENERATION",
  triggerClassName
}: PromptTemplateDialogProps) {
  const [open, setOpen] = useState(false)
  const [source, setSource] = useState<TemplateSource>("COMMON")
  const [activeCategory, setActiveCategory] = useState("全部")
  const [search, setSearch] = useState("")

  const { data: publicPage, isLoading: publicLoading } = useQuery({
    queryKey: ["aigc", "templates", "public", type, scope],
    queryFn: () => listPublicTemplates({ type, scope, size: 100 }),
    enabled: open,
    staleTime: 5 * 60 * 1000
  })
  const { data: myPage, isLoading: mineLoading } = useQuery({
    queryKey: ["prompt-templates", "me", { pageSize: 100 }],
    queryFn: () => generationTemplateApi.listMine({ pageSize: 100 }),
    enabled: open,
    staleTime: 5 * 60 * 1000
  })

  const commonTemplates = publicPage?.list ?? []
  const myTemplates = useMemo(
    () =>
      (myPage?.list ?? []).filter(
        (template) => template.type === type && (template.scope ?? "GENERATION") === scope
      ),
    [myPage?.list, scope, type]
  )
  const sourceTemplates = source === "COMMON" ? commonTemplates : myTemplates
  const categories = useMemo(
    () => [
      "全部",
      ...Array.from(
        new Set(
          sourceTemplates
            .map((template) => template.category)
            .filter((category): category is string => Boolean(category))
        )
      )
    ],
    [sourceTemplates]
  )
  const filteredTemplates = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    return sourceTemplates.filter((template) => {
      if (activeCategory !== "全部" && template.category !== activeCategory) return false
      if (!normalizedSearch) return true
      return `${template.name} ${template.prompt} ${template.category ?? ""}`
        .toLowerCase()
        .includes(normalizedSearch)
    })
  }, [activeCategory, search, sourceTemplates])
  const isLoading = source === "COMMON" ? publicLoading : mineLoading

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (nextOpen) {
      setSource("COMMON")
      setActiveCategory(hasReferenceImages ? "图像编辑" : "全部")
      setSearch("")
    }
  }

  function handleSourceChange(nextSource: TemplateSource) {
    setSource(nextSource)
    setActiveCategory("全部")
  }

  function handleSelect(template: GenerationTemplateVO) {
    onSelect(template.prompt)
    setOpen(false)
    void markTemplateUsed(template.id).catch(() => {})
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger
        render={
          <button
            type="button"
            className={
              triggerClassName ??
              "inline-flex h-7 items-center gap-1 rounded-md px-2 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
            }
          />
        }
      >
        <Sparkles className="size-3" />
        提示词库
      </PopoverTrigger>
      <PopoverContent align="end" sideOffset={6} className="w-[28rem] max-w-[calc(100vw-2rem)] p-0">
        <div className="flex items-center justify-between border-foreground/6 border-b px-4 py-3">
          <span className="font-semibold text-sm">选择提示词</span>
          <Badge variant="secondary">{filteredTemplates.length} 个</Badge>
        </div>

        <div className="flex flex-col gap-2 border-foreground/6 border-b px-3 py-2">
          <div className="flex items-center gap-1">
            {(
              [
                ["COMMON", "通用"],
                ["MINE", "我的"]
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => handleSourceChange(value)}
                className={cn(
                  "rounded-full px-2.5 py-1 text-xs transition-colors",
                  source === value
                    ? "bg-primary/15 text-primary"
                    : "text-muted-foreground hover:bg-foreground/6 hover:text-foreground"
                )}
              >
                {label}
              </button>
            ))}
            <div className="relative ml-auto w-48">
              <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="搜索提示词..."
                className="h-8 pl-8 text-xs"
              />
            </div>
          </div>
          <div className="flex gap-1 overflow-x-auto">
            {categories.map((category) => (
              <button
                key={category}
                type="button"
                onClick={() => setActiveCategory(category)}
                className={cn(
                  "shrink-0 rounded-full px-2.5 py-1 text-xs transition-colors",
                  activeCategory === category
                    ? "bg-foreground/10 text-foreground"
                    : "text-muted-foreground hover:bg-foreground/6 hover:text-foreground"
                )}
              >
                {category}
              </button>
            ))}
          </div>
        </div>

        <ScrollArea className="h-[300px]">
          <div className="flex flex-col gap-1.5 p-3">
            {isLoading ? (
              Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={`prompt-skeleton-${index}`} className="h-16 w-full rounded-xl" />
              ))
            ) : filteredTemplates.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground text-sm">
                {search.trim() ? "没有匹配的提示词" : "暂无可用提示词"}
              </p>
            ) : (
              filteredTemplates.map((template) => (
                <button
                  key={template.id}
                  type="button"
                  onClick={() => handleSelect(template)}
                  className="flex w-full items-start gap-3 rounded-xl border border-foreground/6 bg-foreground/2 px-3 py-2.5 text-left transition-colors hover:bg-foreground/5"
                >
                  <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-foreground/8 font-semibold text-foreground/60 text-sm">
                    {template.name.slice(0, 1)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="truncate font-medium text-sm">{template.name}</span>
                      {template.category ? (
                        <Badge variant="secondary" className="h-4 px-1.5 text-[10px]">
                          {template.category}
                        </Badge>
                      ) : null}
                    </div>
                    <p className="mt-0.5 line-clamp-2 text-muted-foreground text-xs">
                      {template.prompt}
                    </p>
                  </div>
                </button>
              ))
            )}
          </div>
        </ScrollArea>
      </PopoverContent>
    </Popover>
  )
}
