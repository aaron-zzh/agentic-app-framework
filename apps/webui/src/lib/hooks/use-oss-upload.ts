/**
 * useOssUpload——预签名 PUT 直传 hook。
 *
 * <p>服务端签发受文件类型、用户命名空间和存储用途约束的单对象票据，浏览器不会取得可复用的 OSS STS 凭证。
 */

"use client"

import { useCallback, useRef, useState } from "react"
import { backendRequest } from "@/lib/api/rest/backend-client"
import { compressImage, type FileUploadOptions, type UploadResult } from "./use-file-upload"

interface PresignedUploadTicket {
  key: string
  url: string
  contentType: string
  maxSizeBytes: number
  storageConfigId: number | null
}

interface StoredFile {
  fileId: number
  key: string
  url: string
}

export interface OssUploadOptions
  extends Pick<
    FileUploadOptions,
    | "storagePurpose"
    | "maxWidth"
    | "maxHeight"
    | "quality"
    | "outputFormat"
    | "skipCompressBelow"
  > {}

export function useOssUpload(options: OssUploadOptions = {}) {
  const {
    storagePurpose = "MASTER",
    maxWidth = 1920,
    maxHeight = 1920,
    quality = 0.8,
    outputFormat,
    skipCompressBelow = 100 * 1024
  } = options

  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const abortRef = useRef<AbortController | null>(null)

  const upload = useCallback(
    async (file: File): Promise<UploadResult> => {
      setUploading(true)
      setProgress(0)
      abortRef.current = new AbortController()

      try {
        const compressed = await compressImage(file, {
          maxWidth,
          maxHeight,
          quality,
          outputFormat,
          skipCompressBelow
        })

        let width: number | undefined
        let height: number | undefined
        if (compressed.type.startsWith("image/")) {
          const bitmap = await createImageBitmap(compressed)
          width = bitmap.width
          height = bitmap.height
          bitmap.close()
        }

        const query = new URLSearchParams({
          filename: compressed.name,
          contentType: compressed.type,
          storagePurpose
        })
        const ticket = await backendRequest<PresignedUploadTicket>(
          `/api/system/files/presigned-url?${query.toString()}`
        )
        if (compressed.size > ticket.maxSizeBytes) {
          throw new Error("文件超过允许的上传大小")
        }

        const uploadResponse = await fetch(ticket.url, {
          method: "PUT",
          headers: { "Content-Type": ticket.contentType },
          body: compressed,
          signal: abortRef.current.signal
        })
        if (!uploadResponse.ok) {
          throw new Error(`对象存储上传失败 (${uploadResponse.status})`)
        }
        setProgress(90)

        const storedFile = await backendRequest<StoredFile>("/api/system/files/confirm", {
          method: "POST",
          data: {
            key: ticket.key,
            originalName: compressed.name,
            mimeType: ticket.contentType,
            size: compressed.size
          }
        })
        setProgress(100)

        return {
          fileId: storedFile.fileId,
          url: storedFile.url,
          key: storedFile.key,
          name: compressed.name,
          size: compressed.size,
          width,
          height
        }
      } finally {
        setUploading(false)
        abortRef.current = null
      }
    },
    [
      storagePurpose,
      maxWidth,
      maxHeight,
      quality,
      outputFormat,
      skipCompressBelow
    ]
  )

  const cancel = useCallback(() => {
    abortRef.current?.abort()
    setUploading(false)
    setProgress(0)
  }, [])

  return { upload, cancel, uploading, progress }
}
