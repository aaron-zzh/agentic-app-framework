/**
 * /studio/assets/prompts——统一提示词资产的我的与公共视图。
 * @author AaronZZH & Kiro
 */

"use client"

import { Copy, Pencil, Plus, Search, Sparkles, Trash2 } from "lucide-react"
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
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
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
  type PromptTemplateAssetVO,
  type PromptTemplateVisibility,
  useCopyPromptTemplate,
  useCreatePromptTemplate,
  useDeletePromptTemplate,
  useInfiniteMyPromptTemplates,
  useInfinitePublicPromptTemplates,
  usePromptTemplateMeta,
  useUpdatePromptTemplate
} from "@/lib/api/rest/ai"

type AssetView = "MINE" | "PUBLIC"

const VIEW_COPY: Record<AssetView, { title: string; description: string; empty: string }> = {
  MINE: {
    title: "我的提示词",
    description: "管理你在当前组织或工作区创建的提示词",
    empty: "还没有提示词资产"
  },
  PUBLIC: {
    title: "公共提示词",
    description: "浏览平台内置和当前组织或工作区成员公开共享的提示词",
    empty: "暂无公共提示词"
  }
}

const VISIBILITY_LABEL: Record<PromptTemplateVisibility, string> = {
  ENGINE: "引擎内部",
  SYSTEM: "内置",
  PRIVATE: "私有",
  PUBLIC: "工作区公开"
}

