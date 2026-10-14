/**
 * 在组件内按类型前缀订阅全局事件，卸载时自动取消。
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useRef } from "react"
import { type RealtimeEventHandler, subscribe } from "./event-registry"

export function useEventSubscription(typePrefix: string, handler: RealtimeEventHandler): void {
  const handlerRef = useRef(handler)
  handlerRef.current = handler

  useEffect(() => subscribe(typePrefix, (event) => handlerRef.current(event)), [typePrefix])
}
