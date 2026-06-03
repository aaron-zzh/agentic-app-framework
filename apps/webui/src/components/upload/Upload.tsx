/**
 * Upload——文件上传组件（拖拽 + 点击 + 预览 + 图像压缩 + OSS 直传）
 * @author AaronZZH & Kiro
 *
 * 图像文件自动走 useImageUpload（压缩 + 预签名上传）
 * 非图像文件走普通上传
 */

"use client"

import { useCallback, useRef, useState } from "react"
import {
  type ImageUploadOptions,
  type UploadResult,
  useImageUpload
} from "@/lib/hooks/use-image-upload"

export interface UploadFile {
  file: File
  preview?: string
  /** 上传后的远程 URL */
  url?: string
  progress?: number
  status?: "pending" | "uploading" | "done" | "error"
}

interface UploadProps {
  /** 单文件/多文件 */
  multiple?: boolean
  /** 接受的文件类型 */
  accept?: string
  /** 最大文件大小（MB） */
  maxSize?: number
  /** 已选文件 */
  value?: UploadFile[]
  /** 文件变化回调 */
  onChange?: (files: UploadFile[]) => void
  /** 删除文件 */
  onRemove?: (index: number) => void
  /** 上传完成回调（返回远程 URL） */
  onUploaded?: (results: UploadResult[]) => void
  /** 占位文字 */
  placeholder?: string
  /** 错误状态 */
  error?: string
  /** 禁用 */
  disabled?: boolean
  /** 自动上传（选择后立即上传），默认 true */
  autoUpload?: boolean
  /** 图像上传配置 */
  imageOptions?: ImageUploadOptions
}

