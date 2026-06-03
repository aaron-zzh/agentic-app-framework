"use client"

import { useContext, useEffect } from "react"
import { useShallow } from "zustand/react/shallow"
import useInterval from "@/hooks/useInterval"
import { ClockStoreContext, useClockStore } from "@/lib/store/providers"

function useClock() {
  return useClockStore(
    useShallow((store) => ({
      lastUpdate: store.lastUpdate,
      light: store.light
    }))
  )
}

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString()
}

export function Clock() {
  const { lastUpdate, light } = useClock()
  const tick = useClockStore((store) => store.tick)
  const store = useContext(ClockStoreContext)

  // 通过订阅方式监听状态变化
  useEffect(() => {
    if (!store) return

    const unsubscribe = store.subscribe((_state) => {})
    return unsubscribe
  }, [store])

  useInterval(() => {
    tick(Date.now())
  }, 1000)

  return (
    <div
      className={`inline-block rounded bg-black p-4 text-5xl text-[#82fa58] ${light ? "text-[#333]" : ""}`}
    >
      {formatTime(lastUpdate)}
    </div>
  )
}
