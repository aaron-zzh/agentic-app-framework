/**
 * 素材库——瀑布流展示 + 分类筛选 + 详情弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronLeft, ChevronRight, Layers, Plus, RefreshCw, Search, X } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import Video from "yet-another-react-lightbox/plugins/video"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { SceneLayout } from "@/components/r3f/SceneLayout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  useCreateCategory,
  useDeleteCategory,
  useDeleteMediaAsset,
  useMediaAssetList,
  useMediaCategories,
  useMediaTags
} from "@/lib/queries/use-media-assets"
import { cn } from "@/lib/utils/index"
import type { MediaAssetType, MediaCategoryVO } from "../types"
import { AssetCard } from "./AssetCard"
import { AssetDetailDialog } from "./AssetDetailDialog"

function CategoryTree({
  categories,
  selectedId,
  onSelect,
  onDelete
}: {
  categories: MediaCategoryVO[]
  selectedId: number | null
  onSelect: (id: number | null) => void
  onDelete?: (id: number) => void
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <button
        type="button"
        className={cn(
          "rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-muted",
          selectedId === null && "bg-muted font-medium"
        )}
        onClick={() => onSelect(null)}
      >
        全部分类
      </button>
      {categories.map((cat) => (
        <div key={cat.id} className="group/cat relative">
          <button
            type="button"
            className={cn(
              "w-full rounded-md px-2 py-1.5 pr-6 text-left text-sm transition-colors hover:bg-muted",
              selectedId === cat.id && "bg-muted font-medium"
            )}
            onClick={() => onSelect(cat.id)}
          >
            {cat.name}
          </button>
          {onDelete && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation()
                onDelete(cat.id)
              }}
              className="absolute top-1/2 right-1.5 hidden -translate-y-1/2 rounded p-0.5 text-muted-foreground opacity-60 hover:bg-destructive/10 hover:text-destructive hover:opacity-100 group-hover/cat:flex"
              aria-label="删除分类"
            >
              <X className="size-3" />
            </button>
          )}
          {cat.children.length > 0 && (
            <div className="ml-3">
              <CategoryTree
                categories={cat.children}
                selectedId={selectedId}
                onSelect={onSelect}
                onDelete={onDelete}
              />
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

export function AssetLibrary() {
  const [search, setSearch] = useState("")
  const [typeFilter, setTypeFilter] = useState<MediaAssetType | "ALL">("ALL")
  const [sourceFilter, setSourceFilter] = useState<"ALL" | "AI" | "UPLOAD">("ALL")
  const [sort, setSort] = useState<"newest" | "oldest">("newest")
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [selectedTags, setSelectedTags] = useState<number[]>([])
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<number | null>(null)

  const deleteMutation = useDeleteMediaAsset()
  const createCategoryMutation = useCreateCategory()
  const deleteCategoryMutation = useDeleteCategory()
  const { data: categories } = useMediaCategories()
  const { data: tags } = useMediaTags()
  const [newCatName, setNewCatName] = useState("")

  const { data, isLoading, refetch } = useMediaAssetList({
    page,
    pageSize: 20,
    sort: sort === "newest" ? "createTime:desc" : "createTime:asc",
    search: search || undefined,
    ...(typeFilter !== "ALL" && { type: typeFilter }),
    ...(categoryId && { categoryId }),
    ...(sourceFilter === "AI" && { aiGenerated: true }),
    ...(sourceFilter === "UPLOAD" && { aiGenerated: false })
  })

  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)

  const slides = (data?.list ?? [])
    .filter((a) => a.type === "IMAGE" || a.type === "VIDEO")
    .map((a) =>
      a.type === "VIDEO"
        ? { type: "video" as const, sources: [{ src: a.url, type: "video/mp4" }] }
        : { src: a.url }
    )
  const { open: lbOpen, index: lbIndex, onOpen: openLb, onClose: closeLb } = useLightbox(slides)

  function handleDelete(id: number) {
    setDeleteTarget(id)
  }

  return (
    <SceneLayout>
      <div className="flex h-full flex-col">
        <div className="flex flex-wrap items-center gap-3 border-b px-4 py-3">
          <Button
            variant="ghost"
            size="icon"
            className="size-8 shrink-0"
            onClick={() => refetch().then(() => toast.success("已刷新"))}
            title="刷新"
          >
            <RefreshCw className="size-4" />
          </Button>
          <Tabs
            value={sourceFilter}
            onValueChange={(v) => {
              setSourceFilter(v as "ALL" | "AI" | "UPLOAD")
              setPage(0)
            }}
          >
            <TabsList className="h-8">
              <TabsTrigger value="ALL" className="text-xs">
                全部
              </TabsTrigger>
              <TabsTrigger value="AI" className="text-xs">
                作品
              </TabsTrigger>
              <TabsTrigger value="UPLOAD" className="text-xs">
                素材
              </TabsTrigger>
            </TabsList>
          </Tabs>
          <div className="relative ml-auto w-64">
            <Search className="absolute top-2.5 left-2.5 size-4 text-muted-foreground" />
            <Input
              placeholder="搜索..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              className="pl-9"
            />
            {search && (
              <Button
                variant="ghost"
                size="icon"
                className="absolute top-1 right-1 size-6"
                onClick={() => setSearch("")}
              >
                <X className="size-3" />
              </Button>
            )}
          </div>
          <Tabs
            value={typeFilter}
            onValueChange={(v) => {
              setTypeFilter(v as MediaAssetType | "ALL")
              setPage(0)
            }}
          >
            <TabsList className="h-8">
              <TabsTrigger value="ALL" className="text-xs">
                全部
              </TabsTrigger>
              <TabsTrigger value="IMAGE" className="text-xs">
                图片
              </TabsTrigger>
              <TabsTrigger value="VIDEO" className="text-xs">
                视频
              </TabsTrigger>
              <TabsTrigger value="AUDIO" className="text-xs">
                配音
              </TabsTrigger>
              <TabsTrigger value="MUSIC" className="text-xs">
                音乐
              </TabsTrigger>
              <TabsTrigger value="MODEL_3D" className="text-xs">
                3D 模型
              </TabsTrigger>
            </TabsList>
          </Tabs>
          <Select value={sort} onValueChange={(v) => setSort(v as "newest" | "oldest")}>
            <SelectTrigger className="h-8 w-24 text-xs">
              <SelectValue>{sort === "newest" ? "最新" : "最旧"}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="newest">最新</SelectItem>
              <SelectItem value="oldest">最旧</SelectItem>
            </SelectContent>
          </Select>
          <Button
            variant="ghost"
            size="icon"
            className="size-8"
            onClick={() => setSidebarOpen(!sidebarOpen)}
          >
            {sidebarOpen ? <ChevronLeft className="size-4" /> : <ChevronRight className="size-4" />}
          </Button>
        </div>

        <div className="flex flex-1 overflow-hidden">
          {sidebarOpen && (
            <aside className="w-56 shrink-0 overflow-y-auto border-r p-3">
              <p className="mb-2 font-medium text-muted-foreground text-xs">分类</p>
              {categories ? (
                <CategoryTree
                  categories={categories}
                  selectedId={categoryId}
                  onSelect={(id) => {
                    setCategoryId(id)
                    setPage(0)
                  }}
                  onDelete={(id) => deleteCategoryMutation.mutate(id)}
                />
              ) : (
                <div className="flex flex-col gap-1">
                  <Skeleton className="h-7 w-full" />
                  <Skeleton className="h-7 w-full" />
                  <Skeleton className="h-7 w-full" />
                </div>
              )}
              {/* 新增分类 */}
              <form
                className="mt-2 flex gap-1"
                onSubmit={(e) => {
                  e.preventDefault()
                  const name = newCatName.trim()
                  if (!name) return
                  createCategoryMutation.mutate({ name }, { onSuccess: () => setNewCatName("") })
                }}
              >
                <Input
                  value={newCatName}
                  onChange={(e) => setNewCatName(e.target.value)}
                  placeholder="新增分类..."
                  className="h-7 text-xs"
                />
                <Button
                  type="submit"
                  size="icon"
                  variant="ghost"
                  className="size-7 shrink-0"
                  disabled={!newCatName.trim() || createCategoryMutation.isPending}
                >
                  <Plus className="size-3" />
                </Button>
              </form>
              {tags && tags.length > 0 && (
                <div className="mt-4">
                  <p className="mb-2 font-medium text-muted-foreground text-xs">标签</p>
                  <div className="flex flex-wrap gap-1">
                    {tags.map((tag) => (
                      <Badge
                        key={tag.id}
                        variant={selectedTags.includes(tag.id) ? "default" : "outline"}
                        className="cursor-pointer text-[10px]"
                        style={
                          tag.color
                            ? {
                                borderColor: tag.color,
                                color: selectedTags.includes(tag.id) ? "#fff" : tag.color,
                                backgroundColor: selectedTags.includes(tag.id)
                                  ? tag.color
                                  : undefined
                              }
                            : undefined
                        }
                        onClick={() => {
                          setSelectedTags((prev) =>
                            prev.includes(tag.id)
                              ? prev.filter((t) => t !== tag.id)
                              : [...prev, tag.id]
                          )
                          setPage(0)
                        }}
                      >
                        {tag.name}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </aside>
          )}

          <ScrollArea className="flex-1">
            <div className="p-4">
              {isLoading ? (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                  {Array.from({ length: 12 }).map((_, i) => (
                    <Skeleton key={`skel-${i}`} className="aspect-square w-full rounded-lg" />
                  ))}
                </div>
              ) : data && data.list?.length > 0 ? (
                <>
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                    {data.list?.map((asset) => (
                      <AssetCard
                        key={asset.id}
                        asset={asset}
                        onClick={
                          asset.type === "IMAGE" || asset.type === "VIDEO"
                            ? () => openLb(asset.url)
                            : () => setDetailId(asset.id)
                        }
                        onDelete={() => handleDelete(asset.id)}
                        onPreview={() => setDetailId(asset.id)}
                      />
                    ))}
                  </div>
                  {data.total > 20 && (
                    <div className="mt-4 flex items-center justify-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={page === 0}
                        onClick={() => setPage((p) => p - 1)}
                      >
                        上一页
                      </Button>
                      <span className="text-muted-foreground text-sm">
                        {page + 1} / {Math.ceil(data.total / 20)}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={(page + 1) * 20 >= data.total}
                        onClick={() => setPage((p) => p + 1)}
                      >
                        下一页
                      </Button>
                    </div>
                  )}
                </>
              ) : (
                <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                  <Layers className="mb-2 size-10" />
                  <p>暂无素材</p>
                </div>
              )}
            </div>
          </ScrollArea>
        </div>

        <AssetDetailDialog
          assetId={detailId}
          open={detailId !== null}
          onOpenChange={(open) => {
            if (!open) setDetailId(null)
          }}
        />

        <Lightbox open={lbOpen} index={lbIndex} slides={slides} close={closeLb} plugins={[Video]} />

        <Dialog
          open={deleteTarget !== null}
          onOpenChange={(open) => {
            if (!open) setDeleteTarget(null)
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>确认删除</DialogTitle>
            </DialogHeader>
            <p className="text-muted-foreground text-sm">确定删除该素材？此操作不可撤销。</p>
            <div className="flex justify-end gap-2 pt-4">
              <Button variant="outline" onClick={() => setDeleteTarget(null)}>
                取消
              </Button>
              <Button
                variant="destructive"
                onClick={() => {
                  if (deleteTarget !== null) {
                    deleteMutation.mutate(deleteTarget)
                    setDeleteTarget(null)
                  }
                }}
              >
                删除
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>
    </SceneLayout>
  )
}
