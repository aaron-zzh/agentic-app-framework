/**
 * 文案生成参考图——上传后的缩略图列表 + "+" 上传按钮
 *
 * 上限 4 张图（store 中限制），避免视觉 token 占用过大
 * key 透传给后端 referenceImageKeys；url 仅用于本地缩略图预览
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ImagePlus, Loader2, X } from "lucide-react"
import { useRef } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useAigcStore } from "../store"

const MAX_IMAGES = 4

export function CopywritingReferenceImages() {
  const inputRef = useRef<HTMLInputElement>(null)
  const images = useAigcStore((s) => s.copywritingReferenceImages)
  const addImage = useAigcStore((s) => s.addCopywritingReferenceImage)
  const removeImage = useAigcStore((s) => s.removeCopywritingReferenceImage)

  const { upload, uploading, progress } = useFileUpload({
    maxWidth: 2048,
    maxHeight: 2048,
    quality: 0.85
  })

  const reachedLimit = images.length >= MAX_IMAGES

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return

    const remainingSlots = MAX_IMAGES - images.length
    const fileArr = Array.from(files).slice(0, remainingSlots)

    if (files.length > remainingSlots) {
      toast.warning(`最多 ${MAX_IMAGES} 张参考图，已自动截取前 ${remainingSlots} 张`)
    }

    for (const file of fileArr) {
      if (!file.type.startsWith("image/")) {
        toast.error(`${file.name} 不是图片，已跳过`)
        continue
      }
      try {
        const result = await upload(file)
        if (!result.key) {
          toast.error(`${file.name} 上传失败：未获取到 fileKey`)
          continue
        }
        addImage({ key: result.key, url: result.url, name: result.name })
      } catch (e) {
        toast.error(`${file.name} 上传失败：${(e as Error).message}`)
      }
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* 已上传缩略图 */}
      {images.map((img) => (
        <div
          key={img.key}
          className="group relative size-14 rounded-md border bg-muted"
          title={img.name}
        >
          {/* biome-ignore lint/performance/noImgElement: OSS 签名 URL 域名动态，使用 next/image 需配置 remotePatterns，缩略图无 LCP 影响 */}
          <img
            src={img.url}
            alt={img.name}
            className="size-full overflow-hidden rounded-md object-cover"
          />
          <Button
            type="button"
            size="icon-sm"
            variant="secondary"
            className="absolute -top-1 -right-1 size-5 rounded-full p-0 opacity-0 shadow group-hover:opacity-100"
            onClick={() => removeImage(img.key)}
            aria-label={`移除 ${img.name}`}
          >
            <X className="size-3" />
          </Button>
        </div>
      ))}

      {/* 上传按钮 */}
      {!reachedLimit && (
        <button
          type="button"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
          className="flex size-14 shrink-0 flex-col items-center justify-center gap-1 rounded-md border border-foreground/15 border-dashed bg-foreground/[0.03] text-muted-foreground transition-colors hover:border-primary/40 hover:bg-primary/5 hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
          aria-label="上传参考图"
        >
          {uploading ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <ImagePlus className="size-4" />
          )}
          <span className="text-[10px] leading-none">参考图</span>
          {uploading ? (
            <span className="text-[9px] tabular-nums leading-none">{progress}%</span>
          ) : null}
        </button>
      )}

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        className="hidden"
        onChange={(e) => {
          handleFiles(e.target.files)
          // 重置 input，允许同名文件重新上传
          if (inputRef.current) inputRef.current.value = ""
        }}
      />

      {images.length > 0 && (
        <span className="text-[11px] text-muted-foreground">
          {images.length}/{MAX_IMAGES}
        </span>
      )}
    </div>
  )
}
