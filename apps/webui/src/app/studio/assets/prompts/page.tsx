/**
 * /studio/assets/prompts——统一提示词资产的系统、我的与工作区公开视图。
 * @author AaronZZH & Kiro
 */

"use client"

import { Copy, Pencil, Plus, Sparkles, Trash2 } from "lucide-react"
import { useId, useMemo, useState } from "react"
import { GlassCard, GlowButton } from "@/components/studio"
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
import {
  type PromptTemplateAssetVO,
  type PromptTemplateVisibility,
  useCopyPromptTemplate,
  useCreatePromptTemplate,
  useDeletePromptTemplate,
  useMyPromptTemplates,
  usePromptTemplateMeta,
  usePublicPromptTemplates,
  useSystemPromptTemplates,
  useUpdatePromptTemplate
} from "@/lib/api/rest/ai"

type AssetView = "SYSTEM" | "MINE" | "WORKSPACE"

const VIEW_COPY: Record<AssetView, { title: string; description: string; empty: string }> = {
  SYSTEM: {
    title: "系统提示词",
    description: "平台维护的全局提示词，可使用或复制到我的资产",
    empty: "暂无系统提示词"
  },
  MINE: {
    title: "我的提示词",
    description: "管理你在当前组织或工作区创建的提示词",
    empty: "还没有提示词资产"
  },
  WORKSPACE: {
    title: "当前工作区公开",
    description: "当前组织或工作区成员公开共享的提示词",
    empty: "当前工作区暂无公开提示词"
  }
}

const VISIBILITY_LABEL: Record<PromptTemplateVisibility, string> = {
  ENGINE: "引擎内部",
  SYSTEM: "系统",
  PRIVATE: "私有",
  PUBLIC: "公开"
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
  const [prompt, setPrompt] = useState(initial?.prompt ?? "")
  const [isPublic, setIsPublic] = useState(initial?.visibility === "PUBLIC")
  const create = useCreatePromptTemplate()
  const update = useUpdatePromptTemplate()

  function handleSave() {
    if (!name.trim() || !prompt.trim()) return
    const input = {
      name: name.trim(),
      category: category.trim() || "DEFAULT",
      prompt: prompt.trim(),
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
      <DialogContent>
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
          <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div className="flex flex-col gap-0.5">
              <Label htmlFor={`${uid}-public`}>工作区公开</Label>
              <p className="text-muted-foreground text-xs">允许当前组织或工作区成员使用和复制</p>
            </div>
            <Switch
              id={`${uid}-public`}
              checked={isPublic}
              onCheckedChange={setIsPublic}
              aria-label="工作区公开"
            />
          </div>
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
  return value === "SYSTEM" || value === "MINE" || value === "WORKSPACE"
}

export default function StudioAssetsPromptsPage() {
  const [view, setView] = useState<AssetView>("SYSTEM")
  const [editTarget, setEditTarget] = useState<PromptTemplateAssetVO | null | undefined>(undefined)
  const systemQuery = useSystemPromptTemplates({ page: 0, size: 100 }, view === "SYSTEM")
  const publicQuery = usePublicPromptTemplates({ page: 0, size: 100 }, view === "WORKSPACE")
  const mineQuery = useMyPromptTemplates({ pageNo: 1, pageSize: 100 }, view === "MINE")
  const metaQuery = usePromptTemplateMeta()
  const deletePrompt = useDeletePromptTemplate()
  const copyPrompt = useCopyPromptTemplate()

  const operations = useMemo(
    () => new Set(metaQuery.data?.operations ?? []),
    [metaQuery.data?.operations]
  )
  const activeQuery =
    view === "SYSTEM" ? systemQuery : view === "WORKSPACE" ? publicQuery : mineQuery
  const assets = activeQuery.data?.list ?? []
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
          <TabsTrigger value="SYSTEM">系统</TabsTrigger>
          <TabsTrigger value="MINE">我的</TabsTrigger>
          <TabsTrigger value="WORKSPACE">当前工作区公开</TabsTrigger>
        </TabsList>
      </Tabs>

      <div>
        <h2 className="font-medium text-base">{viewCopy.title}</h2>
        <p className="mt-1 text-muted-foreground text-sm">{viewCopy.description}</p>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 5 }).map((_, index) => (
            <Skeleton key={`prompt-asset-skeleton-${index}`} className="h-20 w-full rounded-xl" />
          ))}
        </div>
      ) : assets.length === 0 ? (
        <Empty className="min-h-64 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Sparkles />
            </EmptyMedia>
            <EmptyTitle>{viewCopy.empty}</EmptyTitle>
            <EmptyDescription>
              {view === "MINE"
                ? "创建提示词后，可选择仅自己使用或公开到当前工作区。"
                : viewCopy.description}
            </EmptyDescription>
          </EmptyHeader>
          {view === "MINE" && canCreate ? (
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
              (view === "SYSTEM" && asset.visibility === "SYSTEM") ||
              (view === "WORKSPACE" && asset.visibility === "PUBLIC")
            return (
              <GlassCard key={asset.id} glow="none" className="border border-foreground/6">
                <div className="flex items-start gap-3 p-4">
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
        </div>
      )}

      {editTarget !== undefined ? (
        <EditDialog open onClose={() => setEditTarget(undefined)} initial={editTarget} />
      ) : null}
    </div>
  )
}
