/**
 * useFormImageUpload——表单场景图像上传 hook
 * @author AaronZZH & Kiro
 *
 * 在 useImageUpload 基础上封装 react-hook-form 绑定：
 * - 自动 setValue / setError / clearErrors
 * - 单文件替换时自动删除旧文件（OSS 垃圾回收）
 * - 多文件追加/移除/全部移除
 * - 上传后获取图片尺寸回调
 * - fileId 追踪（支持后端删除）
 *
 * @example
 * ```tsx
 * const upload = useFormImageUpload({ name: "avatar" })
 * <Upload
 *   value={field.value}
 *   onDrop={upload.onDrop}
 *   onDelete={upload.onDelete}
 *   disabled={upload.disabled}
 * />
 * ```
 */

"use client"

import { useCallback, useRef } from "react"
import { useFormContext } from "react-hook-form"
import { type ImageUploadOptions, useImageUpload } from "./use-image-upload"

// ─── 类型定义 ───────────────────────────────────────────────────────────────

export interface UseFormImageUploadOptions {
  /** 表单字段名 */
  name: string
  /** 多文件模式 */
  multiple?: boolean
  /** 图像上传配置（压缩、OSS 等） */
  imageOptions?: ImageUploadOptions
  /** 上传完成后获取图片尺寸 */
  onImageLoad?: (width: number, height: number) => void
  /** 文件删除接口，传入 fileId */
  deleteEndpoint?: string
}

// ─── Hook 实现 ──────────────────────────────────────────────────────────────

export function useFormImageUpload({
  name,
  multiple = false,
  imageOptions,
  onImageLoad,
  deleteEndpoint = "/api/upload/file"
}: UseFormImageUploadOptions) {
  const { setValue, setError, clearErrors, formState, getValues } = useFormContext()
  const { upload, uploadMultiple, uploading, progress, cancel } = useImageUpload({
    usePresign: true,
    ...imageOptions
  })

  /** url → fileId 映射，用于删除时调后端清理 */
  const fileIdMapRef = useRef<Map<string, string>>(new Map())

  /** 调后端删除文件 */
  const deleteRemoteFile = useCallback(
    async (url: string) => {
      const fileId = fileIdMapRef.current.get(url)
      if (!fileId) return
      try {
        await fetch(`${deleteEndpoint}/${fileId}`, { method: "DELETE" })
        fileIdMapRef.current.delete(url)
      } catch {
        // 删除失败不阻塞流程
      }
    },
    [deleteEndpoint]
  )

  /** 获取图片尺寸 */
  const loadImageSize = useCallback(
    (url: string) => {
      if (!onImageLoad) return
      const img = new Image()
      img.onload = () => onImageLoad(img.naturalWidth, img.naturalHeight)
      img.src = url
    },
    [onImageLoad]
  )

  /** 文件拖入/选择回调 */
  const onDrop = useCallback(
    async (acceptedFiles: File[]) => {
      if (acceptedFiles.length === 0) return

      try {
        if (multiple) {
          const results = await uploadMultiple(acceptedFiles)
          const currentValue = getValues(name)
          const currentArray = Array.isArray(currentValue) ? currentValue : []
          const newUrls = results.map((r) => r.url)

          // 追踪 fileId（预签名模式下 url 即标识）
          for (const r of results) {
            fileIdMapRef.current.set(r.url, r.url)
          }

          setValue(name, [...currentArray, ...newUrls], { shouldValidate: true })
        } else {
          // 单文件：先删除旧文件
          const currentValue = getValues(name)
          if (currentValue && typeof currentValue === "string") {
            await deleteRemoteFile(currentValue)
          }

          const result = await upload(acceptedFiles[0])
          fileIdMapRef.current.set(result.url, result.url)
          setValue(name, result.url, { shouldValidate: true })
          loadImageSize(result.url)
        }
        clearErrors(name)
      } catch (err) {
        setError(name, {
          type: "upload",
          message: err instanceof Error ? err.message : "上传失败"
        })
      }
    },
    [
      name,
      multiple,
      upload,
      uploadMultiple,
      getValues,
      setValue,
      setError,
      clearErrors,
      deleteRemoteFile,
      loadImageSize
    ]
  )

  /** 删除当前文件（单文件模式） */
  const onDelete = useCallback(async () => {
    const currentValue = getValues(name)
    if (currentValue && typeof currentValue === "string") {
      await deleteRemoteFile(currentValue)
    }
    setValue(name, multiple ? [] : "", { shouldValidate: true })
  }, [name, multiple, getValues, setValue, deleteRemoteFile])

  /** 移除指定文件（多文件模式） */
  const onRemove = useCallback(
    async (fileUrl: string) => {
      await deleteRemoteFile(fileUrl)
      const currentValue = getValues(name)
      if (Array.isArray(currentValue)) {
        setValue(
          name,
          currentValue.filter((url: string) => url !== fileUrl),
          { shouldValidate: true }
        )
      }
    },
    [name, getValues, setValue, deleteRemoteFile]
  )

  /** 移除全部文件（多文件模式） */
  const onRemoveAll = useCallback(async () => {
    const currentValue = getValues(name)
    if (Array.isArray(currentValue)) {
      await Promise.all(currentValue.map((url: string) => deleteRemoteFile(url)))
    }
    setValue(name, [], { shouldValidate: true })
  }, [name, getValues, setValue, deleteRemoteFile])

  return {
    uploading,
    progress,
    disabled: formState.isSubmitting || uploading,
    onDrop,
    onDelete,
    onRemove,
    onRemoveAll,
    cancel
  }
}