/** 文件上传组件 */
export function Upload({
  multiple = false,
  accept,
  maxSize = 10,
  value = [],
  onChange,
  onRemove,
  onUploaded,
  placeholder,
  error,
  disabled,
  autoUpload = true,
  imageOptions
}: UploadProps) {
  const [dragActive, setDragActive] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const {
    upload: uploadImage,
    uploading,
    progress
  } = useImageUpload({
    usePresign: true,
    ...imageOptions
  })

  const processFiles = useCallback(
    async (fileList: FileList) => {
      const validFiles = Array.from(fileList).filter((f) => f.size <= maxSize * 1024 * 1024)
      const newItems: UploadFile[] = validFiles.map((file) => ({
        file,
        preview: file.type.startsWith("image/") ? URL.createObjectURL(file) : undefined,
        status: "pending" as const
      }))

      const updated = multiple ? [...value, ...newItems] : newItems.slice(0, 1)
      onChange?.(updated)

      if (!autoUpload) return

      // 自动上传
      const results: UploadResult[] = []
      const finalItems = [...updated]

      for (let i = 0; i < newItems.length; i++) {
        const idx = multiple ? value.length + i : i
        const item = newItems[i]
        finalItems[idx] = { ...item, status: "uploading" }
        onChange?.([...finalItems])

        try {
          const result = await uploadImage(item.file)
          results.push(result)
          finalItems[idx] = { ...item, url: result.url, status: "done" }
        } catch {
          finalItems[idx] = { ...item, status: "error" }
        }
        onChange?.([...finalItems])
      }

      if (results.length > 0) onUploaded?.(results)
    },
    [multiple, maxSize, value, onChange, onUploaded, autoUpload, uploadImage]
  )

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setDragActive(false)
      if (disabled) return
      processFiles(e.dataTransfer.files)
    },
    [disabled, processFiles]
  )

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (e.target.files) {
        processFiles(e.target.files)
        e.target.value = ""
      }
    },
    [processFiles]
  )

  return (
    <div className="space-y-2">
      {/* 拖拽区域 */}
      {/* biome-ignore lint/a11y/useSemanticElements: 拖拽上传区域需要 div，button 不支持拖拽语义 */}
      <div
        className={`relative flex min-h-[120px] cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-4 transition-colors ${
          dragActive
            ? "border-primary bg-primary/5"
            : "border-muted-foreground/25 hover:border-primary/50"
        } ${error ? "border-destructive" : ""} ${disabled ? "pointer-events-none opacity-50" : ""}`}
        onDragOver={(e) => {
          e.preventDefault()
          setDragActive(true)
        }}
        onDragLeave={() => setDragActive(false)}
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
          onChange={handleChange}
          disabled={disabled}
        />
        <span className="text-2xl">📁</span>
        <p className="mt-2 text-muted-foreground text-sm">
          {placeholder ?? "拖拽文件到此处，或点击选择"}
        </p>
        <p className="text-muted-foreground text-xs">最大 {maxSize}MB</p>
        {uploading && (
          <div className="absolute inset-x-4 bottom-2">
            <div className="h-1 w-full overflow-hidden rounded-full bg-muted">
              <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
            </div>
          </div>
        )}
      </div>

      {error && <p className="text-destructive text-xs">{error}</p>}

      {/* 文件预览列表 */}
      {value.length > 0 && (
        <ul className="space-y-1">
          {value.map((item, i) => (
            <li
              key={`${item.file.name}-${item.file.size}-${item.file.lastModified}`}
              className="flex items-center gap-2 rounded border px-3 py-1.5"
            >
              {item.preview ? (
                // biome-ignore lint/performance/noImgElement: blob URL，next/image 不支持
                <img src={item.preview} alt="" className="h-8 w-8 rounded object-cover" />
              ) : (
                <span className="flex h-8 w-8 items-center justify-center rounded bg-muted text-xs">
                  📄
                </span>
              )}
              <span className="flex-1 truncate text-sm">{item.file.name}</span>
              <span className="text-muted-foreground text-xs">
                {item.status === "uploading"
                  ? "上传中..."
                  : item.status === "error"
                    ? "失败"
                    : `${(item.file.size / 1024).toFixed(0)}KB`}
              </span>
              {item.status === "done" && <span className="text-green-600 text-xs">✓</span>}
              {onRemove && (
                <button
                  type="button"
                  className="text-muted-foreground hover:text-destructive"
                  onClick={(e) => {
                    e.stopPropagation()
                    onRemove(i)
                  }}
                >
                  ✕
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** 头像上传（圆形裁剪预览 + 图像压缩） */
export function UploadAvatar({
  value,
  onChange,
  disabled,
  imageOptions
}: {
  value?: string
  onChange?: (url: string, file: File) => void
  disabled?: boolean
  imageOptions?: ImageUploadOptions
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [preview, setPreview] = useState(value)
  const { upload, uploading } = useImageUpload({
    maxWidth: 512,
    maxHeight: 512,
    quality: 0.85,
    usePresign: true,
    ...imageOptions
  })

  const handleChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      e.target.value = ""

      // 立即显示本地预览
      setPreview(URL.createObjectURL(file))

      // 压缩 + 上传
      const result = await upload(file)
      setPreview(result.url)
      onChange?.(result.url, file)
    },
    [onChange, upload]
  )

  return (
    <button
      type="button"
      className={`relative h-24 w-24 cursor-pointer overflow-hidden rounded-full border-2 border-dashed ${disabled ? "pointer-events-none opacity-50" : "hover:border-primary"}`}
      onClick={() => inputRef.current?.click()}
      disabled={disabled || uploading}
      aria-label="上传头像"
    >
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        accept="image/*"
        onChange={handleChange}
        disabled={disabled}
      />
      {preview ? (
        // biome-ignore lint/performance/noImgElement: blob URL / 远程 URL 混合
        <img src={preview} alt="avatar" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-muted-foreground">
          <span className="text-xl">📷</span>
        </div>
      )}
      {uploading && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/30">
          <span className="text-white text-xs">上传中...</span>
        </div>
      )}
    </button>
  )
}
