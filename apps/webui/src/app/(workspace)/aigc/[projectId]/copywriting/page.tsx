/**
 * 文案生成工作台——挂载 AigcView 并默认展开文案面板
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect } from "react"
import { AigcView } from "@/features/aigc"
import { useAigcStore } from "@/features/aigc/store"

export default function AigcCopywritingPage() {
  const setCopywritingPanelOpen = useAigcStore((s) => s.setCopywritingPanelOpen)

  // 进入文案工作台时默认展开文案面板
  useEffect(() => {
    setCopywritingPanelOpen(true)
  }, [setCopywritingPanelOpen])

  return <AigcView />
}