function optionalNumber(value: string) {
  const normalized = value.trim()
  if (!normalized) return undefined
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

function parseVariables(value: string) {
  return Array.from(
    new Set(
      value
        .split(/[,，\n]/)
        .map((item) => item.trim())
        .filter(Boolean)
    )
  )
}

interface EditDialogProps {
  open: boolean
  onClose: () => void
  initial?: PromptTemplateAssetVO | null
}

function EditDialog({ open, onClose, initial }: EditDialogProps) {
  const uid = useId()
  const [name, setName] = useState(initial?.name ?? "")
  const [category, setCategory] = useState(initial?.category ?? "DEFAULT")
  const [coverUrl, setCoverUrl] = useState<string | null>(initial?.coverUrl ?? null)
  const [prompt, setPrompt] = useState(initial?.prompt ?? "")
  const [isPublic, setIsPublic] = useState(initial?.visibility === "PUBLIC")
  const [description, setDescription] = useState(initial?.description ?? "")
  const [negativePrompt, setNegativePrompt] = useState(initial?.negativePrompt ?? "")
  const [model, setModel] = useState(initial?.model ?? "")
  const [width, setWidth] = useState(initial?.width?.toString() ?? "")
  const [height, setHeight] = useState(initial?.height?.toString() ?? "")
  const [steps, setSteps] = useState(initial?.steps?.toString() ?? "")
  const [seed, setSeed] = useState(initial?.seed?.toString() ?? "")
  const [variables, setVariables] = useState(initial?.variables.join(", ") ?? "")
  const create = useCreatePromptTemplate()
  const update = useUpdatePromptTemplate()

  function handleSave() {
    if (!name.trim() || !prompt.trim()) return
    const input = {
      name: name.trim(),
      category: category.trim() || "DEFAULT",
      coverUrl,
      prompt: prompt.trim(),
      description: initial ? description.trim() : description.trim() || undefined,
      negativePrompt: initial ? negativePrompt.trim() : negativePrompt.trim() || undefined,
      model: initial ? model.trim() : model.trim() || undefined,
      width: optionalNumber(width),
      height: optionalNumber(height),
      steps: optionalNumber(steps),
      seed: optionalNumber(seed),
      variables: parseVariables(variables),
      isPublic
    }
    if (initial) {
      update.mutate({ id: initial.id, ...input }, { onSuccess: onClose })
      return
    }
    create.mutate({ ...input, type: "IMAGE_GEN", scope: "GENERATION" }, { onSuccess: onClose })
  }

  const isPending = create.isPending || update.isPending

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => !nextOpen && onClose()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{initial ? "编辑提示词" : "新建提示词"}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-3 py-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-name`}>名称</Label>
            <Input
              id={`${uid}-name`}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="提示词名称"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-category`}>分类</Label>
            <Input
              id={`${uid}-category`}
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              placeholder="例如：图像生成"
            />
          </div>
          <CoverImageUpload
            id={`${uid}-cover`}
            value={coverUrl}
            onChange={setCoverUrl}
            disabled={isPending}
          />
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-prompt`}>提示词内容</Label>
            <Textarea
              id={`${uid}-prompt`}
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="输入提示词…"
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
                  <Label htmlFor={`${uid}-description`}>说明</Label>
                  <Textarea
                    id={`${uid}-description`}
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    placeholder="补充用途、适用场景或使用说明"
                    className="min-h-20"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor={`${uid}-negative-prompt`}>负面提示词</Label>
                  <Textarea
                    id={`${uid}-negative-prompt`}
                    value={negativePrompt}
                    onChange={(event) => setNegativePrompt(event.target.value)}
                    placeholder="输入需要排除的内容"
                    className="min-h-20"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor={`${uid}-model`}>推荐模型</Label>
                  <Input
                    id={`${uid}-model`}
                    value={model}
                    onChange={(event) => setModel(event.target.value)}
                    placeholder="可选，例如：wanx-v1"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-width`}>宽度</Label>
                    <Input
                      id={`${uid}-width`}
                      type="number"
                      min="1"
                      value={width}
                      onChange={(event) => setWidth(event.target.value)}
                      placeholder="1024"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-height`}>高度</Label>
                    <Input
                      id={`${uid}-height`}
                      type="number"
                      min="1"
                      value={height}
                      onChange={(event) => setHeight(event.target.value)}
                      placeholder="1024"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-steps`}>步数</Label>
                    <Input
                      id={`${uid}-steps`}
                      type="number"
                      min="1"
                      value={steps}
                      onChange={(event) => setSteps(event.target.value)}
                      placeholder="30"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-seed`}>种子</Label>
                    <Input
                      id={`${uid}-seed`}
                      type="number"
                      value={seed}
                      onChange={(event) => setSeed(event.target.value)}
                      placeholder="随机"
                    />
                  </div>
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor={`${uid}-variables`}>变量</Label>
                  <Input
                    id={`${uid}-variables`}
                    value={variables}
                    onChange={(event) => setVariables(event.target.value)}
                    placeholder="用逗号分隔，例如：产品名, 风格"
                  />
                  <p className="text-muted-foreground text-xs">
                    提示词中可使用对应变量占位符，多个变量用逗号分隔。
                  </p>
                </div>
                <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
                  <div className="flex flex-col gap-0.5">
                    <Label htmlFor={`${uid}-public`}>工作区公开</Label>
                    <p className="text-muted-foreground text-xs">
                      允许当前组织或工作区成员使用和复制
                    </p>
                  </div>
                  <Switch
                    id={`${uid}-public`}
                    checked={isPublic}
                    onCheckedChange={setIsPublic}
                    aria-label="工作区公开"
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
          <Button onClick={handleSave} disabled={isPending || !name.trim() || !prompt.trim()}>
            {isPending ? "保存中…" : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function isAssetView(value: string): value is AssetView {
  return value === "MINE" || value === "PUBLIC"
}

export function PromptAssetsView() {
  const [view, setView] = useState<AssetView>("MINE")
  const [search, setSearch] = useState("")
  const [debouncedSearch] = useDebounce(search.trim(), 300)
  const [editTarget, setEditTarget] = useState<PromptTemplateAssetVO | null | undefined>(undefined)
  const searchParams = { search: debouncedSearch || undefined }
  const publicQuery = useInfinitePublicPromptTemplates(searchParams, view === "PUBLIC")
  const mineQuery = useInfiniteMyPromptTemplates(searchParams, view === "MINE")
  const metaQuery = usePromptTemplateMeta()
  const deletePrompt = useDeletePromptTemplate()
  const copyPrompt = useCopyPromptTemplate()

  const operations = useMemo(
    () => new Set(metaQuery.data?.operations ?? []),
    [metaQuery.data?.operations]
  )
  const activeQuery = view === "PUBLIC" ? publicQuery : mineQuery
  const assets = useMemo(() => {
    const byId = new Map<number, PromptTemplateAssetVO>()
    activeQuery.data?.pages.forEach((page) => {
      page.list.forEach((asset) => {
        byId.set(asset.id, asset)
      })
    })
    return [...byId.values()]
  }, [activeQuery.data?.pages])
  const loadMoreRef = useLoadMoreOnVisible({
    hasNextPage: Boolean(activeQuery.hasNextPage),
    isFetchingNextPage: activeQuery.isFetchingNextPage,
    fetchNextPage: activeQuery.fetchNextPage
  })
  const isLoading = metaQuery.isLoading || activeQuery.isLoading
  const canCreate = view === "MINE" && operations.has("create")
  const viewCopy = VIEW_COPY[view]

  function handleViewChange(value: string) {
    if (isAssetView(value)) setView(value)
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-4 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-semibold text-xl">提示词资产</h1>
          <p className="mt-1 text-muted-foreground text-sm">统一管理和复用生成式任务提示词</p>
        </div>
        {canCreate ? (
          <GlowButton tone="primary" size="sm" onClick={() => setEditTarget(null)}>
            <Plus data-icon="inline-start" />
            新建
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
          placeholder="搜索名称或提示词内容"
          aria-label="搜索提示词资产"
          className="pl-9"
        />
      </div>

      <div>
        <h2 className="font-medium text-base">{viewCopy.title}</h2>
        <p className="mt-1 text-muted-foreground text-sm">{viewCopy.description}</p>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          {["one", "two", "three", "four", "five"].map((key) => (
            <Skeleton key={key} className="h-20 w-full rounded-xl" />
          ))}
        </div>
      ) : assets.length === 0 ? (
        <Empty className="min-h-64 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Sparkles />
            </EmptyMedia>
            <EmptyTitle>{debouncedSearch ? "没有匹配的提示词" : viewCopy.empty}</EmptyTitle>
            <EmptyDescription>
              {debouncedSearch
                ? "尝试更换名称或内容关键词。"
                : view === "MINE"
                  ? "创建提示词后，可选择仅自己使用或公开到当前工作区。"
                  : viewCopy.description}
            </EmptyDescription>
          </EmptyHeader>
          {!debouncedSearch && view === "MINE" && canCreate ? (
            <EmptyContent>
              <Button onClick={() => setEditTarget(null)}>
                <Plus data-icon="inline-start" />
                新建提示词
              </Button>
            </EmptyContent>
          ) : null}
        </Empty>
      ) : (
        <div className="flex flex-col gap-2">
          {assets.map((asset) => {
            const canWrite =
              view === "MINE" && (asset.visibility === "PRIVATE" || asset.visibility === "PUBLIC")
            const canUpdate = canWrite && operations.has("update")
            const canDelete = canWrite && operations.has("delete")
            const canCopy =
              view === "PUBLIC" && (asset.visibility === "SYSTEM" || asset.visibility === "PUBLIC")
            return (
              <GlassCard key={asset.id} glow="none" className="border border-foreground/6">
                <div className="flex items-start gap-3 p-4">
                  <CoverThumbnail
                    src={asset.coverUrl}
                    alt={`${asset.name}封面`}
                    fallback={
                      <span className="font-semibold text-sm">{asset.name.slice(0, 1)}</span>
                    }
                    className="size-12"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-medium text-sm">{asset.name}</p>
                      <Badge variant="secondary">{VISIBILITY_LABEL[asset.visibility]}</Badge>
                      {asset.category ? <Badge variant="outline">{asset.category}</Badge> : null}
                    </div>
                    <p className="mt-1 line-clamp-2 text-muted-foreground text-xs leading-5">
                      {asset.prompt}
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-1">
                    {canCopy ? (
                      <Button
                        variant="outline"
                        size="xs"
                        disabled={copyPrompt.isPending && copyPrompt.variables?.id === asset.id}
                        onClick={() => copyPrompt.mutate({ id: asset.id })}
                      >
                        <Copy data-icon="inline-start" />
                        复制到我的
                      </Button>
                    ) : null}
                    {canUpdate ? (
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => setEditTarget(asset)}
                        aria-label={`编辑${asset.name}`}
                      >
                        <Pencil />
                      </Button>
                    ) : null}
                    {canDelete ? (
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        className="text-destructive hover:text-destructive"
                        disabled={deletePrompt.isPending && deletePrompt.variables === asset.id}
                        onClick={() => deletePrompt.mutate(asset.id)}
                        aria-label={`删除${asset.name}`}
                      >
                        <Trash2 />
                      </Button>
                    ) : null}
                  </div>
                </div>
              </GlassCard>
            )
          })}
          <div
            ref={loadMoreRef}
            className="flex h-10 items-center justify-center text-muted-foreground text-xs"
          >
            {activeQuery.isFetchingNextPage
              ? "正在加载更多…"
              : activeQuery.hasNextPage
                ? "继续向下滚动加载"
                : "已加载全部提示词"}
          </div>
        </div>
      )}

      {editTarget !== undefined ? (
        <EditDialog open onClose={() => setEditTarget(undefined)} initial={editTarget} />
      ) : null}
    </div>
  )
}
