/**
 * Upload——文件上传组件（拖拽 + 点击 + 预览 + 多文件）
 * @author AaronZZH & Kiro
 * 参考 next-ts Upload 设计，使用原生 drag & drop
 */

"use client"

import { useCallback, useRef, useState } from "react"

export interface UploadFile {
  file: File
  preview?: string
  progress?: number
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
  /** 占位文字 */
  placeholder?: string
  /** 错误状态 */
  error?: string
  /** 禁用 */
  disabled?: boolean
}

/** 文件上传组件 */
export function Upload({
  multiple = false,
  accept,
  maxSize = 10,
  value = [],
  onChange,
  onRemove,
  placeholder,
  error,
  disabled
}: UploadProps) {
  const [dragActive, setDragActive] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const handleFiles = useCallback(
    (fileList: FileList) => {
      const newFiles: UploadFile[] = Array.from(fileList)
        .filter((f) => f.size <= maxSize * 1024 * 1024)
        .map((file) => ({
          file,
          preview: file.type.startsWith("image/") ? URL.createObjectURL(file) : undefined
        }))

      if (multiple) {
        onChange?.([...value, ...newFiles])
      } else {
        onChange?.(newFiles.slice(0, 1))
      }
    },
    [multiple, maxSize, value, onChange]
  )

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setDragActive(false)
      if (disabled) return
      handleFiles(e.dataTransfer.files)
    },
    [disabled, handleFiles]
  )

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (e.target.files) handleFiles(e.target.files)
    },
    [handleFiles]
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
          {placeholder ?? (multiple ? "拖拽文件到此处，或点击选择" : "拖拽文件到此处，或点击选择")}
        </p>
        <p className="text-muted-foreground text-xs">最大 {maxSize}MB</p>
      </div>

      {error && <p className="text-destructive text-xs">{error}</p>}

      {/* 文件预览列表 */}
      {value.length > 0 && (
        <ul className="space-y-1">
          {value.map((item, i) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: 文件列表
            <li key={i} className="flex items-center gap-2 rounded border px-3 py-1.5">
              {item.preview ? (
                // biome-ignore lint/performance/noImgElement: 文件预览为 blob URL
                <img src={item.preview} alt="" className="h-8 w-8 rounded object-cover" />
              ) : (
                <span className="flex h-8 w-8 items-center justify-center rounded bg-muted text-xs">
                  📄
                </span>
              )}
              <span className="flex-1 truncate text-sm">{item.file.name}</span>
              <span className="text-muted-foreground text-xs">
                {(item.file.size / 1024).toFixed(0)}KB
              </span>
              {onRemove && (
                <button
                  type="button"
                  className="text-muted-foreground hover:text-destructive"
                  onClick={() => onRemove(i)}
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

/** 头像上传（圆形裁剪预览） */
export function UploadAvatar({
  value,
  onChange,
  disabled
}: {
  value?: string
  onChange?: (file: File) => void
  disabled?: boolean
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [preview, setPreview] = useState(value)

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      setPreview(URL.createObjectURL(file))
      onChange?.(file)
    },
    [onChange]
  )

  return (
    <button
      type="button"
      className={`relative h-24 w-24 cursor-pointer overflow-hidden rounded-full border-2 border-dashed ${disabled ? "pointer-events-none opacity-50" : "hover:border-primary"}`}
      onClick={() => inputRef.current?.click()}
      disabled={disabled}
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
        // biome-ignore lint/performance/noImgElement: 头像预览为 blob URL
        <img src={preview} alt="avatar" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center text-muted-foreground">
          <span className="text-xl">📷</span>
        </div>
      )}
    </button>
  )
}
