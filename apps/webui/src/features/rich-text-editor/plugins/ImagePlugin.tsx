/**
 * ImagePlugin——图片粘贴/拖拽上传（使用通用 useImageUpload hook）
 * @author AaronZZH & Kiro
 *
 * 图片自动压缩 + 预签名 OSS 直传
 */

"use client"

import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { $insertNodes, COMMAND_PRIORITY_HIGH, PASTE_COMMAND } from "lexical"
import { useCallback, useEffect } from "react"
import { type ImageUploadOptions, useImageUpload } from "@/lib/hooks/use-image-upload"
import { $createImageNode } from "../nodes/ImageNode"

interface ImagePluginProps {
  /** 图像上传配置（压缩参数、上传端点等） */
  imageOptions?: ImageUploadOptions
  /** 兼容旧接口：直传上传端点 */
  uploadEndpoint?: string
}

export function ImagePlugin({ imageOptions, uploadEndpoint }: ImagePluginProps) {
  const [editor] = useLexicalComposerContext()
  const { upload } = useImageUpload({
    usePresign: !uploadEndpoint,
    uploadEndpoint: uploadEndpoint ?? "/api/upload",
    ...imageOptions
  })

  const handleImageInsert = useCallback(
    async (file: File) => {
      const result = await upload(file)
      editor.update(() => {
        $insertNodes([$createImageNode(result.url, result.name)])
      })
    },
    [upload, editor]
  )

  useEffect(() => {
    return editor.registerCommand(
      PASTE_COMMAND,
      (event) => {
        const clipboardEvent = event as ClipboardEvent
        const items = clipboardEvent.clipboardData?.items
        if (!items) return false

        for (const item of Array.from(items)) {
          if (!item.type.startsWith("image/")) continue
          const file = item.getAsFile()
          if (!file) continue
          clipboardEvent.preventDefault()
          handleImageInsert(file)
          return true
        }
        return false
      },
      COMMAND_PRIORITY_HIGH
    )
  }, [editor, handleImageInsert])

  useEffect(() => {
    const root = editor.getRootElement()
    if (!root) return

    const handleDrop = (e: DragEvent) => {
      const files = e.dataTransfer?.files
      if (!files?.length) return
      const imageFiles = Array.from(files).filter((f) => f.type.startsWith("image/"))
      if (!imageFiles.length) return
      e.preventDefault()
      for (const file of imageFiles) {
        handleImageInsert(file)
      }
    }

    root.addEventListener("drop", handleDrop)
    return () => root.removeEventListener("drop", handleDrop)
  }, [editor, handleImageInsert])

  return null
}
