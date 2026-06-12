/**
 * 素材组卡片
 * - 折叠态：单张封面图 + 底部标签行（九宫格图标 + 组名 + 箭头）
 * - 展开态：横向滚动多图行 + 底部标签行（可收起）+ 支持拖入改组
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import { useQueryClient } from "@tanstack/react-query"
import { ChevronRight, Download, LayoutGrid, MessageSquarePlus, Plus, Trash2 } from "lucide-react"
import { useRef, useState } from "react"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { backendApi } from "@/lib/api/rest/backend-client"
import { mediaAssetApi } from "@/lib/api/rest/media/media-asset"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "../store"
import type { MediaAssetVO } from "../types"
import { DraggableAssetCard } from "./DraggableAssetCard"

interface AssetGroupCardProps {
  groupId: number
  groupName: string
  assets: MediaAssetVO[]
  /** 单列宽度（px），来自缩放比例，用于展开态内容宽度计算 */
  colWidth?: number
}

export function AssetGroupCard({
  groupId,
  groupName,
  assets,
  colWidth = 160
}: AssetGroupCardProps) {
  const canExpand = assets.length > 1
  const [expanded, setExpanded] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const coverUrl = assets[0]?.thumbnailUrl ?? assets[0]?.url
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const queryClient = useQueryClient()

  function handleDeleteGroup() {
    // 本地立即移除，UI 无感知延迟
    const deletedIds = new Set(assets.map((a) => a.id))
    queryClient.setQueriesData<{ list: MediaAssetVO[]; total: number }>(
      { queryKey: ["media-assets"] },
      (old) =>
        old
          ? {
              ...old,
              list: old.list.filter((a) => !deletedIds.has(a.id)),
              total: old.total - assets.length
            }
          : old
    )
    // 后台删除 + 静默 invalidate 兜底同步
    mediaAssetApi
      .deleteGroup(groupId)
      .then(() => queryClient.invalidateQueries({ queryKey: ["media-assets"] }))
      .catch(() => {
        // 删除失败时回滚：重新拉取
        queryClient.invalidateQueries({ queryKey: ["media-assets"] })
      })
  }

  const { addPendingTask, completePendingTask, failPendingTask, removePendingTask } = useAigcStore()

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    if (!files.length) return
    e.target.value = ""

    // 每个文件立即加占位卡，并行上传
    files.forEach((file) => {
      const tempId = Date.now() + Math.floor(Math.random() * 1000)
      const localUrl = URL.createObjectURL(file)
      // 立即用本地预览 URL 占位，显示真实图片
      const tempAsset = {
        id: tempId,
        name: file.name,
        type: "IMAGE" as const,
        url: localUrl,
        thumbnailUrl: localUrl,
        size: file.size,
        width: null,
        height: null,
        duration: null,
        generationParams: null,
        tags: null,
        categoryId: null,
        groupId,
        userId: 0,
        version: 0,
        createTime: "",
        updateTime: "",
        groupName: null,
        aiGenerated: false,
        modelName: null,
        providerCode: null
      }
      addPendingTask({ id: tempId, prompt: file.name, type: "IMAGE" })
      // 直接把临时 asset 写入（completePendingTask 第三参）让卡片立即显示真图
      completePendingTask(tempId, localUrl, tempAsset)

      const form = new FormData()
      form.append("file", file)
      backendApi
        .post<{ url: string }>("/system/files/upload-image", form, {
          headers: { "Content-Type": undefined }
        })
        .then(async (res) => {
          const asset = await backendApi.post<MediaAssetVO>("/aigc/assets", {
            name: file.name,
            type: "IMAGE",
            url: res.url,
            thumbnailUrl: res.url,
            size: file.size,
            groupId
          })
          completePendingTask(tempId, res.url, asset)
          URL.revokeObjectURL(localUrl)
          setTimeout(() => {
            removePendingTask(tempId)
            queryClient.invalidateQueries({ queryKey: ["media-assets"] })
          }, 1500)
        })
        .catch(() => {
          failPendingTask(tempId, "上传失败")
          setTimeout(() => removePendingTask(tempId), 3000)
        })
    })
  }
  const previewAsset = useAigcStore((s) => s.previewAsset)
  const isGroupSelected = assets.some((a) => a.id === previewAsset?.id)

  const setPreviewAsset = useAigcStore((s) => s.setPreviewAsset)
  const firstAsset = assets[0]
  const { setNodeRef, isOver } = useDroppable({ id: `group-${groupId}` })

  const actionBar = (
    <div
      className={cn(
        "relative flex items-center overflow-hidden rounded-lg border px-2 py-1.5 transition-colors",
        isGroupSelected ? "border-primary bg-primary/5" : "border-border/40 bg-card/50"
      )}
    >
      {/* 左侧图标按钮：hover 时滑入，点击弹出操作菜单 */}
      <Popover open={menuOpen} onOpenChange={setMenuOpen}>
        {/* 隐藏锚点，供 base-ui 定位浮层 */}
        <PopoverTrigger
          className="absolute top-1/2 left-2 size-0 -translate-y-1/2 opacity-0"
          tabIndex={-1}
        />
        <button
          type="button"
          className="absolute top-1/2 left-2 z-10 -translate-x-8 -translate-y-1/2 rounded border border-border/50 bg-background p-1 text-muted-foreground opacity-0 transition-all duration-200 hover:bg-muted group-hover:translate-x-0 group-hover:opacity-100"
          onClick={(e) => {
            e.stopPropagation()
            setMenuOpen(true)
          }}
        >
          <LayoutGrid className="size-3" />
        </button>
        <PopoverContent side="top" align="start" className="w-44 p-1">
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted"
            onClick={(e) => e.stopPropagation()}
          >
            <MessageSquarePlus className="size-3.5 text-muted-foreground" />
            添加到对话
          </button>
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted"
            onClick={(e) => {
              e.stopPropagation()
              setMenuOpen(false)
              fileInputRef.current?.click()
            }}
          >
            <Plus className="size-3.5 text-muted-foreground" />
            添加素材
          </button>
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted"
            onClick={(e) => e.stopPropagation()}
          >
            <Download className="size-3.5 text-muted-foreground" />
            下载所有素材
          </button>
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-destructive text-sm hover:bg-muted"
            onClick={(e) => {
              e.stopPropagation()
              setMenuOpen(false)
              setDeleteConfirmOpen(true)
            }}
          >
            <Trash2 className="size-3.5" />
            删除所有素材
          </button>
        </PopoverContent>
      </Popover>
      {/* 组名 + 展开箭头 */}
      <button
        type="button"
        onClick={() => canExpand && setExpanded((v) => !v)}
        onKeyDown={(e) =>
          (e.key === "Enter" || e.key === " ") && canExpand && setExpanded((v) => !v)
        }
        className="flex w-full min-w-0 cursor-pointer items-center gap-1 transition-[padding] duration-200 group-hover:pl-6"
      >
        <span
          className={cn(
            "min-w-0 flex-1 truncate text-[11px]",
            isGroupSelected ? "font-medium text-primary" : "text-foreground"
          )}
        >
          {groupName}
        </span>
        {canExpand && (
          <ChevronRight
            className={cn(
              "size-3.5 shrink-0 transition-transform duration-200",
              isGroupSelected ? "text-primary" : "text-muted-foreground",
              expanded && "rotate-90"
            )}
          />
        )}
      </button>
    </div>
  )

  if (!expanded) {
    return (
      <div className="group rounded-lg">
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          multiple
          className="hidden"
          onChange={handleFileChange}
        />
        <ConfirmDialog
          open={deleteConfirmOpen}
          onOpenChange={setDeleteConfirmOpen}
          title={`删除 ${assets.length} 张素材`}
          description="此操作将永久删除组内所有素材及文件，无法恢复。确认继续？"
          confirmText="删除"
          variant="destructive"
          onConfirm={handleDeleteGroup}
        />
        <button
          type="button"
          className={cn(
            "relative z-10 aspect-square w-full cursor-pointer overflow-hidden rounded-[6px] outline outline-1 outline-transparent transition-[outline-color]",
            isGroupSelected ? "outline-primary" : "hover:outline-primary/40"
          )}
          onPointerUp={() => firstAsset && setPreviewAsset(firstAsset)}
        >
          {coverUrl && (
            // biome-ignore lint/performance/noImgElement: 素材组封面
            <img src={coverUrl} alt={groupName} className="size-full object-cover" />
          )}
        </button>
        <div className="mt-1">{actionBar}</div>
      </div>
    )
  }

  const colSpan = Math.min(assets.length, 5)
  /** 展开态 bar 宽度 = colSpan 列宽 + (colSpan-1) 间距 + 左右 padding */
  const expandedBarWidth = colSpan * colWidth + (colSpan - 1) * 6 + 12

  return (
    <div
      ref={setNodeRef}
      style={{ gridColumn: `span ${colSpan}` }}
      className={cn("group overflow-hidden rounded-lg transition-colors", isOver && "bg-primary/5")}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={handleFileChange}
      />
      <div className="flex gap-1.5 overflow-x-auto p-1.5">
        {assets.map((asset) => (
          <div key={asset.id} style={{ width: colWidth, minWidth: colWidth }} className="shrink-0">
            <DraggableAssetCard asset={asset} groupId={groupId} />
          </div>
        ))}
      </div>
      <div className="p-1.5 pt-0" style={{ width: expandedBarWidth }}>
        {actionBar}
      </div>
    </div>
  )
}
