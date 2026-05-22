/**
 * useChatterConfig——页面级 Chatter 配置（本地优先 + 远程兜底）
 * 页面加载时设置默认配置，若本地无缓存则从后端加载
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect } from "react"
import { loadRemoteConfig, useChatterStore, type ChatterPageConfig } from "@/stores/chatter-store"

/**
 * 在页面组件中调用，设置该页面的 Chatter 默认配置
 * 优先级：本地缓存 > 远程配置 > 传入的 defaultConfig
 */
export function useChatterConfig(
  pageId: string,
  defaultConfig: Partial<ChatterPageConfig>
): void {
  const configs = useChatterStore((s) => s.configs)
  const setConfig = useChatterStore((s) => s.setConfig)

  useEffect(() => {
    const cached = configs[pageId]

    if (cached) {
      // 本地有缓存，直接用，不请求后端
      return
    }

    // 本地无缓存：先用 defaultConfig，再异步从后端加载
    setConfig(pageId, defaultConfig)

    loadRemoteConfig(pageId).then((remote) => {
      if (remote) {
        // 后端有配置，覆盖 defaultConfig
        setConfig(pageId, remote)
      }
      // 后端无配置（null），保持 defaultConfig
    })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageId]) // 只在 pageId 变化时执行，不依赖 configs 避免循环
}
