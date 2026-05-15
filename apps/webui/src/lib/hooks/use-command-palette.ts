/**
 * useCommandPalette——⌘K 快捷键控制命令面板开关
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useState } from "react"

/** 命令面板状态 Hook */
export function useCommandPalette() {
  const [open, setOpen] = useState(false)

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault()
        setOpen((v) => !v)
      }
    }
    document.addEventListener("keydown", handleKeyDown)
    return () => document.removeEventListener("keydown", handleKeyDown)
  }, [])

  const onClose = useCallback(() => setOpen(false), [])

  return { open, onClose }
}
