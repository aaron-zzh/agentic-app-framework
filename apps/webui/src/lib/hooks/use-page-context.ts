/**
 * usePageContext——页面进入时注册上下文到后端（AI 感知预留）
 * 同时设置当前页面 ID 到 chatter store
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect } from "react"
import { useChatterStore } from "@/stores/chatter-store"

interface PageContextOptions {
  pageId: string
  pageTitle?: string
  /** 当前页面可拖放的组件列表（供 AI 感知，v0.2.0 生效） */
  availableComponents?: string[]
}

/**
 * 页面加载时调用，注册页面上下文
 * - 设置 chatter store 的 currentPageId
 * - 异步上报到后端（fire-and-forget，v0.2.0 后端实现前为空操作）
 */
export function usePageContext({ pageId, pageTitle, availableComponents }: PageContextOptions): void {
  const setCurrentPage = useChatterStore((s) => s.setCurrentPage)

  useEffect(() => {
    setCurrentPage(pageId)

    // 异步上报到后端（fire-and-forget）
    fetch("/api/context/page-enter", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pageId, pageTitle, availableComponents }),
    }).catch(() => {
      // 静默失败，不影响页面功能
    })
  }, [pageId, pageTitle, availableComponents, setCurrentPage])
}
