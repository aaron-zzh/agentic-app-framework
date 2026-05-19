/**
 * FileUploadArea——文件上传区域
 * 支持拖拽上传、粘贴上传、进度条、文件类型/大小校验
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Upload, X } from "lucide-react"
import { type DragEvent, type ClipboardEvent, useCallback, useRef, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"

interface UploadingFile {
  id: string
  name: string
  progress: number
}

interface FileUploadAreaProps {
  /** 允许的文件类型（MIME），如 ["image/*", "application/pdf"] */
  accept?: string[]
  /** 最大文件大小（字节），默认 10MB */
  maxSize?: number
  /** 最大文件数量 */
  maxCount?: number
  /** 上传回调，返回上传后的 URL */
  onUpload: (file: File) => Promise<string>
  /** 上传完成回调 */
  onComplete?: (result: { name: string; url: string; size: number; type: string }) => void
}

const DEFAULT_MAX_SIZE = 10 * 1024 * 1024 // 10MB

export function FileUploadArea({
  accept,
  maxSize = DEFAULT_MAX_SIZE,
  maxCount = 5,
  onUpload,
  onComplete
}: FileUploadAreaProps) {
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState<UploadingFile[]>([])
  const inputRef = useRef<HTMLInputElement>(null)

  /** 校验文件 */
  const validate = useCallback(
    (file: File): boolean => {
      if (file.size > maxSize) {
        toast.error(`文件 ${file.name} 超过大小限制（${(maxSize / 1024 / 1024).toFixed(0)}MB）`)
        return false
      }
      if (accept && accept.length > 0) {
        const matched = accept.some((pattern) => {
          if (pattern.endsWith("/*")) {
            return file.type.startsWith(pattern.replace("/*", "/"))
          }
          return file.type === pattern
        })
        if (!matched) {
          toast.error(`文件 ${file.name} 类型不支持`)
          return false
        }
      }
      return true
    },
    [accept, maxSize]
  )

  /** 处理文件上传 */
  const processFiles = useCallback(
    async (files: File[]) => {
      const validFiles = files.filter(validate).slice(0, maxCount)
      for (const file of validFiles) {
        const id = `upload-${Date.now()}-${file.name}`
        setUploading((prev) => [...prev, { id, name: file.name, progress: 0 }])

        try {
          // 模拟进度（实际应由 onUpload 内部回调）
          setUploading((prev) =>
            prev.map((u) => (u.id === id ? { ...u, progress: 50 } : u))
          )
          const url = await onUpload(file)
          setUploading((prev) =>
            prev.map((u) => (u.id === id ? { ...u, progress: 100 } : u))
          )
          onComplete?.({ name: file.name, url, size: file.size, type: file.type })
          // 完成后移除进度条
          setTimeout(() => {
            setUploading((prev) => prev.filter((u) => u.id !== id))
          }, 500)
        } catch {
          toast.error(`上传 ${file.name} 失败`)
          setUploading((prev) => prev.filter((u) => u.id !== id))
        }
      }
    },
    [validate, maxCount, onUpload, onComplete]
  )

  const handleDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault()
      setDragging(false)
      const files = Array.from(e.dataTransfer.files)
      processFiles(files)
    },
    [processFiles]
  )

  const handlePaste = useCallback(
    (e: ClipboardEvent) => {
      const files = Array.from(e.clipboardData.files)
      if (files.length > 0) {
        e.preventDefault()
        processFiles(files)
      }
    },
    [processFiles]
  )

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files ?? [])
      processFiles(files)
      // 重置 input 以允许重复选择同一文件
      e.target.value = ""
    },
    [processFiles]
  )

  const cancelUpload = useCallback((id: string) => {
    setUploading((prev) => prev.filter((u) => u.id !== id))
  }, [])

  return (
    <div onPaste={handlePaste}>
      {/* 拖拽区域 */}
      <div
        role="button"
        tabIndex={0}
        className={`flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed p-4 transition-colors ${
          dragging ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"
        }`}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => e.key === "Enter" && inputRef.current?.click()}
      >
        <Upload className="size-5 text-muted-foreground" />
        <p className="text-muted-foreground text-sm">拖拽文件到此处，或点击选择</p>
        <input
          ref={inputRef}
          type="file"
          multiple
          className="hidden"
          accept={accept?.join(",")}
          onChange={handleInputChange}
        />
      </div>

      {/* 上传进度 */}
      {uploading.length > 0 && (
        <div className="mt-2 space-y-1">
          {uploading.map((file) => (
            <div key={file.id} className="flex items-center gap-2">
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs">{file.name}</p>
                <Progress value={file.progress} className="h-1" />
              </div>
              <Button variant="ghost" size="xs" onClick={() => cancelUpload(file.id)}>
                <X className="size-3" />
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
