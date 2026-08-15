/**
 * AIGC 资产封面上传控件：上传图片并将结果 URL 回写给表单。
 * @author AaronZZH & Kiro
 */

"use client"

import { ImageIcon, Trash2, Upload } from "lucide-react"
import type { ChangeEvent } from "react"
import { useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { CoverThumbnail } from "./CoverThumbnail"

interface CoverImageUploadProps {
  id: string
  value?: string | null
  onChange: (url: string | null) => void
  disabled?: boolean
}

/** 上传 image/* 封面，展示预览、进度、状态和移除入口。 */
export function CoverImageUpload({ id, value, onChange, disabled = false }: CoverImageUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const { upload, uploading, progress } = useFileUpload({
    storagePurpose: "PUBLIC_ASSET",
    maxWidth: 1024,
    maxHeight: 1024,
    outputFormat: "image/webp"
  })

  async function handleFile(file: File) {
    if (!file.type.startsWith("image/")) {
      setError("请选择图片文件")
      return
    }
    setError(null)
    try {
      const result = await upload(file)
      onChange(result.url)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : "封面上传失败")
    }
  }

  function handleInputChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ""
    if (file) void handleFile(file)
  }

  function handleRemove() {
    setError(null)
    onChange(null)
  }

  const status = error
    ? error
    : uploading
      ? `上传中 ${progress}%`
      : value
        ? "封面已上传"
        : "支持任意图片格式"

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>封面（可选）</Label>
      <div className="flex items-center gap-3">
        <CoverThumbnail
          src={value}
          alt="资产封面"
          fallback={<ImageIcon className="size-4" aria-hidden />}
          className="size-14 border border-foreground/8"
        />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <input
            ref={inputRef}
            id={id}
            type="file"
            accept="image/*"
            className="sr-only"
            disabled={disabled || uploading}
            onChange={handleInputChange}
          />
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={disabled || uploading}
              onClick={() => inputRef.current?.click()}
            >
              <Upload data-icon="inline-start" />
              {value ? "替换封面" : "上传封面"}
            </Button>
            {value ? (
              <Button
                type="button"
                size="sm"
                variant="ghost"
                disabled={disabled || uploading}
                onClick={handleRemove}
              >
                <Trash2 data-icon="inline-start" />
                移除
              </Button>
            ) : null}
          </div>
          {uploading ? <Progress value={progress} aria-label="封面上传进度" /> : null}
          <p className={error ? "text-destructive text-xs" : "text-muted-foreground text-xs"}>
            {status}
          </p>
        </div>
      </div>
    </div>
  )
}
