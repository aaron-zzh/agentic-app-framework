/**
 * 文档上传组件——拖拽 + 点击上传，支持进度展示
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { CheckCircle, Loader2, Upload, XCircle } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { knowledgeApi } from "@/lib/api/rest/knowledge/knowledge"
import { cn } from "@/lib/utils/cn"

const ACCEPT = ".pdf,.doc,.docx,.md,.html,.txt"

interface UploadItem {
  id: string
  name: string
  progress: number
  status: "uploading" | "completed" | "failed"
  error?: string
}

interface DocumentUploadProps {
  knowledgeBaseId: string
}

export function DocumentUpload({ knowledgeBaseId }: DocumentUploadProps) {
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const qc = useQueryClient()

  const uploadFile = useCallback(
    async (file: File) => {
      const id = `${Date.now()}-${file.name}`
      setUploads((prev) => [...prev, { id, name: file.name, progress: 0, status: "uploading" }])

      try {
        await knowledgeApi.uploadDocument(knowledgeBaseId, file, (pct) => {
          setUploads((prev) => prev.map((u) => (u.id === id ? { ...u, progress: pct } : u)))
        })
        setUploads((prev) =>
          prev.map((u) => (u.id === id ? { ...u, progress: 100, status: "completed" } : u))
        )
        qc.invalidateQueries({ queryKey: ["knowledge-bases", knowledgeBaseId, "documents"] })
      } catch (err) {
        setUploads((prev) =>
          prev.map((u) =>
            u.id === id ? { ...u, status: "failed", error: (err as Error).message } : u
          )
        )
      }
    },
    [knowledgeBaseId, qc]
  )

  const handleFiles = useCallback(
    (files: FileList | null) => {
      if (!files) return
      Array.from(files).forEach(uploadFile)
    },
    [uploadFile]
  )

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setDragging(false)
      handleFiles(e.dataTransfer.files)
    },
    [handleFiles]
  )

  return (
    <div className="space-y-3">
      {/* 拖拽区域 */}
      {/* biome-ignore lint/a11y/useSemanticElements: 拖拽上传区域需要 div */}
      <div
        className={cn(
          "flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 transition-colors",
          dragging
            ? "border-primary bg-primary/5"
            : "border-muted-foreground/25 hover:border-primary/50"
        )}
        role="button"
        tabIndex={0}
        aria-label="上传文件"
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === "Enter") inputRef.current?.click()
        }}
      >
        <Upload className="mb-2 size-8 text-muted-foreground" />
        <p className="font-medium text-sm">拖拽文件到此处，或点击选择</p>
        <p className="text-muted-foreground text-xs">支持 PDF、Word、Markdown、HTML、TXT</p>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        multiple
        className="hidden"
        onChange={(e) => handleFiles(e.target.files)}
      />

      {/* 上传列表 */}
      {uploads.length > 0 && (
        <div className="space-y-2">
          {uploads.map((item) => (
            <div key={item.id} className="flex items-center gap-3 rounded-md border p-3">
              <StatusIcon status={item.status} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-sm">{item.name}</p>
                {item.status === "uploading" && (
                  <Progress value={item.progress} className="mt-1 h-1.5" />
                )}
                {item.error && <p className="text-destructive text-xs">{item.error}</p>}
              </div>
              <Badge
                variant={
                  item.status === "completed"
                    ? "default"
                    : item.status === "failed"
                      ? "destructive"
                      : "secondary"
                }
              >
                {item.status === "uploading"
                  ? `${item.progress}%`
                  : item.status === "completed"
                    ? "完成"
                    : "失败"}
              </Badge>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function StatusIcon({ status }: { status: UploadItem["status"] }) {
  switch (status) {
    case "uploading":
      return <Loader2 className="size-5 animate-spin text-primary" />
    case "completed":
      return <CheckCircle className="size-5 text-green-500" />
    case "failed":
      return <XCircle className="size-5 text-destructive" />
  }
}
