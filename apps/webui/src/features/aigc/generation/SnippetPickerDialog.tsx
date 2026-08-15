/**
 * 创作片段选择器：按公共/我的、分类和关键词筛选，返回结构化片段供输入区展示。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Check, Library, Plus, Search } from "lucide-react"
import { useId, useMemo, useState } from "react"
import { useDebounce } from "use-debounce"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import { CoverImageUpload } from "@/features/aigc/generation/CoverImageUpload"
import { CoverThumbnail } from "@/features/aigc/generation/CoverThumbnail"
import { useLoadMoreOnVisible } from "@/features/studio/assets/useLoadMoreOnVisible"
import {
  type AigcSnippet,
  useAigcSnippetMeta,
  useCreateAigcSnippet,
  useInfiniteAigcSnippets
} from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils/cn"

type SnippetSource = "COMMON" | "MINE"

interface SnippetPickerDialogProps {
  selectedSnippets: AigcSnippet[]
  onSelectedSnippetsChange: (snippets: AigcSnippet[]) => void
  triggerClassName?: string
}

/** 按基础提示词、片段选择顺序组合最终提交内容。 */
export function buildPromptWithSnippets(prompt: string, snippets: AigcSnippet[]): string {
  const parts = [prompt.trim()]
  for (const snippet of snippets) {
    const content = snippet.content?.trim()
    if (content) parts.push(content)
  }
  return parts.filter(Boolean).join(", ")
}

