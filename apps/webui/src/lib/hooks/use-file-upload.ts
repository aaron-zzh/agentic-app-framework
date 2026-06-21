/**
 * useFileUpload——通用文件上传 hook
 * @author AaronZZH & Kiro
 *
 * 统一上传链路：
 * - 默认 → 后端 POST /api/system/files/upload（存储类型由后端 aaf.storage.type 决定，local/s3/oss 任一）
 * - NEXT_PUBLIC_UPLOAD_MODE=oss → 切换到 useOssUpload（STS 临时凭证 + ali-oss SDK 分片直传，绕过后端转发）
 *
 * 文件类型支持：
 * - 图片：自动 Canvas 压缩（webp/jpeg）+ 获取尺寸
 * - 非图片：原文件直传，不压缩
 *
 * @example
 * ```tsx
 * const { upload, uploading, progress } = useFileUpload({ maxWidth: 1920, quality: 0.8 })
 * const result = await upload(file)
 * // result.url — 后端返回的可访问 URL（含域名前缀）
 * ```
 */

"use client"

import type { AxiosProgressEvent } from "axios"
import { useCallback, useRef, useState } from "react"
import { backendApi } from "@/lib/api/rest/backend-client"
import { useOssUpload } from "./use-oss-upload"

// ─── 类型定义 ───────────────────────────────────────────────────────────────

export interface FileUploadOptions {
  /** 图片最大宽度（px），超过则等比缩放，默认 1920 */
  maxWidth?: number
  /** 图片最大高度（px），超过则等比缩放，默认 1920 */
  maxHeight?: number
  /** 图片压缩质量 0-1，默认 0.8 */
  quality?: number
  /** 图片输出格式，默认 image/webp（不支持时回退 image/jpeg） */
  outputFormat?: "image/webp" | "image/jpeg" | "image/png"
  /** 跳过图片压缩的文件大小阈值（字节），小于此值不压缩，默认 100KB */
  skipCompressBelow?: number
}

export interface UploadResult {
  /** 上传后的访问 URL */
  url: string
  /** 文件名 */
  name: string
  /** 文件大小（压缩后） */
  size: number
  /** 图片宽度（仅图片文件） */
  width?: number
  /** 图片高度（仅图片文件） */
  height?: number
  /** 后端存储 key（用于后续删除） */
  key?: string
}

/** 后端 FileController.upload 返回结构（com.xuejiai.aaf.framework.storage.FileVO） */
interface FileVO {
  key: string
  url: string
  filename: string
  size: number
  contentType: string
}

// ─── 图像压缩 ───────────────────────────────────────────────────────────────

function supportsWebp(): boolean {
  if (typeof document === "undefined") return false
  const canvas = document.createElement("canvas")
  return canvas.toDataURL("image/webp").startsWith("data:image/webp")
}

/**
 * 客户端图像压缩——Canvas 缩放 + 质量压缩
 *
 * <p>非图片或小文件直接返回原文件，不做压缩。
 */
export async function compressImage(
  file: File,
  options: Pick<
    FileUploadOptions,
    "maxWidth" | "maxHeight" | "quality" | "outputFormat" | "skipCompressBelow"
  >
): Promise<File> {
  const {
    maxWidth = 1920,
    maxHeight = 1920,
    quality = 0.8,
    outputFormat,
    skipCompressBelow = 100 * 1024
  } = options

  // 非图片或小文件跳过压缩
  if (!file.type.startsWith("image/") || file.size < skipCompressBelow) {
    return file
  }

  // GIF/SVG 不压缩
  if (file.type === "image/gif" || file.type === "image/svg+xml") {
    return file
  }

  const bitmap = await createImageBitmap(file)
  let { width, height } = bitmap

  // 等比缩放
  if (width > maxWidth || height > maxHeight) {
    const ratio = Math.min(maxWidth / width, maxHeight / height)
    width = Math.round(width * ratio)
    height = Math.round(height * ratio)
  }

  const canvas = new OffscreenCanvas(width, height)
  const ctx = canvas.getContext("2d")
  if (!ctx) return file
  ctx.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  const format = outputFormat ?? (supportsWebp() ? "image/webp" : "image/jpeg")
  const blob = await canvas.convertToBlob({ type: format, quality })

  // 压缩后比原文件大则返回原文件
  if (blob.size >= file.size) return file

  const ext = format === "image/webp" ? ".webp" : format === "image/png" ? ".png" : ".jpg"
  const name = file.name.replace(/\.[^.]+$/, ext)
  return new File([blob], name, { type: format })
}

