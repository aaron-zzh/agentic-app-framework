/**
 * 检测页面是否滚动（用于 header 亚克力效果）
 */

"use client"

import { useEffect, useState } from "react"

export function useScrollOffset(threshold = 0) {
  const [isOffset, setIsOffset] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      setIsOffset(window.scrollY > threshold)
    }

    handleScroll()
    window.addEventListener("scroll", handleScroll)
    return () => window.removeEventListener("scroll", handleScroll)
  }, [threshold])

  return isOffset
}
