/**
 * useStreamingText——带队列缓冲的流式文本渲染 hook
 * 将 SSE chunks 放入队列，以固定帧率（requestAnimationFrame）逐字输出，
 * 避免大块文字一次性跳出，产生打字机效果。
 */
import { useCallback, useEffect, useRef, useState } from "react"

interface UseStreamingTextOptions {
  /** 每帧最多消费的字符数，默认 8 */
  charsPerFrame?: number
}

export function useStreamingText(options: UseStreamingTextOptions = {}) {
  const { charsPerFrame = 8 } = options
  const [text, setText] = useState("")
  const queue = useRef<string[]>([])
  const rafId = useRef<number | null>(null)
  const activeRef = useRef(false)

  const flush = useCallback(() => {
    if (queue.current.length === 0) {
      rafId.current = null
      return
    }
    // 每帧取 charsPerFrame 个字符
    const chars = queue.current.splice(0, charsPerFrame).join("")
    setText((prev) => prev + chars)
    rafId.current = requestAnimationFrame(flush)
  }, [charsPerFrame])

  const push = useCallback(
    (chunk: string) => {
      // 把 chunk 拆成单字符放入队列
      queue.current.push(...chunk.split(""))
      if (rafId.current === null) {
        rafId.current = requestAnimationFrame(flush)
      }
    },
    [flush]
  )

  const reset = useCallback(() => {
    if (rafId.current !== null) {
      cancelAnimationFrame(rafId.current)
      rafId.current = null
    }
    queue.current = []
    setText("")
    activeRef.current = false
  }, [])

  const start = useCallback(() => {
    reset()
    activeRef.current = true
  }, [reset])

  // 组件卸载时清理
  useEffect(() => {
    return () => {
      if (rafId.current !== null) cancelAnimationFrame(rafId.current)
    }
  }, [])

  return { text, push, reset, start }
}
