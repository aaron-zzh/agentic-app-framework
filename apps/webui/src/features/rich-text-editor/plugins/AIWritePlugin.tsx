/**
 * AIWritePlugin——AI 写作插件
 * @author AaronZZH & Kiro
 *
 * 触发：工具栏 ✨ 或 /ai 命令
 * 生成内容流式插入到光标位置
 */

"use client"

import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import {
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_LOW,
  createCommand,
  type LexicalCommand,
  type LexicalEditor
} from "lexical"
import { useCallback, useEffect, useRef, useState } from "react"
import { streamSSE } from "@/lib/utils/sse"
import { AIWriteDialog } from "./AIWriteDialog"

export const OPEN_AI_WRITE_COMMAND: LexicalCommand<void> = createCommand("OPEN_AI_WRITE")

/** 流式读取并逐 token 插入编辑器 */
async function streamInsert(body: ReadableStream<Uint8Array>, editor: LexicalEditor) {
  await streamSSE(body, {
    onData: (data) => {
      let token: string
      try {
        token = JSON.parse(data).choices?.[0]?.delta?.content ?? ""
      } catch {
        token = data
      }
      if (!token) return
      editor.update(() => {
        const sel = $getSelection()
        if ($isRangeSelection(sel)) sel.insertText(token)
      })
    }
  })
}

export function AIWritePlugin() {
  const [editor] = useLexicalComposerContext()
  const [open, setOpen] = useState(false)
  const [selectedText, setSelectedText] = useState("")
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    const unregister = editor.registerCommand(
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
    return unregister
  }, [editor])

  const handleSubmit = useCallback(
    async (prompt: string) => {
      const content = [prompt, selectedText && `\n参考：${selectedText}`].filter(Boolean).join("")
      abortRef.current = new AbortController()

      editor.focus()
      await new Promise((r) => requestAnimationFrame(r))

      try {
        const res = await fetch("/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ messages: [{ role: "user", content }] }),
          signal: abortRef.current.signal
        })

        if (!res.ok || !res.body) throw new Error("请求失败")
        await streamInsert(res.body, editor)
      } catch {
        // 静默处理（AbortError 为用户取消，其他错误暂不提示）
      }
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
