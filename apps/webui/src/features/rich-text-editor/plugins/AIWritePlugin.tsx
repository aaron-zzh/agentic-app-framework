/**
 * AIWritePlugin——AI 写作插件
 * @author AaronZZH & Kiro
 *
 * 触发：工具栏 ✨ 或 /ai 命令
 * 调用通用 Assistant 执行接口，生成内容流式插入到光标位置
 */

"use client"

import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import {
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_LOW,
  createCommand,
  type LexicalCommand
} from "lexical"
import { useCallback, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { executeAssistant } from "@/lib/api/headless-assistant"
import { AIWriteDialog } from "./AIWriteDialog"

export const OPEN_AI_WRITE_COMMAND: LexicalCommand<void> = createCommand("OPEN_AI_WRITE")

export function AIWritePlugin() {
  const [editor] = useLexicalComposerContext()
  const [open, setOpen] = useState(false)
  const [selectedText, setSelectedText] = useState("")
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    return editor.registerCommand(
      OPEN_AI_WRITE_COMMAND,
      () => {
        editor.getEditorState().read(() => {
          const sel = $getSelection()
          setSelectedText($isRangeSelection(sel) ? sel.getTextContent() : "")
        })
        setOpen(true)
        return true
      },
      COMMAND_PRIORITY_LOW
    )
  }, [editor])

  const handleSubmit = useCallback(
    async (prompt: string) => {
      abortRef.current = new AbortController()

      editor.focus()
      await new Promise((r) => requestAnimationFrame(r))

      await executeAssistant(
        {
          input: {
            text: selectedText ? `${prompt}\n\n参考上下文：\n${selectedText}` : prompt,
            variables: {},
            attachments: []
          },
          skill: { code: "rich-text-write" },
          knowledge: {
            knowledgeBaseIds: [],
            includePublic: false,
            topK: 5,
            similarityThreshold: 0.2
          },
          model: { mode: "AUTO", modelId: null },
          memory: { mode: "DISABLED" },
          output: {}
        },
        {
          signal: abortRef.current.signal,
          onChunk: (token) => {
            editor.update(() => {
              const sel = $getSelection()
              if ($isRangeSelection(sel)) sel.insertText(token)
            })
          },
          onError: (err) => {
            if (err.name !== "AbortError") toast.error(err.message ?? "生成失败")
          }
        }
      )
    },
    [editor, selectedText]
  )

  return (
    <AIWriteDialog
      open={open}
      onClose={() => setOpen(false)}
      selectedText={selectedText}
      onSubmit={handleSubmit}
    />
  )
}
