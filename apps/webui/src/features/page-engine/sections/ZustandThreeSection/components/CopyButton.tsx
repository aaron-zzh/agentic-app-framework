"use client"

import { Check, Copy } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { copyToClipboard } from "@/lib/utils/copy-to-clipboard"

interface CopyButtonProps {
  code: string
}

export default function CopyButton({ code }: CopyButtonProps) {
  const [isCopied, setIsCopied] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const handleCopy = useCallback(() => {
    if (timer.current) clearTimeout(timer.current)
    copyToClipboard(code).then(() => {
      setIsCopied(true)
      timer.current = setTimeout(() => setIsCopied(false), 3000)
    })
  }, [code])

  return (
    <Button
      variant="ghost"
      size="icon"
      className="size-8 text-gray-100 hover:bg-gray-600 hover:text-white"
      onClick={handleCopy}
    >
      {isCopied ? <Check className="size-4" /> : <Copy className="size-4" />}
    </Button>
  )
}
