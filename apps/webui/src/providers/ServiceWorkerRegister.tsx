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
          // console.info("[SW] 注册成功:", reg.scope)
        })
        .catch((_err) => {
          // console.warn("[SW] 注册失败:", err)
        })
    }
  }, [])

  return null
}
