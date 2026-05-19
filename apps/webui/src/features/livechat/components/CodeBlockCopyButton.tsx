/**
 * CodeBlockCopyButton——代码块复制按钮
 * 复制代码到剪贴板，成功后显示 ✓ 图标 2 秒
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Check, Copy } from "lucide-react"
import { useCallback, useState } from "react"
import { Button } from "@/components/ui/button"

interface CodeBlockCopyButtonProps {
  code: string
}

export function CodeBlockCopyButton({ code }: CodeBlockCopyButtonProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = useCallback(async () => {
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }, [code])

  return (
    <Button
      variant="ghost"
      size="xs"
      className="absolute top-2 right-2 opacity-0 transition-opacity group-hover/code:opacity-100"
      onClick={handleCopy}
    >
      {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
      <span className="sr-only">{copied ? "已复制" : "复制代码"}</span>
    </Button>
  )
}
