"use client"

import { FileText, Pencil, Plus, Search, Trash2 } from "lucide-react"
import { useId, useMemo, useState } from "react"
import { useDebounce } from "use-debounce"
import { GlassCard, GlowButton } from "@/components/studio"
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { CoverImageUpload } from "@/features/aigc/generation/CoverImageUpload"
import { CoverThumbnail } from "@/features/aigc/generation/CoverThumbnail"
import { useLoadMoreOnVisible } from "@/features/studio/assets/useLoadMoreOnVisible"
import {
  type AigcSnippet,
  useAigcSnippetMeta,
  useCreateAigcSnippet,
  useDeleteAigcSnippet,
  useInfiniteAigcSnippets,
  useUpdateAigcSnippet
} from "@/lib/api/rest/ai/aigc"

type SnippetView = "MINE" | "PUBLIC"

function isSnippetView(value: string): value is SnippetView {
  return value === "MINE" || value === "PUBLIC"
}

interface SnippetEditDialogProps {
  initial?: AigcSnippet | null
  onClose: () => void
}

function SnippetEditDialog({ initial, onClose }: SnippetEditDialogProps) {
  const uid = useId()
  const [name, setName] = useState(initial?.name ?? "")
  const [category, setCategory] = useState(initial?.category ?? "")
  const [coverUrl, setCoverUrl] = useState<string | null>(initial?.coverUrl ?? null)
  const [content, setContent] = useState(initial?.content ?? "")
  const [isPublic, setIsPublic] = useState(initial?.isPublic ?? false)
  const createSnippet = useCreateAigcSnippet()
  const updateSnippet = useUpdateAigcSnippet()
  const isPending = createSnippet.isPending || updateSnippet.isPending

  function handleSave() {
    const input = {
      name: name.trim(),
      category: category.trim() || undefined,
      coverUrl,
      content: content.trim(),
      isPublic
    }
    if (!input.name || !input.content) return
    if (initial) {
      updateSnippet.mutate(
        { id: initial.id, version: initial.version, ...input },
        { onSuccess: onClose }
      )
      return
    }
    createSnippet.mutate(input, { onSuccess: onClose })
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{initial ? "编辑片段" : "新建片段"}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-3 py-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-name`}>标题</Label>
            <Input
              id={`${uid}-name`}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="例如：电影感光影"
            />
          </div>
          <CoverImageUpload
            id={`${uid}-cover`}
            value={coverUrl}
            onChange={setCoverUrl}
            disabled={isPending}
          />
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-content`}>片段内容</Label>
            <Textarea
              id={`${uid}-content`}
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="输入可复用的提示词片段…"
              className="min-h-32"
            />
          </div>
          <Accordion>
            <AccordionItem value="advanced" className="rounded-lg border px-3">
              <AccordionTrigger className="py-3 text-sm hover:no-underline">
                高级设置
              </AccordionTrigger>
              <AccordionContent className="flex flex-col gap-3 pb-3">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor={`${uid}-category`}>分类</Label>
                  <Input
                    id={`${uid}-category`}
                    value={category}
                    onChange={(event) => setCategory(event.target.value)}
                    placeholder="例如：风格、构图、人像"
                  />
                </div>
                <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
                  <div className="flex flex-col gap-0.5">
                    <Label htmlFor={`${uid}-public`}>公开片段</Label>
                    <p className="text-muted-foreground text-xs">允许当前组织或工作区成员复用</p>
                  </div>
                  <Switch
                    id={`${uid}-public`}
                    checked={isPublic}
                    onCheckedChange={setIsPublic}
                    aria-label="公开片段"
                  />
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button disabled={isPending || !name.trim() || !content.trim()} onClick={handleSave}>
            {isPending ? "保存中…" : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Studio 创作片段资产工作台。 */
export function SnippetAssetsView() {
  const [view, setView] = useState<SnippetView>("MINE")
  const [search, setSearch] = useState("")
  const [debouncedSearch] = useDebounce(search.trim(), 300)
  const [editTarget, setEditTarget] = useState<AigcSnippet | null | undefined>(undefined)
  const snippetQuery = useInfiniteAigcSnippets({
    search: debouncedSearch || undefined,
    ownerOnly: view === "MINE" || undefined,
    publicOnly: view === "PUBLIC" || undefined
  })
  const metaQuery = useAigcSnippetMeta()
  const deleteSnippet = useDeleteAigcSnippet()
  const operations = useMemo(
    () => new Set(metaQuery.data?.operations ?? []),
    [metaQuery.data?.operations]
  )
  const snippets = useMemo(() => {
    const byId = new Map<number, AigcSnippet>()
    snippetQuery.data?.pages.forEach((page) => {
      page.list.forEach((snippet) => {
        byId.set(snippet.id, snippet)
      })
    })
    return [...byId.values()]
  }, [snippetQuery.data?.pages])
  const loadMoreRef = useLoadMoreOnVisible({
    hasNextPage: snippetQuery.hasNextPage,
    isFetchingNextPage: snippetQuery.isFetchingNextPage,
    fetchNextPage: snippetQuery.fetchNextPage
  })
  const canCreate = view === "MINE" && operations.has("create")

  function handleViewChange(value: string) {
    if (isSnippetView(value)) setView(value)
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-4 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-semibold text-xl">创作片段</h1>
          <p className="mt-1 text-muted-foreground text-sm">
            保存并复用常用描述，内置片段可直接用于创作
          </p>
        </div>
        {canCreate ? (
          <GlowButton tone="primary" size="sm" onClick={() => setEditTarget(null)}>
            <Plus data-icon="inline-start" />
            新建片段
          </GlowButton>
        ) : null}
      </header>

      <Tabs value={view} onValueChange={handleViewChange}>
        <TabsList>
          <TabsTrigger value="MINE">我的</TabsTrigger>
          <TabsTrigger value="PUBLIC">公共</TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="搜索标题或片段内容"
          aria-label="搜索创作片段"
          className="pl-9"
        />
      </div>

      {snippetQuery.isLoading || metaQuery.isLoading ? (
        <div className="flex flex-col gap-2">
          {["one", "two", "three", "four", "five"].map((key) => (
            <Skeleton key={key} className="h-24 w-full rounded-xl" />
          ))}
        </div>
      ) : snippets.length === 0 ? (
        <Empty className="min-h-64 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <FileText />
            </EmptyMedia>
            <EmptyTitle>
              {debouncedSearch
                ? "没有匹配的片段"
                : view === "MINE"
                  ? "还没有创作片段"
                  : "暂无公共片段"}
            </EmptyTitle>
            <EmptyDescription>
              {debouncedSearch
                ? "尝试更换标题或内容关键词。"
                : view === "MINE"
                  ? "新建片段，积累你的创作表达库。"
                  : "公开片段和平台内置片段会显示在这里。"}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="flex flex-col gap-2">
          {snippets.map((snippet) => {
            const canUpdate = snippet.ownedByCurrentUser && operations.has("update")
            const canDelete = snippet.ownedByCurrentUser && operations.has("delete")
            return (
              <GlassCard key={snippet.id} glow="none" className="border border-foreground/6">
                <div className="flex items-start gap-3 p-4">
                  <CoverThumbnail
                    src={snippet.coverUrl}
                    alt={`${snippet.name}封面`}
                    fallback={
                      <span className="font-semibold text-sm">{snippet.name.slice(0, 1)}</span>
                    }
                    className="size-12"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-medium text-sm">{snippet.name}</p>
                      {snippet.builtin ? <Badge variant="secondary">内置</Badge> : null}
                      {snippet.isPublic ? <Badge variant="outline">公开</Badge> : null}
                      {snippet.category ? (
                        <Badge variant="outline">{snippet.category}</Badge>
                      ) : null}
                    </div>
                    <p className="mt-2 line-clamp-3 text-muted-foreground text-sm leading-6">
                      {snippet.content}
                    </p>
                  </div>
                  {canUpdate || canDelete ? (
                    <div className="flex shrink-0 gap-1">
                      {canUpdate ? (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => setEditTarget(snippet)}
                          aria-label={`编辑${snippet.name}`}
                        >
                          <Pencil />
                        </Button>
                      ) : null}
                      {canDelete ? (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          className="text-destructive hover:text-destructive"
                          disabled={
                            deleteSnippet.isPending && deleteSnippet.variables === snippet.id
                          }
                          onClick={() => deleteSnippet.mutate(snippet.id)}
                          aria-label={`删除${snippet.name}`}
                        >
                          <Trash2 />
                        </Button>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </GlassCard>
            )
          })}
          <div
            ref={loadMoreRef}
            className="flex h-10 items-center justify-center text-muted-foreground text-xs"
          >
            {snippetQuery.isFetchingNextPage
              ? "正在加载更多…"
              : snippetQuery.hasNextPage
                ? "继续向下滚动加载"
                : "已加载全部片段"}
          </div>
        </div>
      )}

      {editTarget !== undefined ? (
        <SnippetEditDialog initial={editTarget} onClose={() => setEditTarget(undefined)} />
      ) : null}
    </div>
  )
}
