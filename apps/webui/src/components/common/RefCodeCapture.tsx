/**
 * RefCodeCapture——根 layout 客户端组件，进入站点时把 URL 里的 ?refCode= 持久化到 sessionStorage。
 *
 * <p>不渲染任何内容；与其他业务无耦合。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect } from "react"
import { captureRefCodeFromUrl } from "@/lib/utils/ref-code"

export function RefCodeCapture() {
  useEffect(() => {
    captureRefCodeFromUrl()
  }, [])
  return null
}
