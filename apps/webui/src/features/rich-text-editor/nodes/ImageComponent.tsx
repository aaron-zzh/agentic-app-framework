/**
 * ImageComponent——图片渲染组件
 * @author AaronZZH & Kiro
 */

"use client"

import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { useLexicalNodeSelection } from "@lexical/react/useLexicalNodeSelection"
import { mergeRegister } from "@lexical/utils"
import {
  $getNodeByKey,
  $getSelection,
  $isNodeSelection,
  CLICK_COMMAND,
  COMMAND_PRIORITY_LOW,
  KEY_BACKSPACE_COMMAND,
  KEY_DELETE_COMMAND,
  type NodeKey
} from "lexical"
import { useCallback, useEffect, useRef } from "react"
import { cn } from "@/lib/utils/cn"
import { $isImageNode } from "./ImageNode"

interface ImageComponentProps {
  src: string
  alt: string
  width?: number
  height?: number
  nodeKey: NodeKey
}

export default function ImageComponent({ src, alt, width, height, nodeKey }: ImageComponentProps) {
  const [editor] = useLexicalComposerContext()
  const [isSelected, setSelected, clearSelection] = useLexicalNodeSelection(nodeKey)
  const imgRef = useRef<HTMLImageElement>(null)

  const onDelete = useCallback(
    (e: KeyboardEvent) => {
      if (isSelected && $isNodeSelection($getSelection())) {
        e.preventDefault()
        editor.update(() => {
          const node = $getNodeByKey(nodeKey)
          if ($isImageNode(node)) node.remove()
        })
        return true
      }
      return false
    },
    [editor, isSelected, nodeKey]
  )

  useEffect(() => {
    return mergeRegister(
      editor.registerCommand(
        CLICK_COMMAND,
        (e: MouseEvent) => {
          if (imgRef.current?.contains(e.target as Node)) {
            if (!e.shiftKey) clearSelection()
            setSelected(!isSelected)
            return true
          }
          return false
        },
        COMMAND_PRIORITY_LOW
      ),
      editor.registerCommand(KEY_DELETE_COMMAND, onDelete, COMMAND_PRIORITY_LOW),
      editor.registerCommand(KEY_BACKSPACE_COMMAND, onDelete, COMMAND_PRIORITY_LOW)
    )
  }, [editor, isSelected, setSelected, clearSelection, onDelete])

  return (
    <span
      className={cn(
        "inline-block cursor-default rounded",
        isSelected && "outline outline-2 outline-primary"
      )}
    >
      {/* biome-ignore lint/performance/noImgElement: Lexical ImageNode 需要原生 img */}
      <img
        ref={imgRef}
        src={src}
        alt={alt}
        width={width}
        height={height}
        className="max-w-full rounded"
        style={{ maxHeight: 400 }}
        draggable={false}
      />
    </span>
  )
}
