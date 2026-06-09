/**
 * 素材库——瀑布流展示 + 分类筛选 + 详情弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronLeft, ChevronRight, Layers, Search, X } from "lucide-react"
import { useState } from "react"
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
  useDeleteMediaAsset,
  useMediaAssetList,
  useMediaCategories,
  useMediaTags,
  useRegenerateAsset
} from "@/lib/queries/use-media-assets"
import { cn } from "@/lib/utils/index"
import { AssetCard } from "./AssetCard"
import { AssetDetailDialog } from "./AssetDetailDialog"
import type { MediaAssetType, MediaCategoryVO } from "./types"

/** 分类树节点 */
function CategoryTree({
  categories,
  selectedId,
  onSelect
}: {
  categories: MediaCategoryVO[]
  selectedId: number | null
  onSelect: (id: number | null) => void
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
        <div key={cat.id}>
          <button
            type="button"
            className={cn(
              "w-full rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-muted",
              selectedId === cat.id && "bg-muted font-medium"
            )}
            onClick={() => onSelect(cat.id)}
          >
            {cat.name}
          </button>
          {cat.children.length > 0 && (
            <div className="ml-3">
              <CategoryTree categories={cat.children} selectedId={selectedId} onSelect={onSelect} />
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

/** 素材库主组件 */
export function AssetLibrary() {
  const [search, setSearch] = useState("")
  const [typeFilter, setTypeFilter] = useState<MediaAssetType | "ALL">("ALL")
  const [sort, setSort] = useState<"newest" | "oldest">("newest")
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [selectedTags, setSelectedTags] = useState<number[]>([])
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<number | null>(null)

  const deleteMutation = useDeleteMediaAsset()
  const regenerateMutation = useRegenerateAsset()
  const { data: categories } = useMediaCategories()
  const { data: tags } = useMediaTags()

  const { data, isLoading } = useMediaAssetList({
    page,
    pageSize: 20,
    sort: sort === "newest" ? "createTime:desc" : "createTime:asc",
    search: search || undefined,
    ...(typeFilter !== "ALL" && { type: typeFilter }),
    ...(categoryId && { categoryId })
  })

  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)

  function handleDelete(id: number) {
    setDeleteTarget(id)
  }

  function handleRegenerate(id: number) {
    regenerateMutation.mutate({ assetId: id })
  }

  return (
    <div className="flex h-full flex-col">
      {/* 顶部工具栏 */}
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-3">
        <h1 className="font-semibold text-lg">素材库</h1>

        {/* 搜索框 */}
        <div className="relative ml-auto w-64">
          <Search className="absolute top-2.5 left-2.5 size-4 text-muted-foreground" />
          <Input
            placeholder="搜索素材..."
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

        {/* 类型筛选 */}
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
            <TabsTrigger value="MODEL_3D" className="text-xs">
              3D 模型
            </TabsTrigger>
          </TabsList>
        </Tabs>

        {/* 排序 */}
        <Select value={sort} onValueChange={(v) => setSort(v as "newest" | "oldest")}>
          <SelectTrigger className="h-8 w-24 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="newest">最新</SelectItem>
            <SelectItem value="oldest">最旧</SelectItem>
          </SelectContent>
        </Select>

        {/* 侧边栏切换 */}
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
        {/* 左侧边栏 */}
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
              />
            ) : (
              <div className="flex flex-col gap-1">
                <Skeleton className="h-7 w-full" />
                <Skeleton className="h-7 w-full" />
                <Skeleton className="h-7 w-full" />
              </div>
            )}

            {/* 标签筛选 */}
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
                              backgroundColor: selectedTags.includes(tag.id) ? tag.color : undefined
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

        {/* 主体：瀑布流网格 */}
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
                      onClick={() => setDetailId(asset.id)}
                      onDelete={() => handleDelete(asset.id)}
                      onRegenerate={() => handleRegenerate(asset.id)}
                    />
                  ))}
                </div>

                {/* 分页 */}
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

      {/* 详情弹窗 */}
      <AssetDetailDialog
        assetId={detailId}
        open={detailId !== null}
        onOpenChange={(open) => {
          if (!open) setDetailId(null)
        }}
      />

      {/* 删除确认弹窗 */}
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
  )
}
