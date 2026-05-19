/**
 * useLongPress——长按手势 hook（触摸优化）
 * @author AaronZZH & Kiro
 *
 * 用于移动端长按触发批量选择等操作。
 *
 * @example
 * ```tsx
 * const longPressProps = useLongPress(() => onSelect(id), { delay: 500 })
 * <div {...longPressProps}>内容</div>
 * ```
 */

"use client"

import { useCallback, useRef } from "react"

interface LongPressOptions {
  /** 长按触发延迟（ms），默认 500 */
  delay?: number
  /** 移动阈值（px），超过则取消长按，默认 10 */
  moveThreshold?: number
}

interface LongPressHandlers {
  onPointerDown: (e: React.PointerEvent) => void
  onPointerUp: () => void
  onPointerLeave: () => void
  onPointerMove: (e: React.PointerEvent) => void
}

/** 长按手势 hook */
export function useLongPress(
  callback: () => void,
  options: LongPressOptions = {}
): LongPressHandlers {
  const { delay = 500, moveThreshold = 10 } = options
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const startPos = useRef<{ x: number; y: number } | null>(null)

  const clear = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
    startPos.current = null
  }, [])

  const onPointerDown = useCallback(
    (e: React.PointerEvent) => {
      startPos.current = { x: e.clientX, y: e.clientY }
      timerRef.current = setTimeout(() => {
        callback()
        timerRef.current = null
      }, delay)
    },
    [callback, delay]
  )

  const onPointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (!startPos.current) return
      const dx = Math.abs(e.clientX - startPos.current.x)
      const dy = Math.abs(e.clientY - startPos.current.y)
      if (dx > moveThreshold || dy > moveThreshold) {
        clear()
      }
    },
    [clear, moveThreshold]
  )

  return {
    onPointerDown,
    onPointerUp: clear,
    onPointerLeave: clear,
    onPointerMove
  }
}