// ─── Hook 实现 ──────────────────────────────────────────────────────────────

export function useFileUpload(options: FileUploadOptions = {}) {
  const {
    maxWidth = 1920,
    maxHeight = 1920,
    quality = 0.8,
    outputFormat,
    skipCompressBelow = 100 * 1024
  } = options

  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const abortRef = useRef<AbortController | null>(null)

  /** 后端直传：POST /system/files/upload (multipart/form-data) */
  const uploadToBackend = useCallback(
    async (file: File, signal: AbortSignal): Promise<{ url: string; key: string }> => {
      const form = new FormData()
      form.append("file", file)

      const vo = await backendApi.post<FileVO>("/system/files/upload", form, {
        // 让 axios 自动设置 multipart boundary
        headers: { "Content-Type": undefined as unknown as string },
        signal,
        onUploadProgress: (e: AxiosProgressEvent) => {
          if (e.total) setProgress(Math.round((e.loaded / e.total) * 100))
        }
      })
      return { url: vo.url, key: vo.key }
    },
    []
  )

  /** 上传单个文件（图片自动压缩） */
  const upload = useCallback(
    async (file: File): Promise<UploadResult> => {
      setUploading(true)
      setProgress(0)
      abortRef.current = new AbortController()
      const { signal } = abortRef.current

      try {
        // 1. 图片压缩（非图片自动跳过）
        const compressed = await compressImage(file, {
          maxWidth,
          maxHeight,
          quality,
          outputFormat,
          skipCompressBelow
        })

        // 2. 获取图片尺寸（仅图片文件）
        let width: number | undefined
        let height: number | undefined
        if (compressed.type.startsWith("image/")) {
          const bitmap = await createImageBitmap(compressed)
          width = bitmap.width
          height = bitmap.height
          bitmap.close()
        }

        // 3. 上传
        const { url, key } = await uploadToBackend(compressed, signal)

        setProgress(100)
        return { url, key, name: compressed.name, size: compressed.size, width, height }
      } finally {
        setUploading(false)
        abortRef.current = null
      }
    },
    [maxWidth, maxHeight, quality, outputFormat, skipCompressBelow, uploadToBackend]
  )

  /** 批量上传 */
  const uploadMultiple = useCallback(
    async (files: File[]): Promise<UploadResult[]> => {
      const results: UploadResult[] = []
      for (const file of files) {
        results.push(await upload(file))
      }
      return results
    },
    [upload]
  )

  /** 取消上传 */
  const cancel = useCallback(() => {
    abortRef.current?.abort()
    setUploading(false)
    setProgress(0)
  }, [])

  // ─── env 切链路：NEXT_PUBLIC_UPLOAD_MODE=oss 时委托给 useOssUpload（STS + 分片直传） ───
  // hook 顺序固定：始终调用 useOssUpload，不依赖 env 开关条件
  const ossHook = useOssUpload({
    maxWidth,
    maxHeight,
    quality,
    outputFormat,
    skipCompressBelow
  })
  const useOssMode = process.env.NEXT_PUBLIC_UPLOAD_MODE === "oss"

  if (useOssMode) {
    const ossUploadMultiple = async (files: File[]): Promise<UploadResult[]> => {
      const results: UploadResult[] = []
      for (const f of files) results.push(await ossHook.upload(f))
      return results
    }
    return {
      upload: ossHook.upload,
      uploadMultiple: ossUploadMultiple,
      cancel: ossHook.cancel,
      uploading: ossHook.uploading,
      progress: ossHook.progress
    }
  }

  return { upload, uploadMultiple, cancel, uploading, progress }
}
