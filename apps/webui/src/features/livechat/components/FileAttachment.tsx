/**
 * FileAttachment——文件附件展示组件
 * 文件列表（图标+文件名+大小），图片显示缩略图，点击打开 lightbox
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { FileIcon, ImageIcon } from "lucide-react"
import { useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogTitle
} from "@/components/ui/dialog"

export interface FileItem {
  id: string
  name: string
  url: string
  size: number
  type: string
}

interface FileAttachmentProps {
  files: FileItem[]
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function isImage(type: string): boolean {
  return type.startsWith("image/")
}

export function FileAttachment({ files }: FileAttachmentProps) {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  if (files.length === 0) return null

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {files.map((file) => (
          <div key={file.id} className="flex items-center gap-2 rounded-md border p-2">
            {isImage(file.type) ? (
              <button
                type="button"
                className="shrink-0 cursor-pointer"
                onClick={() => setPreviewUrl(file.url)}
              >
                {/* biome-ignore lint/performance/noImgElement: 缩略图为动态 URL */}
                <img
                  src={file.url}
                  alt={file.name}
                  className="size-10 rounded object-cover"
                />
              </button>
            ) : (
              <FileIcon className="size-5 shrink-0 text-muted-foreground" />
            )}
            <div className="min-w-0">
              <p className="truncate text-sm">{file.name}</p>
              <p className="text-muted-foreground text-xs">{formatSize(file.size)}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Lightbox 预览 */}
      <Dialog open={!!previewUrl} onOpenChange={(open) => !open && setPreviewUrl(null)}>
        <DialogContent className="sm:max-w-3xl">
          <DialogTitle className="sr-only">图片预览</DialogTitle>
          {previewUrl && (
            // biome-ignore lint/performance/noImgElement: lightbox 预览为动态 URL
            <img
              src={previewUrl}
              alt="预览"
              className="max-h-[80vh] w-full object-contain"
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
