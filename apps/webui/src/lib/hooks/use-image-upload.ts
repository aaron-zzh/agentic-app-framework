/**
 * useImageUpload——通用图像上传 hook
 * @author AaronZZH & Kiro
 *
 * 支持：图像压缩 + 预签名 OSS 直传 + 进度追踪 + 多场景复用
 * 消费方：Upload 组件、富文本编辑器 ImagePlugin、头像上传等
 *
 * @example
 * ```tsx
 * const { upload, uploading, progress } = useImageUpload({ maxWidth: 1920, quality: 0.8 })
 * const result = await upload(file)
 * // result.url — 上传后的访问地址
 * ```
 */

"use client"

import { useCallback, useRef, useState } from "react"

// ─── 类型定义 ───────────────────────────────────────────────────────────────

export interface ImageUploadOptions {
  /** 最大宽度（px），超过则等比缩放，默认 1920 */
  maxWidth?: number
  /** 最大高度（px），超过则等比缩放，默认 1920 */
  maxHeight?: number
  /** 压缩质量 0-1，默认 0.8 */
  quality?: number
  /** 输出格式，默认 image/webp（不支持时回退 image/jpeg） */
  outputFormat?: "image/webp" | "image/jpeg" | "image/png"
  /** 跳过压缩的文件大小阈值（字节），小于此值不压缩，默认 100KB */
  skipCompressBelow?: number
  /** 预签名 URL 获取接口，默认 /api/upload/presign */
  presignEndpoint?: string
  /** 直传上传接口（非 OSS 模式），默认 /api/upload */
  uploadEndpoint?: string
  /** 使用预签名 OSS 直传模式，默认 true */
  usePresign?: boolean
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
}

interface PresignResponse {
  /** 预签名上传 URL */
  uploadUrl: string
  /** 上传后的访问 URL */
  accessUrl: string
  /** 需要附带的表单字段（OSS Policy 等） */
  fields?: Record<string, string>
}

// ─── 图像压缩 ───────────────────────────────────────────────────────────────

function supportsWebp(): boolean {
  if (typeof document === "undefined") return false
  const canvas = document.createElement("canvas")
  return canvas.toDataURL("image/webp").startsWith("data:image/webp")
}

/**
 * 客户端图像压缩——Canvas 缩放 + 质量压缩
 */
export async function compressImage(
  file: File,
  options: Pick<
    ImageUploadOptions,
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

export function useImageUpload(options: ImageUploadOptions = {}) {
  const {
    maxWidth = 1920,
    maxHeight = 1920,
    quality = 0.8,
    outputFormat,
    skipCompressBelow = 100 * 1024,
    presignEndpoint = "/api/upload/presign",
    uploadEndpoint = "/api/upload",
    usePresign = true
  } = options

  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const abortRef = useRef<AbortController | null>(null)

  /** 获取预签名 URL */
  const getPresignUrl = useCallback(
    async (filename: string, contentType: string): Promise<PresignResponse> => {
      const res = await fetch(presignEndpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ filename, contentType })
      })
      if (!res.ok) throw new Error(`获取预签名 URL 失败: ${res.statusText}`)
      const json = await res.json()
      return json.data ?? json
    },
    [presignEndpoint]
  )

  /** 通过预签名 URL 直传 OSS */
  const uploadToOss = useCallback(
    async (file: File, presign: PresignResponse, signal: AbortSignal): Promise<string> => {
      return new Promise<string>((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open("PUT", presign.uploadUrl)
        xhr.setRequestHeader("Content-Type", file.type)

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) setProgress(Math.round((e.loaded / e.total) * 100))
        }
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(presign.accessUrl)
          } else {
            reject(new Error(`OSS 上传失败: ${xhr.status}`))
          }
        }
        xhr.onerror = () => reject(new Error("网络错误"))

        signal.addEventListener("abort", () => xhr.abort())
        xhr.send(file)
      })
    },
    []
  )

  /** 通过后端接口上传（非 OSS 模式） */
  const uploadViaServer = useCallback(
    async (file: File, signal: AbortSignal): Promise<string> => {
      return new Promise<string>((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        const form = new FormData()
        form.append("file", file)

        xhr.open("POST", uploadEndpoint)
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) setProgress(Math.round((e.loaded / e.total) * 100))
        }
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            const json = JSON.parse(xhr.responseText)
            resolve(json.data?.url ?? json.url)
          } else {
            reject(new Error(`上传失败: ${xhr.status}`))
          }
        }
        xhr.onerror = () => reject(new Error("网络错误"))

        signal.addEventListener("abort", () => xhr.abort())
        xhr.send(form)
      })
    },
    [uploadEndpoint]
  )

  /** 上传单张图片（压缩 + 上传） */
  const upload = useCallback(
    async (file: File): Promise<UploadResult> => {
      setUploading(true)
      setProgress(0)
      abortRef.current = new AbortController()
      const { signal } = abortRef.current

      try {
        // 1. 压缩
        const compressed = await compressImage(file, {
          maxWidth,
          maxHeight,
          quality,
          outputFormat,
          skipCompressBelow
        })

        // 2. 获取图片尺寸
        let width: number | undefined
        let height: number | undefined
        if (compressed.type.startsWith("image/")) {
          const bitmap = await createImageBitmap(compressed)
          width = bitmap.width
          height = bitmap.height
          bitmap.close()
        }

        // 3. 上传
        let url: string
        if (usePresign) {
          const presign = await getPresignUrl(compressed.name, compressed.type)
          url = await uploadToOss(compressed, presign, signal)
        } else {
          url = await uploadViaServer(compressed, signal)
        }

        setProgress(100)
        return { url, name: compressed.name, size: compressed.size, width, height }
      } finally {
        setUploading(false)
        abortRef.current = null
      }
    },
    [
      maxWidth,
      maxHeight,
      quality,
      outputFormat,
      skipCompressBelow,
      usePresign,
      getPresignUrl,
      uploadToOss,
      uploadViaServer
    ]
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

  return { upload, uploadMultiple, cancel, uploading, progress }
}
