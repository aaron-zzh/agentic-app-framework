/**
 * ReferenceDropZone——生成面板参考素材拖放区
 * 支持：① dnd-kit 从素材库拖入；② 系统文件拖放；③ 点击上传按钮选文件
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import { Loader2, Plus, Upload, X } from "lucide-react"
import { useRef, useState } from "react"
import { toast } from "sonner"
import { API_ORIGIN } from "@/lib/api/config"
import { backendApi } from "@/lib/api/rest/backend-client"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "../store"
import type { MediaAssetVO } from "../types"

async function uploadImageFile(file: File): Promise<MediaAssetVO> {
  const form = new FormData()
  form.append("file", file)
  const res = await backendApi.post<{ url: string; id?: number; name?: string }>(
    "/system/files/upload-image",
    form,
    // 删除全局 Content-Type，让 axios 自动为 FormData 设置 multipart/form-data; boundary=...
    { headers: { "Content-Type": undefined } }
  )
  // 构造一个临时的 MediaAssetVO 用于展示
  const fullUrl = res.url.startsWith("http") ? res.url : `${API_ORIGIN}${res.url}`
  return {
    id: res.id ?? Date.now(),
    name: file.name,
    type: "IMAGE",
    url: fullUrl,
    thumbnailUrl: fullUrl
  } as unknown as MediaAssetVO
}

export function ReferenceDropZone({
  max = 16,
  isEditMode = false
}: {
  max?: number
  isEditMode?: boolean
}) {
  const { isOver, setNodeRef } = useDroppable({ id: "generation-drop-zone" })
  const referenceAssets = useAigcStore((s) => s.referenceAssets)
  const removeReferenceAsset = useAigcStore((s) => s.removeReferenceAsset)
  const addReferenceAsset = useAigcStore((s) => s.addReferenceAsset)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [fileDragOver, setFileDragOver] = useState(false)

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return
    if (referenceAssets.length >= max) {
      toast.error(`最多添加 ${max} 张参考图`)
      return
    }
    setUploading(true)
    try {
      for (const file of Array.from(files)) {
        if (!file.type.startsWith("image/")) continue
        const asset = await uploadImageFile(file)
        addReferenceAsset(asset)
      }
    } catch {
      toast.error("上传失败，请重试")
    } finally {
      setUploading(false)
    }
  }

  // 系统文件拖放（与 dnd-kit 拖拽不冲突）
  function onFileDragOver(e: React.DragEvent) {
    if (e.dataTransfer.types.includes("Files")) {
      e.preventDefault()
      setFileDragOver(true)
    }
  }

  function onFileDragLeave() {
    setFileDragOver(false)
  }

  function onFileDrop(e: React.DragEvent) {
    if (!e.dataTransfer.types.includes("Files")) return
    e.preventDefault()
    setFileDragOver(false)
    handleFiles(e.dataTransfer.files)
  }

  return (
    <section
      ref={setNodeRef}
      aria-label="参考素材拖放区"
      className={cn(
        "flex min-h-[96px] flex-col gap-2 rounded-lg border border-border/50 border-dashed bg-muted/30 p-3 transition-colors",
        (isOver || fileDragOver) && "border-primary bg-primary/5"
      )}
      onDragOver={onFileDragOver}
      onDragLeave={onFileDragLeave}
      onDrop={onFileDrop}
    >
      {/* 缩略图网格 */}
      {referenceAssets.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {referenceAssets.map((asset) => (
            <div
              key={asset.id}
              className="group relative size-14 overflow-hidden rounded-md bg-muted"
            >
              {/* biome-ignore lint/performance/noImgElement: 动态参考素材缩略图 */}
              <img
                src={asset.thumbnailUrl ?? undefined}
                alt={asset.name}
                className="size-full object-cover"
              />
              <button
                type="button"
                onClick={() => removeReferenceAsset(asset.id)}
                className="absolute -top-1 -right-1 hidden size-4 items-center justify-center rounded-full bg-destructive text-destructive-foreground group-hover:flex"
              >
                <X className="size-3" />
              </button>
            </div>
          ))}
        </div>
      )}

      {/* 底部：提示文字 + 计数 + 上传按钮 */}
      <div className="mt-auto flex items-center gap-2">
        {referenceAssets.length === 0 && (
          <span className="flex flex-1 items-center gap-1.5 text-muted-foreground text-xs">
            <Upload className="size-3.5 shrink-0" />
            拖拽素材或文件到此处作为参考
          </span>
        )}
        {referenceAssets.length > 0 && isEditMode && (
          <span className="flex items-center gap-1 rounded-full bg-violet-500/15 px-2 py-0.5 font-medium text-violet-500 text-xs">
            <svg className="size-3" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path
                d="M11 2L14 5L5 14H2V11L11 2Z"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinejoin="round"
              />
            </svg>
            图像编辑
          </span>
        )}
        <div className="ml-auto flex shrink-0 items-center gap-2">
          <span className="text-muted-foreground text-xs">
            {referenceAssets.length}/{max}
          </span>
          <button
            type="button"
            disabled={uploading}
            onClick={() => fileInputRef.current?.click()}
            className="flex size-7 items-center justify-center rounded-lg border border-border/60 bg-background text-muted-foreground transition-colors hover:bg-muted disabled:opacity-50"
          >
            {uploading ? (
              <Loader2 className="size-3.5 animate-spin" />
            ) : (
              <Plus className="size-3.5" />
            )}
          </button>
        </div>
      </div>

      {/* 隐藏文件选择器 */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        multiple
        className="hidden"
        onChange={(e) => handleFiles(e.target.files)}
      />
    </section>
  )
}
