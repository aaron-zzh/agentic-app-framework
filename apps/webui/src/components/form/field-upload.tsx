/**
 * Field.Upload——RHF 图片/文件上传控件
 * @author AaronZZH & Kiro
 *
 * 集成 useFormImageUpload，支持单文件/多文件、图像压缩、OSS 直传
 *
 * @example
 * ```tsx
 * <Field.Upload name="cover" label="封面图" accept="image/*" />
 * <Field.Upload name="attachments" label="附件" multiple maxSize={20} />
 * ```
 */

"use client"

import { useRef } from "react"
import { Controller, useFormContext } from "react-hook-form"

import { Label } from "@/components/ui/label"
import { useFormImageUpload } from "@/lib/hooks/use-form-image-upload"
import type { ImageUploadOptions } from "@/lib/hooks/use-image-upload"
import { cn } from "@/lib/utils/cn"

export interface FieldUploadProps {
  name: string
  label?: string
  /** 多文件模式 */
  multiple?: boolean
  /** 接受的文件类型，默认 image/* */
  accept?: string
  /** 最大文件大小（MB），默认 10 */
  maxSize?: number
  /** 占位文字 */
  placeholder?: string
  className?: string
  disabled?: boolean
  /** 图像上传配置 */
  imageOptions?: ImageUploadOptions
  /** 上传后获取图片尺寸 */
  onImageLoad?: (width: number, height: number) => void
}

export function FieldUpload({
  name,
  label,
  multiple = false,
  accept = "image/*",
  maxSize = 10,
  placeholder,
  className,
  disabled,
  imageOptions,
  onImageLoad
}: FieldUploadProps) {
  const { control } = useFormContext()
  const {
    uploading,
    progress,
    disabled: uploadDisabled,
    onDrop,
    onDelete,
    onRemove,
    onRemoveAll
  } = useFormImageUpload({ name, multiple, imageOptions, onImageLoad })

  const isDisabled = disabled || uploadDisabled

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label && <Label htmlFor={name}>{label}</Label>}
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <>
            <DropZone
              value={field.value}
              multiple={multiple}
              accept={accept}
              maxSize={maxSize}
              placeholder={placeholder}
              disabled={isDisabled}
              uploading={uploading}
              progress={progress}
              onDrop={onDrop}
              onDelete={onDelete}
              onRemove={onRemove}
              onRemoveAll={onRemoveAll}
              error={!!error}
            />
            {error && <p className="text-destructive text-xs">{error.message}</p>}
          </>
        )}
      />
    </div>
  )
}

// ─── 内部 DropZone 组件 ─────────────────────────────────────────────────────

interface DropZoneProps {
  value: string | string[] | undefined
  multiple: boolean
  accept: string
  maxSize: number
  placeholder?: string
  disabled: boolean
  uploading: boolean
  progress: number
  error: boolean
  onDrop: (files: File[]) => void
  onDelete: () => void
  onRemove: (url: string) => void
  onRemoveAll: () => void
}

function DropZone({
  value,
  multiple,
  accept,
  maxSize,
  placeholder,
  disabled,
  uploading,
  progress,
  error,
  onDrop,
  onDelete,
  onRemove,
  onRemoveAll
}: DropZoneProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    if (disabled) return
    const files = Array.from(e.dataTransfer.files).filter((f) => f.size <= maxSize * 1024 * 1024)
    if (files.length > 0) onDrop(files)
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files) return
    const files = Array.from(e.target.files).filter((f) => f.size <= maxSize * 1024 * 1024)
    if (files.length > 0) onDrop(files)
    e.target.value = ""
  }

  const hasValue = multiple
    ? Array.isArray(value) && value.length > 0
    : !!value && typeof value === "string"

  return (
    <div className="space-y-2">
      {/* 单文件已有值时显示预览 */}
      {!multiple && hasValue && typeof value === "string" ? (
        <div className="relative inline-block">
          {/* biome-ignore lint/performance/noImgElement: 远程 URL 预览 */}
          <img src={value} alt={value.split('/').pop() || '已上传图片'} className="h-32 w-32 rounded-lg border object-cover" />
          <button
            type="button"
            className="absolute -top-2 -right-2 flex h-5 w-5 items-center justify-center rounded-full bg-destructive text-white text-xs"
            onClick={onDelete}
            disabled={disabled}
          >
            ✕
          </button>
        </div>
      ) : (
        /* 拖拽区域 */
        /* biome-ignore lint/a11y/useSemanticElements: 拖拽上传区域 */
        <div
          className={cn(
            "relative flex min-h-[100px] cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-4 transition-colors",
            error ? "border-destructive" : "border-muted-foreground/25 hover:border-primary/50",
            disabled && "pointer-events-none opacity-50"
          )}
          onDragOver={handleDragOver}
          onDrop={handleDrop}
          onClick={() => inputRef.current?.click()}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") inputRef.current?.click()
          }}
          role="button"
          tabIndex={disabled ? -1 : 0}
          aria-label="上传文件"
        >
          <input
            ref={inputRef}
            type="file"
            className="hidden"
            multiple={multiple}
            accept={accept}
            onChange={handleFileSelect}
            disabled={disabled}
          />
          <span className="text-xl">📁</span>
          <p className="mt-1 text-muted-foreground text-sm">{placeholder ?? "拖拽或点击上传"}</p>
          <p className="text-muted-foreground text-xs">最大 {maxSize}MB</p>
          {uploading && (
            <div className="absolute inset-x-4 bottom-2">
              <div className="h-1 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full bg-primary transition-all"
                  style={{ width: `${progress}%` }}
                />
              </div>
            </div>
          )}
        </div>
      )}

      {/* 多文件列表 */}
      {multiple && Array.isArray(value) && value.length > 0 && (
        <div className="space-y-1">
          <ul className="space-y-1">
            {value.map((url: string) => (
              <li key={url} className="flex items-center gap-2 rounded border px-3 py-1.5">
                {/* biome-ignore lint/performance/noImgElement: 远程 URL 缩略图 */}
                <img src={url} alt="" className="h-8 w-8 rounded object-cover" />
                <span className="flex-1 truncate text-muted-foreground text-xs">
                  {url.split("/").pop()}
                </span>
                <button
                  type="button"
                  className="text-muted-foreground text-xs hover:text-destructive"
                  onClick={() => onRemove(url)}
                  disabled={disabled}
                >
                  ✕
                </button>
              </li>
            ))}
          </ul>
          {value.length > 1 && (
            <button
              type="button"
              className="text-muted-foreground text-xs hover:text-destructive"
              onClick={onRemoveAll}
              disabled={disabled}
            >
              移除全部
            </button>
          )}
        </div>
      )}
    </div>
  )
}
