/**
 * 虚拟进度 hook：在指定时长内从 0 随机增长到 99%，营造前快后慢的减速感
 * @author AaronZZH & Kiro
 */

import { useEffect, useRef, useState } from "react"

/**
 * @param totalMs 总时长（毫秒），默认 20000
 * @returns 当前虚拟进度（0~99）
 */
export function useFakeProgress(totalMs = 20000): number {
  const [progress, setProgress] = useState(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    let current = 0

    function scheduleNext() {
      const remaining = 99 - current
      if (remaining <= 0) return
      const step = Math.max(1, Math.floor(Math.random() * Math.min(remaining * 0.3, 8) + 1))
      const delay = (totalMs / 99) * step * (0.5 + Math.random())
      timerRef.current = setTimeout(() => {
        current = Math.min(99, current + step)
        setProgress(current)
        scheduleNext()
      }, delay)
    }

    scheduleNext()
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [totalMs])

  return progress
}