/** 以技能选择器同款 Popover 选择片段，并返回结构化选择结果。 */
export function SnippetPickerDialog({
  selectedSnippets: appliedSnippets,
  onSelectedSnippetsChange,
  triggerClassName
}: SnippetPickerDialogProps) {
  const uid = useId()
  const quickCreate = useBoolean()
  const [open, setOpen] = useState(false)
  const [source, setSource] = useState<SnippetSource>("MINE")
  const [search, setSearch] = useState("")
  const [debouncedSearch] = useDebounce(search.trim(), 300)
  const [activeCategory, setActiveCategory] = useState("全部")
  const [selectedSnippets, setSelectedSnippets] = useState<AigcSnippet[]>([])
  const [createName, setCreateName] = useState("")
  const [createCoverUrl, setCreateCoverUrl] = useState<string | null>(null)
  const [createContent, setCreateContent] = useState("")
  const createSnippet = useCreateAigcSnippet()
  const metaQuery = useAigcSnippetMeta()
  const canCreate = source === "MINE" && Boolean(metaQuery.data?.operations.includes("create"))
  const snippetQuery = useInfiniteAigcSnippets(
    {
      search: debouncedSearch || undefined,
      ownerOnly: source === "MINE" || undefined,
      publicOnly: source === "COMMON" || undefined
    },
    open
  )

  const snippets = useMemo(() => {
    const byId = new Map<number, AigcSnippet>()
    snippetQuery.data?.pages.forEach((page) => {
      page.list.forEach((snippet) => {
        if (snippet.content?.trim()) byId.set(snippet.id, snippet)
      })
    })
    return [...byId.values()]
  }, [snippetQuery.data?.pages])
  const sourceSnippets = useMemo(
    () =>
      source === "COMMON"
        ? snippets.filter((snippet) => snippet.isPublic)
        : snippets.filter((snippet) => snippet.ownedByCurrentUser),
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
  const visibleSnippets = useMemo(
    () =>
      activeCategory === "全部"
        ? sourceSnippets
        : sourceSnippets.filter((snippet) => snippet.category === activeCategory),
    [activeCategory, sourceSnippets]
  )
  const loadMoreRef = useLoadMoreOnVisible({
    hasNextPage: Boolean(snippetQuery.hasNextPage),
    isFetchingNextPage: snippetQuery.isFetchingNextPage,
    fetchNextPage: snippetQuery.fetchNextPage
  })

  function resetQuickCreate() {
    quickCreate.onFalse()
    setCreateName("")
    setCreateCoverUrl(null)
    setCreateContent("")
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (nextOpen) {
      setSource("MINE")
      setSearch("")
      setActiveCategory("全部")
      setSelectedSnippets(appliedSnippets)
      resetQuickCreate()
      return
    }
    setSelectedSnippets([])
  }

  function handleSourceChange(nextSource: SnippetSource) {
    setSource(nextSource)
    setActiveCategory("全部")
    resetQuickCreate()
  }

  function handleCreate() {
    const name = createName.trim()
    const content = createContent.trim()
    if (!name || !content) return
    createSnippet.mutate(
      { name, coverUrl: createCoverUrl, content, isPublic: false },
      {
        onSuccess: (created) => {
          const nextSnippets = [
            ...selectedSnippets.filter((snippet) => snippet.id !== created.id),
            created
          ]
          onSelectedSnippetsChange(nextSnippets)
          setOpen(false)
          resetQuickCreate()
        }
      }
    )
  }

  function toggleSnippet(snippet: AigcSnippet) {
    setSelectedSnippets((current) =>
      current.some((selected) => selected.id === snippet.id)
        ? current.filter((selected) => selected.id !== snippet.id)
        : [...current, snippet]
    )
  }

  function applySelected() {
    onSelectedSnippetsChange(selectedSnippets)
    setOpen(false)
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
        <div className="flex items-center justify-between gap-3 border-foreground/6 border-b px-4 py-3">
          <span className="font-semibold text-sm">
            {quickCreate.value ? "快速创建片段" : "选择片段"}
          </span>
          <div className="flex shrink-0 items-center gap-2">
            {!quickCreate.value && canCreate ? (
              <Button type="button" size="sm" variant="outline" onClick={quickCreate.onTrue}>
                <Plus className="size-3.5" />
                新建
              </Button>
            ) : null}
            <Badge variant="secondary">
              {quickCreate.value ? "保存到我的" : `已选 ${selectedSnippets.length} 个`}
            </Badge>
          </div>
        </div>

        {quickCreate.value ? (
          <div className="flex flex-col gap-3 p-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-quick-name`}>标题</Label>
              <Input
                id={`${uid}-quick-name`}
                value={createName}
                onChange={(event) => setCreateName(event.target.value)}
                placeholder="例如：电影感光影"
                autoFocus
              />
            </div>
            <CoverImageUpload
              id={`${uid}-quick-cover`}
              value={createCoverUrl}
              onChange={setCreateCoverUrl}
              disabled={createSnippet.isPending}
            />
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-quick-content`}>片段内容</Label>
              <Textarea
                id={`${uid}-quick-content`}
                value={createContent}
                onChange={(event) => setCreateContent(event.target.value)}
                placeholder="输入可复用的提示词片段…"
                className="min-h-32"
              />
            </div>
            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="outline" onClick={resetQuickCreate}>
                返回
              </Button>
              <Button
                type="button"
                disabled={createSnippet.isPending || !createName.trim() || !createContent.trim()}
                onClick={handleCreate}
              >
                {createSnippet.isPending ? "创建中…" : "创建并添加"}
              </Button>
            </div>
          </div>
        ) : (
          <>
            <div className="flex flex-col gap-2 border-foreground/6 border-b px-3 py-2">
              <div className="flex items-center gap-1">
                {(
                  [
                    ["MINE", "我的"],
                    ["COMMON", "公共"]
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
                {snippetQuery.isLoading ? (
                  Array.from({ length: 3 }).map((_, index) => (
                    <Skeleton
                      key={`snippet-skeleton-${index}`}
                      className="h-16 w-full rounded-xl"
                    />
                  ))
                ) : visibleSnippets.length === 0 ? (
                  <p className="py-8 text-center text-muted-foreground text-sm">
                    {search.trim()
                      ? "没有匹配的片段"
                      : source === "COMMON"
                        ? "暂无公共片段"
                        : "暂无个人片段，请先在资产中心创建"}
                  </p>
                ) : (
                  visibleSnippets.map((snippet) => {
                    const selected = selectedSnippets.some((item) => item.id === snippet.id)
                    return (
                      <button
                        key={snippet.id}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => toggleSnippet(snippet)}
                        className={cn(
                          "flex w-full items-start gap-3 rounded-xl border px-3 py-2.5 text-left transition-colors",
                          selected
                            ? "border-primary/40 bg-primary/10"
                            : "border-foreground/6 bg-foreground/2 hover:bg-foreground/5"
                        )}
                      >
                        <CoverThumbnail
                          src={snippet.coverUrl}
                          alt={`${snippet.name}封面`}
                          fallback={
                            <span className="font-semibold text-sm">
                              {snippet.name.slice(0, 1)}
                            </span>
                          }
                          className={cn("mt-0.5 size-9", selected && "bg-primary/20 text-primary")}
                        />
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
                <div
                  ref={loadMoreRef}
                  className="flex h-8 items-center justify-center text-muted-foreground text-xs"
                >
                  {snippetQuery.isFetchingNextPage
                    ? "正在加载更多…"
                    : snippetQuery.hasNextPage
                      ? "继续向下滚动加载"
                      : visibleSnippets.length > 0
                        ? "已加载全部片段"
                        : null}
                </div>
              </div>
            </ScrollArea>

            <div className="flex justify-end gap-2 border-foreground/6 border-t px-3 py-2.5">
              <Button type="button" size="sm" variant="ghost" onClick={() => setOpen(false)}>
                取消
              </Button>
              <Button
                type="button"
                size="sm"
                disabled={selectedSnippets.length === 0}
                onClick={applySelected}
              >
                应用{selectedSnippets.length > 0 ? ` ${selectedSnippets.length} 个` : ""}片段
              </Button>
            </div>
          </>
        )}
      </PopoverContent>
    </Popover>
  )
}
