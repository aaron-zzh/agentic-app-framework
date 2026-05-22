/**
 * 文档变更 SSE 订阅 hook
 * 监听后端文档变更事件，自动刷新缓存并提示用户
 * @author AaronZZH & Kiro
 */
import { useEffect } from "react"
import { toast } from "sonner"

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

export function useDocEvents(docId: number | null, onUpdate: () => void): void {
  useEffect(() => {
    if (!docId) return
    const es = new EventSource(`${BASE_URL}/api/autodev/docs/events?docId=${docId}`)
    es.onmessage = (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data as string) as { type: string }
        if (data.type === "doc_updated") {
          onUpdate()
          toast.info("文档已更新，已自动刷新")
        }
      } catch {
        // 忽略非 JSON 消息
      }
    }
    es.onerror = () => es.close()
    return () => es.close()
  }, [docId, onUpdate])
}
