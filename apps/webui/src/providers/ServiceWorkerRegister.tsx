/**
 * ServiceWorkerRegister——在客户端注册 Service Worker
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect } from "react"

export function ServiceWorkerRegister() {
  useEffect(() => {
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker
        .register("/sw.js")
        .then((_reg) => {
          // 注册成功，静默
        })
        .catch((err) => {
          // 开发环境打印警告，便于排查 SW 问题
          if (process.env.NODE_ENV === "development") {
            console.warn("[SW] 注册失败:", err)
          }
        })
    }
  }, [])

  return null
}
