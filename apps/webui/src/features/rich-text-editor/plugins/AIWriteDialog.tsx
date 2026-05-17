/**
 * AIWriteDialog——AI 写作弹窗
 * @author AaronZZH & Kiro
 *
 * 提交后弹窗关闭，生成内容直接流式插入编辑器
 */

"use client"

import { Sparkles } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"

interface AIWriteDialogProps {
  open: boolean
  onClose: () => void
  selectedText?: string
  /** 提交提示词，由外部处理流式生成 */
  onSubmit: (prompt: string) => void
}

export function AIWriteDialog({ open, onClose, selectedText, onSubmit }: AIWriteDialogProps) {
  const [prompt, setPrompt] = useState("")
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 50)
  }, [open])

  const handleSubmit = useCallback(() => {
    if (!prompt.trim()) return
    onSubmit(prompt.trim())
    onClose()
    setPrompt("")
  }, [prompt, onSubmit, onClose])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault()
        handleSubmit()
      }
      if (e.key === "Escape") {
        onClose()
        setPrompt("")
      }
    },
    [handleSubmit, onClose]
  )

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) {
          onClose()
          setPrompt("")
        }
      }}
    >
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-primary" />
            AI 写作
            {selectedText && (
              <span className="ml-auto rounded-full bg-accent px-2 py-0.5 font-normal text-muted-foreground text-xs">
                已选 {selectedText.length} 字作为上下文
              </span>
            )}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-3">
          <Textarea
            ref={inputRef}
            placeholder="输入提示词，Enter 开始生成，Shift+Enter 换行"
            rows={3}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <div className="flex justify-end">
            <Button size="sm" onClick={handleSubmit} disabled={!prompt.trim()}>
              <Sparkles className="mr-1 h-3 w-3" />
              生成
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
