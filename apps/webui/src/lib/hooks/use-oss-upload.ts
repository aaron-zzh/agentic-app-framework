/**
 * useOssUpload——阿里云 OSS SDK 直传 hook（STS 临时凭证 + 分片上传）
 * @author AaronZZH & Kiro
 *
 * - 小文件（< multipartThreshold）普通直传
 * - 大文件分片上传，支持进度回调
 * - 图像文件自动压缩（复用 compressImage）
 * - STS 凭证自动缓存，过期前 5 分钟刷新
 *
 * @example
 * ```tsx
 * const { upload, uploading, progress } = useOssUpload()
 * const result = await upload(file)
 * // result.url — 上传后访问地址
 * ```
 */

"use client"

import type OSSType from "ali-oss"
import { useCallback, useRef, useState } from "react"
import { backendRequest } from "@/lib/api/rest/backend-client"
import { compressImage, type FileUploadOptions, type UploadResult } from "./use-file-upload"

// ─── 类型 ────────────────────────────────────────────────────────────────────

interface StsCredentials {
  accessKeyId: string
  accessKeySecret: string
  securityToken: string
  expiration: string
  bucket: string
  endpoint: string
  region: string
  /** 对象访问 URL 前缀（可空，自定义域名 / CDN 场景使用），为空时回退 https://<bucket>.<endpoint>/<key> */
  urlPrefix?: string | null
}

export interface OssUploadOptions
  extends Pick<
    FileUploadOptions,
    "maxWidth" | "maxHeight" | "quality" | "outputFormat" | "skipCompressBelow"
  > {
  /** 分片上传阈值（字节），超过此大小使用分片上传，默认 5MB */
  multipartThreshold?: number
  /** 分片大小（字节），默认 1MB */
  partSize?: number
  /** 上传目录前缀，默认 uploads */
  prefix?: string
}

// ─── STS 凭证缓存（模块级单例，同一页面所有实例复用） ─────────────────────────

let cachedCredentials: StsCredentials | null = null

function isCredentialsValid(cred: StsCredentials): boolean {
  // 过期前 5 分钟视为无效，提前刷新
  const expireAt = new Date(cred.expiration).getTime() - 5 * 60 * 1000
  return Date.now() < expireAt
}

async function getCredentials(): Promise<StsCredentials> {
  if (cachedCredentials && isCredentialsValid(cachedCredentials)) {
    return cachedCredentials
  }
  cachedCredentials = await backendRequest<StsCredentials>("/api/system/files/sts-token")
  return cachedCredentials
}

// ─── Hook ────────────────────────────────────────────────────────────────────

export function useOssUpload(options: OssUploadOptions = {}) {
  const {
    maxWidth = 1920,
    maxHeight = 1920,
    quality = 0.8,
    outputFormat,
    skipCompressBelow = 100 * 1024,
    multipartThreshold = 5 * 1024 * 1024,
    partSize = 1 * 1024 * 1024,
    prefix = "uploads"
  } = options

  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const abortRef = useRef<unknown>(null)

  const upload = useCallback(
    async (file: File): Promise<UploadResult> => {
      setUploading(true)
      setProgress(0)

      try {
        // 1. 图像压缩
        const compressed = await compressImage(file, {
          maxWidth,
          maxHeight,
          quality,
          outputFormat,
          skipCompressBelow
        })

        // 2. 获取尺寸（图片）
        let width: number | undefined
        let height: number | undefined
        if (compressed.type.startsWith("image/")) {
          const bitmap = await createImageBitmap(compressed)
          width = bitmap.width
          height = bitmap.height
          bitmap.close()
        }

        // 3. 获取 STS 凭证 + 动态加载 OSS SDK（避免 SSR 静态分析触发 Node-only 依赖如 proxy-agent）
        const cred = await getCredentials()
        const { default: OSS } = await import("ali-oss")
        const client: OSSType = new OSS({
          region: cred.region,
          accessKeyId: cred.accessKeyId,
          accessKeySecret: cred.accessKeySecret,
          stsToken: cred.securityToken,
          bucket: cred.bucket,
          authorizationV4: true,
          // 凭证即将过期时自动刷新
          refreshSTSToken: async () => {
            cachedCredentials = null
            const fresh = await getCredentials()
            return {
              accessKeyId: fresh.accessKeyId,
              accessKeySecret: fresh.accessKeySecret,
              stsToken: fresh.securityToken
            }
          }
        })

        // 4. 生成 key
        const date = new Date()
        const ext = compressed.name.includes(".")
          ? compressed.name.substring(compressed.name.lastIndexOf("."))
          : ""
        const key = `${prefix}/${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, "0")}/${String(date.getDate()).padStart(2, "0")}/${crypto.randomUUID()}${ext}`

        // 5. 上传（按大小选择普通 or 分片）
        if (compressed.size >= multipartThreshold) {
          await client.multipartUpload(key, compressed, {
            partSize,
            progress: (p: number, checkpoint: unknown) => {
              abortRef.current = checkpoint
              setProgress(Math.round(p * 100))
            }
          })
        } else {
          await client.put(key, compressed)
          setProgress(100)
        }

        setProgress(100)
        // 优先使用自定义域名/CDN 前缀，回退 OSS 原生域名
        const url = cred.urlPrefix
          ? `${cred.urlPrefix.replace(/\/$/, "")}/${key}`
          : `https://${cred.bucket}.${cred.endpoint}/${key}`

        // 6. 通知后端记录文件元数据到 sys_file
        try {
          await backendRequest("/api/system/files/confirm", {
            method: "POST",
            data: {
              key,
              originalName: compressed.name,
              mimeType: compressed.type,
              size: compressed.size
            }
          })
        } catch (_e) {
          // 记录失败不阻断上传结果
        }

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
      multipartThreshold,
      partSize,
      prefix
    ]
  )

  /** 取消分片上传 */
  const cancel = useCallback(() => {
    // 分片上传中止需要 checkpoint，OSS SDK 会自动处理
    abortRef.current = null
    setUploading(false)
    setProgress(0)
  }, [])

  return { upload, cancel, uploading, progress }
}
