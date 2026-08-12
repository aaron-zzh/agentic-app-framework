/**
 * Studio 媒体/资产库。
 *
 * media 展示所有持久化媒体；asset 仅展示用户明确保存的资产。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  BookmarkPlus,
  Box,
  ChevronDown,
  ChevronRight,
  Folder,
  FolderOpen,
  ImageIcon,
  LayoutGrid,
  Music,
  Plus,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Tags,
  Trash2,
  Video
} from "lucide-react"
import { useId, useMemo, useState } from "react"
import VideoPlugin from "yet-another-react-lightbox/plugins/video"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import {
  Popover,
  PopoverContent,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger
} from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { AigcAsset, AigcAssetTag, AigcMedia, AigcMediaType } from "@/features/aigc/types"
import {
  type AssetCategoryOption,
  type AssetFilterOption,
  useAssetFilterOptions,
  useAssetList,
  useCreateAssetCategory,
  useCreateAssetCollection,
  useCreateAssetTag,
  useDeleteAssetCategory,
  useDeleteAssetCollection,
  useDeleteAssetTag,
  useMediaList,
  useReplaceAssetTags,
  useSaveMediaAsAsset
} from "@/lib/api/rest/media"
import { notify } from "@/lib/notification"
import { cn } from "@/lib/utils/index"

const PAGE_SIZE = 20
const MEDIA_TYPES: Array<{ value: AigcMediaType | "ALL"; label: string }> = [
  { value: "ALL", label: "全部" },
  { value: "IMAGE", label: "图像" },
  { value: "VIDEO", label: "视频" },
  { value: "AUDIO", label: "配音" },
  { value: "MUSIC", label: "音乐" },
  { value: "MODEL_3D", label: "3D" }
]

export interface AssetLibraryProps {
  collection?: "media" | "asset"
}

export function AssetLibrary({ collection = "media" }: AssetLibraryProps) {
  return collection === "asset" ? <SavedAssetLibrary /> : <MediaLibrary />
}

function MediaLibrary() {
  const [pageNo, setPageNo] = useState(1)
  const [keyword, setKeyword] = useState("")
  const [mediaType, setMediaType] = useState<AigcMediaType | "ALL">("ALL")
  const { data, isLoading, refetch } = useMediaList({
    pageNo,
    pageSize: PAGE_SIZE,
    keyword: keyword.trim() || undefined,
    mediaType: mediaType === "ALL" ? undefined : mediaType
  })

  return (
    <LibraryView
      title="素材"
      description="上传或生成的持久化媒体；需要跨项目复用时可进一步登记为资产。"
      items={data?.list ?? []}
      total={data?.total ?? 0}
      pageNo={pageNo}
      keyword={keyword}
      mediaType={mediaType}
      isLoading={isLoading}
      onKeywordChange={(value) => {
        setKeyword(value)
        setPageNo(1)
      }}
      onTypeChange={(value) => {
        setMediaType(value)
        setPageNo(1)
      }}
      onPageChange={setPageNo}
      onRefresh={() => refetch()}
    />
  )
}

const UNCATEGORIZED = "UNCATEGORIZED" as const

type CategorySelection = number | typeof UNCATEGORIZED | null
type QuickCreateKind = "category" | "collection"

interface AssetCategoryNode extends AssetCategoryOption {
  children: AssetCategoryNode[]
}

function buildCategoryTree(categories: AssetCategoryOption[]): AssetCategoryNode[] {
  const nodes = new Map<number, AssetCategoryNode>()
  for (const category of categories) nodes.set(category.id, { ...category, children: [] })

  const roots: AssetCategoryNode[] = []
  for (const node of nodes.values()) {
    const parent = node.parentId === null ? undefined : nodes.get(node.parentId)
    if (parent) parent.children.push(node)
    else roots.push(node)
  }

  const sortNodes = (items: AssetCategoryNode[]) => {
    items.sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)
    for (const item of items) sortNodes(item.children)
  }
  sortNodes(roots)
  return roots
}

function collectCategoryBranchIds(categories: AssetCategoryOption[], categoryId: number): number[] {
  const childrenByParent = new Map<number, number[]>()
  for (const category of categories) {
    if (category.parentId === null) continue
    const children = childrenByParent.get(category.parentId) ?? []
    children.push(category.id)
    childrenByParent.set(category.parentId, children)
  }

  const result: number[] = []
  const visited = new Set<number>()
  const visit = (id: number) => {
    if (visited.has(id)) return
    visited.add(id)
    result.push(id)
    for (const childId of childrenByParent.get(id) ?? []) visit(childId)
  }
  visit(categoryId)
  return result
}

function SavedAssetLibrary() {
  const [pageNo, setPageNo] = useState(1)
  const [keyword, setKeyword] = useState("")
  const [mediaType, setMediaType] = useState<AigcMediaType | "ALL">("ALL")
  const [categorySelection, setCategorySelection] = useState<CategorySelection>(null)
  const [tagIds, setTagIds] = useState<number[]>([])
  const [collectionId, setCollectionId] = useState<number | null>(null)
  const [quickCreateKind, setQuickCreateKind] = useState<QuickCreateKind | null>(null)
  const filterOptions = useAssetFilterOptions()
  const createCategory = useCreateAssetCategory()
  const createCollection = useCreateAssetCollection()
  const categoryTree = useMemo(
    () => buildCategoryTree(filterOptions.categories),
    [filterOptions.categories]
  )
  const categoryIds = useMemo(
    () =>
      typeof categorySelection === "number"
        ? collectCategoryBranchIds(filterOptions.categories, categorySelection)
        : [],
    [categorySelection, filterOptions.categories]
  )
  const { data, isLoading, refetch } = useAssetList({
    pageNo,
    pageSize: PAGE_SIZE,
    keyword: keyword.trim() || undefined,
    mediaType: mediaType === "ALL" ? undefined : mediaType,
    categoryIds: categoryIds.length > 0 ? categoryIds.map(String) : undefined,
    uncategorized: categorySelection === UNCATEGORIZED ? true : undefined,
    tagIds: tagIds.length > 0 ? tagIds.map(String) : undefined,
    collectionId: collectionId ?? undefined
  })

  const selectCategory = (value: CategorySelection) => {
    setCategorySelection(value)
    setPageNo(1)
  }
  const selectCollection = (value: number | null) => {
    setCollectionId(value)
    setPageNo(1)
  }
  const resetNavigation = () => {
    setCategorySelection(null)
    setCollectionId(null)
    setPageNo(1)
  }
  const handleCategoryDeleted = (id: number) => {
    if (categorySelection === id) setCategorySelection(null)
    setPageNo(1)
  }
  const handleCollectionDeleted = (id: number) => {
    if (collectionId === id) setCollectionId(null)
    setPageNo(1)
  }
  const createParentId = typeof categorySelection === "number" ? categorySelection : null
  const createParentName =
    createParentId === null
      ? null
      : (filterOptions.categories.find((category) => category.id === createParentId)?.label ?? null)

  const navigationProps: AssetNavigationContentProps = {
    categoryTree,
    collections: filterOptions.collections,
    isLoading: filterOptions.isLoading,
    categorySelection,
    collectionId,
    onReset: resetNavigation,
    onSelectCategory: selectCategory,
    onSelectCollection: selectCollection,
    onCreateCategory: () => setQuickCreateKind("category"),
    onCreateCollection: () => setQuickCreateKind("collection"),
    onCategoryDeleted: handleCategoryDeleted,
    onCollectionDeleted: handleCollectionDeleted
  }

  const handleQuickCreate = (name: string) => {
    if (quickCreateKind === "category") {
      createCategory.mutate(
        { name, parentId: createParentId, sortOrder: 0 },
        {
          onSuccess: (category) => {
            selectCategory(category.id)
            setQuickCreateKind(null)
            notify.success(`分类「${category.name}」已创建`)
          }
        }
      )
      return
    }
    if (quickCreateKind === "collection") {
      createCollection.mutate(
        { name, collectionType: "CUSTOM", description: null },
        {
          onSuccess: (collection) => {
            selectCollection(collection.id)
            setQuickCreateKind(null)
            notify.success(`集合「${collection.name}」已创建`)
          }
        }
      )
    }
  }

  return (
    <div className="flex h-full min-h-0">
      <aside className="hidden w-60 shrink-0 border-foreground/6 border-r bg-background/20 lg:flex">
        <AssetNavigationContent {...navigationProps} />
      </aside>

      <div className="min-w-0 flex-1">
        <LibraryView
          title="资产库"
          description="这里只展示已明确保存为资产的媒体，文案内容不会进入此列表。"
          items={(data?.list ?? []).map((asset) => asset.media)}
          assets={data?.list ?? []}
          tagOptions={filterOptions.tags}
          total={data?.total ?? 0}
          pageNo={pageNo}
          keyword={keyword}
          mediaType={mediaType}
          isLoading={isLoading}
          filters={
            <>
              <AssetNavigationPopover {...navigationProps} />
              <AssetTagFilter
                values={tagIds}
                options={filterOptions.tags}
                disabled={filterOptions.isLoading}
                onValueChange={(values) => {
                  setTagIds(values)
                  setPageNo(1)
                }}
              />
            </>
          }
          onKeywordChange={(value) => {
            setKeyword(value)
            setPageNo(1)
          }}
          onTypeChange={(value) => {
            setMediaType(value)
            setPageNo(1)
          }}
          onPageChange={setPageNo}
          onRefresh={() => refetch()}
        />
      </div>

      {quickCreateKind ? (
        <QuickCreateAssetGroupDialog
          kind={quickCreateKind}
          parentName={quickCreateKind === "category" ? createParentName : null}
          isPending={createCategory.isPending || createCollection.isPending}
          onCreate={handleQuickCreate}
          onClose={() => setQuickCreateKind(null)}
        />
      ) : null}
    </div>
  )
}

interface AssetNavigationContentProps {
  categoryTree: AssetCategoryNode[]
  collections: AssetFilterOption[]
  isLoading: boolean
  categorySelection: CategorySelection
  collectionId: number | null
  onReset: () => void
  onSelectCategory: (value: CategorySelection) => void
  onSelectCollection: (value: number | null) => void
  onCreateCategory: () => void
  onCreateCollection: () => void
  onCategoryDeleted: (id: number) => void
  onCollectionDeleted: (id: number) => void
}

function AssetNavigationContent({
  categoryTree,
  collections,
  isLoading,
  categorySelection,
  collectionId,
  onReset,
  onSelectCategory,
  onSelectCollection,
  onCreateCategory,
  onCreateCollection,
  onCategoryDeleted,
  onCollectionDeleted
}: AssetNavigationContentProps) {
  const [collectionsOpen, setCollectionsOpen] = useState(true)

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="px-3 py-3">
        <h2 className="font-medium text-sm">资产导航</h2>
      </div>
      <ScrollArea className="min-h-0 flex-1">
        <div className="flex flex-col gap-3 px-2 pb-4">
          <button
            type="button"
            onClick={onReset}
            className={cn(
              "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors",
              categorySelection === null && collectionId === null
                ? "bg-primary/10 font-medium text-primary"
                : "text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
            )}
          >
            <Box className="size-4" />
            全部资产
          </button>

          <section className="flex flex-col gap-1">
            <div className="flex items-center justify-between px-2">
              <span className="font-medium text-muted-foreground text-xs">分类</span>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="新增资产分类"
                onClick={onCreateCategory}
              >
                <Plus />
              </Button>
            </div>
            <button
              type="button"
              onClick={() => onSelectCategory(null)}
              className={cn(
                "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors",
                categorySelection === null
                  ? "bg-foreground/8 text-foreground"
                  : "text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
              )}
            >
              <FolderOpen className="size-4" />
              全部分类
            </button>
            <button
              type="button"
              onClick={() => onSelectCategory(UNCATEGORIZED)}
              className={cn(
                "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors",
                categorySelection === UNCATEGORIZED
                  ? "bg-foreground/8 text-foreground"
                  : "text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
              )}
            >
              <Folder className="size-4" />
              未分类
            </button>
            {isLoading ? (
              <div className="flex flex-col gap-2 px-2 py-1">
                <Skeleton className="h-7" />
                <Skeleton className="h-7" />
              </div>
            ) : categoryTree.length > 0 ? (
              categoryTree.map((category) => (
                <AssetCategoryTreeItem
                  key={category.id}
                  category={category}
                  selectedId={typeof categorySelection === "number" ? categorySelection : null}
                  onSelect={onSelectCategory}
                  onDeleted={onCategoryDeleted}
                />
              ))
            ) : (
              <p className="px-2 py-2 text-muted-foreground text-xs">暂无分类</p>
            )}
          </section>

          <Separator />

          <section className="flex flex-col gap-1">
            <div className="flex items-center gap-1 px-1">
              <button
                type="button"
                onClick={() => setCollectionsOpen((open) => !open)}
                className="flex min-w-0 flex-1 items-center gap-1 rounded px-1 py-1 text-left font-medium text-muted-foreground text-xs hover:text-foreground"
              >
                {collectionsOpen ? (
                  <ChevronDown className="size-3.5" />
                ) : (
                  <ChevronRight className="size-3.5" />
                )}
                集合
              </button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="新增资产集合"
                onClick={onCreateCollection}
              >
                <Plus />
              </Button>
            </div>
            {collectionsOpen ? (
              <>
                <button
                  type="button"
                  onClick={() => onSelectCollection(null)}
                  className={cn(
                    "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors",
                    collectionId === null
                      ? "bg-foreground/8 text-foreground"
                      : "text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
                  )}
                >
                  <LayoutGrid className="size-4" />
                  全部集合
                </button>
                {isLoading ? (
                  <div className="flex flex-col gap-2 px-2 py-1">
                    <Skeleton className="h-7" />
                    <Skeleton className="h-7" />
                  </div>
                ) : collections.length > 0 ? (
                  collections.map((collection) => (
                    <AssetCollectionNavigationItem
                      key={collection.id}
                      collection={collection}
                      selected={collectionId === collection.id}
                      onSelect={onSelectCollection}
                      onDeleted={onCollectionDeleted}
                    />
                  ))
                ) : (
                  <p className="px-2 py-2 text-muted-foreground text-xs">暂无集合</p>
                )}
              </>
            ) : null}
          </section>
        </div>
      </ScrollArea>
    </div>
  )
}

function AssetCategoryTreeItem({
  category,
  selectedId,
  onSelect,
  onDeleted,
  depth = 0
}: {
  category: AssetCategoryNode
  selectedId: number | null
  onSelect: (value: CategorySelection) => void
  onDeleted: (id: number) => void
  depth?: number
}) {
  const [open, setOpen] = useState(true)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const deleteCategory = useDeleteAssetCategory()
  const hasChildren = category.children.length > 0

  return (
    <div>
      <div className="flex items-center" style={{ paddingLeft: `${depth * 12}px` }}>
        {hasChildren ? (
          <button
            type="button"
            aria-label={open ? `折叠${category.label}` : `展开${category.label}`}
            onClick={() => setOpen((value) => !value)}
            className="flex size-7 shrink-0 items-center justify-center rounded text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
          >
            {open ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}
          </button>
        ) : (
          <span className="size-7 shrink-0" />
        )}
        <button
          type="button"
          onClick={() => onSelect(category.id)}
          className={cn(
            "flex min-w-0 flex-1 items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors",
            selectedId === category.id
              ? "bg-foreground/8 text-foreground"
              : "text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
          )}
        >
          {open && hasChildren ? (
            <FolderOpen className="size-4 shrink-0" />
          ) : (
            <Folder className="size-4 shrink-0" />
          )}
          <span className="truncate">{category.label}</span>
        </button>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={`删除资产分类${category.label}`}
          onClick={() => setDeleteOpen(true)}
        >
          <Trash2 />
        </Button>
      </div>
      {open
        ? category.children.map((child) => (
            <AssetCategoryTreeItem
              key={child.id}
              category={child}
              selectedId={selectedId}
              onSelect={onSelect}
              onDeleted={onDeleted}
              depth={depth + 1}
            />
          ))
        : null}
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除资产分类"
        description={`确定删除「${category.label}」吗？如果分类下还有子分类或关联限制，后端会拒绝删除。`}
        confirmText={deleteCategory.isPending ? "删除中…" : "删除"}
        variant="destructive"
        onConfirm={() =>
          deleteCategory.mutate(category.id, {
            onSuccess: () => {
              onDeleted(category.id)
              notify.success(`分类「${category.label}」已删除`)
            }
          })
        }
      />
    </div>
  )
}

function AssetCollectionNavigationItem({
  collection,
  selected,
  onSelect,
  onDeleted
}: {
  collection: AssetFilterOption
  selected: boolean
  onSelect: (value: number | null) => void
  onDeleted: (id: number) => void
}) {
  const [deleteOpen, setDeleteOpen] = useState(false)
  const deleteCollection = useDeleteAssetCollection()

  return (
    <>
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onSelect(collection.id)}
          className={cn(
            "flex min-w-0 flex-1 items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors",
            selected
              ? "bg-foreground/8 text-foreground"
              : "text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
          )}
        >
          <LayoutGrid className="size-4 shrink-0" />
          <span className="truncate">{collection.label}</span>
        </button>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={`删除资产集合${collection.label}`}
          onClick={() => setDeleteOpen(true)}
        >
          <Trash2 />
        </Button>
      </div>
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除资产集合"
        description={`确定删除「${collection.label}」吗？集合中的资产不会被删除。`}
        confirmText={deleteCollection.isPending ? "删除中…" : "删除"}
        variant="destructive"
        onConfirm={() =>
          deleteCollection.mutate(collection.id, {
            onSuccess: () => {
              onDeleted(collection.id)
              notify.success(`集合「${collection.label}」已删除`)
            }
          })
        }
      />
    </>
  )
}

function AssetNavigationPopover(props: AssetNavigationContentProps) {
  return (
    <div className="lg:hidden">
      <Popover>
        <PopoverTrigger render={<Button variant="outline" />}>
          <SlidersHorizontal data-icon="inline-start" />
          分类与集合
        </PopoverTrigger>
        <PopoverContent className="h-[70vh] w-72 p-0" align="start">
          <AssetNavigationContent {...props} />
        </PopoverContent>
      </Popover>
    </div>
  )
}

function QuickCreateAssetGroupDialog({
  kind,
  parentName,
  isPending,
  onCreate,
  onClose
}: {
  kind: QuickCreateKind
  parentName: string | null
  isPending: boolean
  onCreate: (name: string) => void
  onClose: () => void
}) {
  const [name, setName] = useState("")
  const isCategory = kind === "category"

  return (
    <Dialog open onOpenChange={(open) => (!open ? onClose() : undefined)}>
      <DialogContent>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            const value = name.trim()
            if (value) onCreate(value)
          }}
        >
          <DialogHeader>
            <DialogTitle>{isCategory ? "新增资产分类" : "新增资产集合"}</DialogTitle>
            <DialogDescription>
              {isCategory
                ? parentName
                  ? `新分类将创建在「${parentName}」下。`
                  : "新分类将创建为根分类。"
                : "集合可跨分类组织同一专题下的资产。"}
            </DialogDescription>
          </DialogHeader>
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder={isCategory ? "分类名称" : "集合名称"}
            autoFocus
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              取消
            </Button>
            <Button type="submit" disabled={!name.trim() || isPending}>
              {isPending ? "创建中…" : "创建"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function AssetTagFilter({
  values,
  options,
  disabled,
  onValueChange
}: {
  values: number[]
  options: AssetFilterOption[]
  disabled: boolean
  onValueChange: (values: number[]) => void
}) {
  const idPrefix = useId()
  const [deleteTarget, setDeleteTarget] = useState<AssetFilterOption | null>(null)
  const deleteTag = useDeleteAssetTag()
  const selectedLabels = options
    .filter((option) => values.includes(option.id))
    .map((option) => option.label)
  const triggerLabel =
    values.length === 0
      ? "全部标签"
      : values.length === 1
        ? (selectedLabels[0] ?? "已选 1 个标签")
        : `已选 ${values.length} 个标签`

  const toggleTag = (tagId: number) => {
    onValueChange(
      values.includes(tagId) ? values.filter((value) => value !== tagId) : [...values, tagId]
    )
  }

  return (
    <>
      <Popover>
        <PopoverTrigger
          render={
            <Button
              variant="outline"
              disabled={disabled}
              className="min-w-36 justify-start font-normal"
            />
          }
        >
          <Tags data-icon="inline-start" />
          <span className="max-w-28 truncate">{triggerLabel}</span>
        </PopoverTrigger>
        <PopoverContent className="w-64" align="start">
          <PopoverHeader className="flex-row items-center justify-between">
            <PopoverTitle>标签筛选</PopoverTitle>
            {values.length > 0 ? (
              <Button variant="ghost" size="sm" onClick={() => onValueChange([])}>
                清空
              </Button>
            ) : null}
          </PopoverHeader>
          <p className="text-muted-foreground text-xs">匹配同时包含所选标签的资产</p>
          <ScrollArea className="h-56">
            <div className="flex flex-col gap-1 pr-3">
              {options.length > 0 ? (
                options.map((option) => {
                  const checkboxId = `${idPrefix}-${option.id}`
                  return (
                    <div key={option.id} className="flex items-center gap-2 rounded-md px-1 py-1.5">
                      <Checkbox
                        id={checkboxId}
                        checked={values.includes(option.id)}
                        onCheckedChange={() => toggleTag(option.id)}
                      />
                      <label
                        htmlFor={checkboxId}
                        className="min-w-0 flex-1 cursor-pointer truncate"
                      >
                        {option.label}
                      </label>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`删除资产标签${option.label}`}
                        onClick={() => setDeleteTarget(option)}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  )
                })
              ) : (
                <p className="py-6 text-center text-muted-foreground text-sm">暂无标签</p>
              )}
            </div>
          </ScrollArea>
        </PopoverContent>
      </Popover>
      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null)
        }}
        title="删除资产标签"
        description={
          deleteTarget
            ? `确定删除「${deleteTarget.label}」吗？删除后，使用该标签的资产将不再带有此标签。`
            : undefined
        }
        confirmText={deleteTag.isPending ? "删除中…" : "删除"}
        variant="destructive"
        onConfirm={() => {
          if (!deleteTarget) return
          const tagId = deleteTarget.id
          deleteTag.mutate(tagId, {
            onSuccess: () => {
              onValueChange(values.filter((value) => value !== tagId))
              notify.success(`标签「${deleteTarget.label}」已删除`)
              setDeleteTarget(null)
            }
          })
        }}
      />
    </>
  )
}

interface LibraryViewProps {
  title: string
  description: string
  items: AigcMedia[]
  assets?: AigcAsset[]
  tagOptions?: AssetFilterOption[]
  total: number
  pageNo: number
  keyword: string
  mediaType: AigcMediaType | "ALL"
  isLoading: boolean
  filters?: React.ReactNode
  onKeywordChange: (value: string) => void
  onTypeChange: (value: AigcMediaType | "ALL") => void
  onPageChange: (page: number) => void
  onRefresh: () => void
}

function LibraryView({
  title,
  description,
  items,
  assets,
  tagOptions = [],
  total,
  pageNo,
  keyword,
  mediaType,
  isLoading,
  filters,
  onKeywordChange,
  onTypeChange,
  onPageChange,
  onRefresh
}: LibraryViewProps) {
  const slides = items
    .filter((media) => media.mediaType === "IMAGE" || media.mediaType === "VIDEO")
    .map((media) =>
      media.mediaType === "VIDEO"
        ? {
            type: "video" as const,
            sources: [
              { src: media.currentVersion.url, type: media.currentVersion.mimeType ?? "video/mp4" }
            ]
          }
        : { src: media.currentVersion.url }
    )
  const { open, index, onOpen, onClose } = useLightbox(slides)
  const assetsByMediaId = useMemo(
    () => new Map((assets ?? []).map((asset) => [asset.mediaId, asset])),
    [assets]
  )
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="flex h-full flex-col gap-4 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-semibold text-xl">{title}</h1>
          <p className="mt-1 text-muted-foreground text-sm">{description}</p>
        </div>
        <Button variant="outline" onClick={onRefresh}>
          <RefreshCw />
          刷新
        </Button>
      </header>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-64 flex-1">
          <Search className="absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            placeholder="搜索名称"
            className="pl-9"
          />
        </div>
        {filters}
        <Tabs
          value={mediaType}
          onValueChange={(value) => onTypeChange(value as AigcMediaType | "ALL")}
        >
          <TabsList>
            {MEDIA_TYPES.map((type) => (
              <TabsTrigger key={type.value} value={type.value}>
                {type.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {Array.from({ length: 10 }).map((_, itemIndex) => (
            <Skeleton key={`media-skeleton-${itemIndex}`} className="aspect-square rounded-xl" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-12 text-center">
            <ImageIcon className="text-muted-foreground" />
            <p className="font-medium">暂无内容</p>
            <p className="text-muted-foreground text-sm">完成生成或保存资产后将在这里展示。</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {items.map((media) => (
            <MediaCard
              key={media.id}
              media={media}
              asset={assetsByMediaId.get(media.id)}
              tagOptions={tagOptions}
              onPreview={onOpen}
            />
          ))}
        </div>
      )}

      {total > PAGE_SIZE && (
        <div className="mt-auto flex items-center justify-center gap-3 pt-2">
          <Button variant="outline" disabled={pageNo <= 1} onClick={() => onPageChange(pageNo - 1)}>
            上一页
          </Button>
          <span className="text-muted-foreground text-sm">
            {pageNo} / {pageCount}
          </span>
          <Button
            variant="outline"
            disabled={pageNo >= pageCount}
            onClick={() => onPageChange(pageNo + 1)}
          >
            下一页
          </Button>
        </div>
      )}

      <Lightbox open={open} index={index} slides={slides} close={onClose} plugins={[VideoPlugin]} />
    </div>
  )
}

function MediaCard({
  media,
  asset,
  tagOptions,
  onPreview
}: {
  media: AigcMedia
  asset?: AigcAsset
  tagOptions: AssetFilterOption[]
  onPreview: (url: string) => void
}) {
  const version = media.currentVersion
  const isAudio = media.mediaType === "AUDIO" || media.mediaType === "MUSIC"
  const saveAsAsset = useSaveMediaAsAsset()
  const isSaved = asset !== undefined || media.assetId != null

  return (
    <Card className="gap-3">
      <CardHeader>
        <CardTitle className="truncate text-sm">{media.name}</CardTitle>
        <CardDescription>{media.mediaType}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {media.mediaType === "IMAGE" ? (
          <button
            type="button"
            className="aspect-square overflow-hidden rounded-lg bg-muted"
            onClick={() => onPreview(version.url)}
          >
            {/* biome-ignore lint/performance/noImgElement: 动态媒体缩略图 */}
            <img
              src={version.thumbnailUrl ?? version.url}
              alt={media.name}
              className="size-full object-cover"
            />
          </button>
        ) : media.mediaType === "VIDEO" ? (
          <button
            type="button"
            className="relative aspect-square overflow-hidden rounded-lg bg-muted"
            onClick={() => onPreview(version.url)}
          >
            <video src={version.url} muted preload="metadata" className="size-full object-cover" />
            <Video className="-translate-1/2 absolute inset-1/2 text-white" />
          </button>
        ) : isAudio ? (
          <div className="flex aspect-square flex-col items-center justify-center gap-3 rounded-lg bg-muted p-3">
            <Music className="text-muted-foreground" />
            <audio controls src={version.url} className="w-full">
              <track kind="captions" />
            </audio>
          </div>
        ) : (
          <a
            href={version.url}
            target="_blank"
            rel="noreferrer"
            className="flex aspect-square flex-col items-center justify-center gap-2 rounded-lg bg-muted text-muted-foreground hover:text-foreground"
          >
            <Box />
            打开 3D
          </a>
        )}
        {asset ? (
          <AssetCardTagEditor asset={asset} options={tagOptions} />
        ) : isSaved ? (
          <Badge variant="secondary">已保存资产</Badge>
        ) : (
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={saveAsAsset.isPending}
            onClick={() =>
              saveAsAsset.mutate(
                { mediaId: media.id },
                { onSuccess: () => notify.success("素材已保存到资产库") }
              )
            }
          >
            <BookmarkPlus /> {saveAsAsset.isPending ? "正在保存…" : "保存为资产"}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}

function AssetCardTagEditor({
  asset,
  options
}: {
  asset: AigcAsset
  options: AssetFilterOption[]
}) {
  const assetTags = useMemo(() => asset.tags ?? [], [asset.tags])
  const checkboxPrefix = useId()
  const [open, setOpen] = useState(false)
  const [draftTagIds, setDraftTagIds] = useState<number[]>(() => assetTags.map((tag) => tag.id))
  const [newTagName, setNewTagName] = useState("")
  const [createdTags, setCreatedTags] = useState<AigcAssetTag[]>([])
  const createTag = useCreateAssetTag()
  const replaceTags = useReplaceAssetTags()

  const availableOptions = useMemo(() => {
    const byId = new Map(options.map((option) => [option.id, option]))
    for (const tag of [...assetTags, ...createdTags]) {
      if (byId.has(tag.id)) continue
      byId.set(tag.id, {
        resource: null,
        id: tag.id,
        label: tag.name,
        imageUrl: null
      })
    }
    return Array.from(byId.values())
  }, [assetTags, createdTags, options])

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (nextOpen) {
      setDraftTagIds(assetTags.map((tag) => tag.id))
      setNewTagName("")
    }
  }

  const toggleTag = (tagId: number) => {
    setDraftTagIds((current) =>
      current.includes(tagId)
        ? current.filter((currentId) => currentId !== tagId)
        : [...current, tagId]
    )
  }

  const createAndSelectTag = () => {
    const name = newTagName.trim()
    if (!name) return
    createTag.mutate(
      { name, color: null },
      {
        onSuccess: (tag) => {
          setCreatedTags((current) =>
            current.some((currentTag) => currentTag.id === tag.id) ? current : [...current, tag]
          )
          setDraftTagIds((current) => (current.includes(tag.id) ? current : [...current, tag.id]))
          setNewTagName("")
          notify.success(`标签「${tag.name}」已创建并选中`)
        }
      }
    )
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger
        render={
          <button
            type="button"
            className="flex min-h-8 w-full items-center gap-1.5 overflow-hidden rounded-md border border-foreground/8 px-2 py-1 text-left text-muted-foreground transition-colors hover:bg-foreground/[0.04] hover:text-foreground"
            aria-label={`标记资产 ${asset.media.name}`}
          />
        }
      >
        {assetTags.length > 0 ? (
          <>
            {assetTags.slice(0, 2).map((tag) => (
              <Badge key={tag.id} variant="outline" className="max-w-24 gap-1 font-normal">
                <span
                  className="size-1.5 shrink-0 rounded-full bg-muted-foreground"
                  style={tag.color ? { backgroundColor: tag.color } : undefined}
                />
                <span className="truncate">{tag.name}</span>
              </Badge>
            ))}
            {assetTags.length > 2 ? (
              <span className="shrink-0 text-xs">+{assetTags.length - 2}</span>
            ) : null}
          </>
        ) : (
          <>
            <Tags className="size-3.5 shrink-0" />
            <span className="truncate text-xs">添加标签</span>
          </>
        )}
        <Plus className="ml-auto size-3.5 shrink-0" />
      </PopoverTrigger>
      <PopoverContent className="w-72" align="start">
        <PopoverHeader>
          <PopoverTitle>标记资产</PopoverTitle>
        </PopoverHeader>
        <p className="text-muted-foreground text-xs">选择已有标签，或新建标签后应用到资产。</p>

        <form
          className="flex gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            createAndSelectTag()
          }}
        >
          <Input
            value={newTagName}
            onChange={(event) => setNewTagName(event.target.value)}
            placeholder="新建标签"
            className="h-8 text-xs"
            disabled={createTag.isPending}
          />
          <Button
            type="submit"
            size="icon-sm"
            variant="outline"
            disabled={!newTagName.trim() || createTag.isPending}
            aria-label="创建并选中标签"
          >
            <Plus />
          </Button>
        </form>

        <ScrollArea className="h-48">
          <div className="flex flex-col gap-1 pr-3">
            {availableOptions.length > 0 ? (
              availableOptions.map((option) => {
                const checkboxId = `${checkboxPrefix}-${asset.id}-${option.id}`
                return (
                  <div key={option.id} className="flex items-center gap-2 rounded-md px-1 py-1.5">
                    <Checkbox
                      id={checkboxId}
                      checked={draftTagIds.includes(option.id)}
                      onCheckedChange={() => toggleTag(option.id)}
                    />
                    <label
                      htmlFor={checkboxId}
                      className="min-w-0 flex-1 cursor-pointer truncate text-sm"
                    >
                      {option.label}
                    </label>
                  </div>
                )
              })
            ) : (
              <p className="py-6 text-center text-muted-foreground text-sm">暂无标签</p>
            )}
          </div>
        </ScrollArea>

        <div className="flex items-center justify-between border-foreground/8 border-t pt-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={draftTagIds.length === 0 || replaceTags.isPending}
            onClick={() => setDraftTagIds([])}
          >
            清空
          </Button>
          <Button
            type="button"
            size="sm"
            disabled={replaceTags.isPending}
            onClick={() =>
              replaceTags.mutate(
                { assetId: asset.id, tagIds: draftTagIds },
                {
                  onSuccess: () => {
                    setOpen(false)
                    notify.success("资产标签已更新")
                  }
                }
              )
            }
          >
            {replaceTags.isPending ? "保存中…" : `应用标记（${draftTagIds.length}）`}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
