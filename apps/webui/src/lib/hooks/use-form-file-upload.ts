/**
 * useFormFileUpload——表单场景文件上传 hook
 * @author AaronZZH & Kiro
 *
 * 在 useFileUpload 基础上封装 react-hook-form 绑定：
 * - 自动 setValue / setError / clearErrors
 * - 单文件替换时自动删除旧文件（调后端 DELETE /system/files?key=xxx）
 * - 多文件追加/移除/全部移除
 * - 上传后获取图片尺寸回调（仅图片）
 * - 通过 url→key 映射追踪后端 key，支持后端删除
 *
 * @example
 * ```tsx
 * const upload = useFormFileUpload({ name: "avatar" })
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
import { backendApi } from "@/lib/api/rest/backend-client"
import { type FileUploadOptions, useFileUpload } from "./use-file-upload"

// ─── 类型定义 ───────────────────────────────────────────────────────────────

export interface UseFormFileUploadOptions {
  /** 表单字段名 */
  name: string
  /** 多文件模式 */
  multiple?: boolean
  /** 文件上传配置（图片压缩等） */
  fileOptions?: FileUploadOptions
  /** 上传完成后获取图片尺寸（仅图片文件触发） */
  onImageLoad?: (width: number, height: number) => void
  /** 上传完成回调——单文件场景拿到结果立即触发，可在此即时同步后端/全局 store */
  onUploaded?: (result: { url: string; key?: string; name: string; size: number }) => void
}

// ─── Hook 实现 ──────────────────────────────────────────────────────────────

export function useFormFileUpload({
  name,
  multiple = false,
  fileOptions,
  onImageLoad,
  onUploaded
}: UseFormFileUploadOptions) {
  const { setValue, setError, clearErrors, formState, getValues } = useFormContext()
  const { upload, uploadMultiple, uploading, progress, cancel } = useFileUpload(fileOptions)

  /** url → 后端 key 映射，用于删除时定位文件 */
  const keyMapRef = useRef<Map<string, string>>(new Map())

  /** 调后端删除文件（key 含 / 用 query param 而不是 PathVariable，避免被路由截断） */
  const deleteRemoteFile = useCallback(async (url: string) => {
    const key = keyMapRef.current.get(url)
    if (!key) return
    try {
      await backendApi.delete<void>("/system/files", {
        params: { key },
        showError: false
      })
      keyMapRef.current.delete(url)
    } catch {
      // 删除失败不阻塞流程，孤儿文件由后台清理
    }
  }, [])

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

          // 记录 url → key 映射
          for (const r of results) {
            if (r.key) keyMapRef.current.set(r.url, r.key)
          }

          setValue(name, [...currentArray, ...newUrls], { shouldValidate: true })
        } else {
          // 单文件：先删除旧文件
          const currentValue = getValues(name)
          if (currentValue && typeof currentValue === "string") {
            await deleteRemoteFile(currentValue)
          }

          const result = await upload(acceptedFiles[0])
          if (result.key) keyMapRef.current.set(result.url, result.key)
          setValue(name, result.url, { shouldValidate: true })
          loadImageSize(result.url)
          // 上传成功立即回调（单文件场景），用于即时同步后端/全局 store
          onUploaded?.(result)
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
      loadImageSize,
      onUploaded
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
