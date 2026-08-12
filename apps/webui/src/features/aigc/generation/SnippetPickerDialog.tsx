/**
 * 创作片段选择器：按通用/我的、分类和关键词筛选，多选后追加到提示词。
 * @author AaronZZH & Kiro
 */

"use client"

import { Check, Library, Search } from "lucide-react"
import { useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { useAigcSnippets } from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils/cn"

type SnippetSource = "COMMON" | "MINE"

interface SnippetPickerDialogProps {
  value: string
  onChange: (value: string) => void
  triggerClassName?: string
}

/** 以技能选择器同款 Popover 选择片段，并将内容追加到提示词末尾。 */
export function SnippetPickerDialog({
  value,
  onChange,
  triggerClassName
}: SnippetPickerDialogProps) {
  const [open, setOpen] = useState(false)
  const [source, setSource] = useState<SnippetSource>("COMMON")
  const [search, setSearch] = useState("")
  const [activeCategory, setActiveCategory] = useState("全部")
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const { data: page, isLoading } = useAigcSnippets({ pageSize: 200 })

  const snippets = useMemo(
    () => (page?.list ?? []).filter((snippet) => Boolean(snippet.content?.trim())),
    [page?.list]
  )
  const sourceSnippets = useMemo(
    () => (source === "COMMON" ? snippets.filter((snippet) => snippet.isPublic) : snippets),
    [snippets, source]
  )
  const categories = useMemo(
    () => [
      "全部",
      ...Array.from(
        new Set(
          sourceSnippets
            .map((snippet) => snippet.category)
            .filter((category): category is string => Boolean(category))
        )
      )
    ],
    [sourceSnippets]
  )
  const visibleSnippets = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    return sourceSnippets.filter((snippet) => {
      if (activeCategory !== "全部" && snippet.category !== activeCategory) return false
      if (!normalizedSearch) return true
      return `${snippet.name} ${snippet.content ?? ""} ${snippet.category ?? ""}`
        .toLowerCase()
        .includes(normalizedSearch)
    })
  }, [activeCategory, search, sourceSnippets])
  const selectedSnippets = useMemo(() => {
    const byId = new Map(snippets.map((snippet) => [snippet.id, snippet]))
    return selectedIds.map((id) => byId.get(id)).filter((snippet) => snippet !== undefined)
  }, [selectedIds, snippets])

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (nextOpen) {
      setSource("COMMON")
      setSearch("")
      setActiveCategory("全部")
      return
    }
    setSelectedIds([])
  }

  function handleSourceChange(nextSource: SnippetSource) {
    setSource(nextSource)
    setActiveCategory("全部")
  }

  function toggleSnippet(id: number) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((selectedId) => selectedId !== id) : [...current, id]
    )
  }

  function appendSelected() {
    const addition = selectedSnippets
      .map((snippet) => snippet.content?.trim())
      .filter((content): content is string => Boolean(content))
      .join(", ")
    if (!addition) return

    const current = value.trim()
    const separator = current && !/[,，;；\s]$/.test(current) ? ", " : ""
    onChange(current ? `${current}${separator}${addition}` : addition)
    setOpen(false)
    setSelectedIds([])
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
        <Library className="size-3" />
        片段
      </PopoverTrigger>
      <PopoverContent align="end" sideOffset={6} className="w-[28rem] max-w-[calc(100vw-2rem)] p-0">
        <div className="flex items-center justify-between border-foreground/6 border-b px-4 py-3">
          <span className="font-semibold text-sm">选择片段</span>
          <Badge variant="secondary">已选 {selectedIds.length} 个</Badge>
        </div>

        <div className="flex flex-col gap-2 border-foreground/6 border-b px-3 py-2">
          <div className="flex items-center gap-1">
            {(
              [
                ["COMMON", "通用"],
                ["MINE", "我的"]
              ] as const
            ).map(([sourceValue, label]) => (
              <button
                key={sourceValue}
                type="button"
                onClick={() => handleSourceChange(sourceValue)}
                className={cn(
                  "rounded-full px-2.5 py-1 text-xs transition-colors",
                  source === sourceValue
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
                placeholder="搜索片段..."
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
                <Skeleton key={`snippet-skeleton-${index}`} className="h-16 w-full rounded-xl" />
              ))
            ) : visibleSnippets.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground text-sm">
                {search.trim()
                  ? "没有匹配的片段"
                  : source === "COMMON"
                    ? "暂无通用片段"
                    : "暂无个人片段，请先在资产中心创建"}
              </p>
            ) : (
              visibleSnippets.map((snippet) => {
                const selected = selectedIds.includes(snippet.id)
                return (
                  <button
                    key={snippet.id}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => toggleSnippet(snippet.id)}
                    className={cn(
                      "flex w-full items-start gap-3 rounded-xl border px-3 py-2.5 text-left transition-colors",
                      selected
                        ? "border-primary/40 bg-primary/10"
                        : "border-foreground/6 bg-foreground/2 hover:bg-foreground/5"
                    )}
                  >
                    <div
                      className={cn(
                        "mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg font-semibold text-sm",
                        selected
                          ? "bg-primary/20 text-primary"
                          : "bg-foreground/8 text-foreground/60"
                      )}
                    >
                      {snippet.name.slice(0, 1)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="truncate font-medium text-sm">{snippet.name}</span>
                        {snippet.category ? (
                          <Badge variant="secondary" className="h-4 px-1.5 text-[10px]">
                            {snippet.category}
                          </Badge>
                        ) : null}
                        {selected ? <Check className="size-3.5 shrink-0 text-primary" /> : null}
                      </div>
                      <p className="mt-0.5 line-clamp-2 text-muted-foreground text-xs">
                        {snippet.content}
                      </p>
                    </div>
                  </button>
                )
              })
            )}
          </div>
        </ScrollArea>

        <div className="flex justify-end gap-2 border-foreground/6 border-t px-3 py-2.5">
          <Button type="button" size="sm" variant="ghost" onClick={() => setOpen(false)}>
            取消
          </Button>
          <Button
            type="button"
            size="sm"
            disabled={selectedIds.length === 0}
            onClick={appendSelected}
          >
            追加{selectedIds.length > 0 ? ` ${selectedIds.length} 个` : ""}片段
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
