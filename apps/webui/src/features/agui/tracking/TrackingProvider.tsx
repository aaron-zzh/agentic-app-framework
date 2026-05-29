/**
 * 全局事件委托埋点 Provider
 * 基于事件冒泡 + data-track-* 属性实现零侵入埋点
 * @author AaronZZH & Kiro
 */
"use client"

import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useRef
} from "react"

import type { UserAction } from "../types"

/** 会话 ID（页面生命周期内唯一） */
const SESSION_ID = crypto.randomUUID()

/** 事件队列刷新间隔（毫秒） */
const FLUSH_INTERVAL = 5000

/** 队列最大长度 */
const MAX_QUEUE_SIZE = 100

interface TrackingContextValue {
  /** 手动埋点 */
  track: (action: Partial<UserAction> & Pick<UserAction, "type" | "target">) => void
}

const TrackingContext = createContext<TrackingContextValue>({
  track: () => {}
})

/** 从 DOM 元素提取 data-track-* 属性 */
function extractTrackAttrs(el: HTMLElement): Record<string, string> {
  const attrs: Record<string, string> = {}
  for (const attr of el.attributes) {
    if (attr.name.startsWith("data-track-")) {
      const key = attr.name.slice(11) // 去掉 "data-track-"
      attrs[key] = attr.value
    }
  }
  return attrs
}

/** 向上查找最近的带 data-track-* 属性的元素 */
function findTrackableAncestor(el: HTMLElement): HTMLElement | null {
  let current: HTMLElement | null = el
  while (current) {
    if (current.hasAttribute("data-track-id")) return current
    current = current.parentElement
  }
  return null
}

/** 获取当前页面上下文 */
function getPageContext(): UserAction["context"] {
  const path = typeof window !== "undefined" ? window.location.pathname : ""
  const params = typeof window !== "undefined" ? new URLSearchParams(window.location.search) : null
  return {
    page: path,
    view: params?.get("view") ?? "list",
    entity: path.split("/").filter(Boolean)[1],
    recordId: path.split("/").filter(Boolean)[2]
  }
}

/** 构建 UserAction */
function buildAction(
  type: UserAction["type"],
  target: string,
  attrs: Record<string, string>,
  value?: unknown
): UserAction {
  return {
    type,
    target,
    value,
    timestamp: Date.now(),
    semantics: {
      componentId: attrs.id ?? "",
      semanticRole: attrs.role ?? "",
      entitySlug: attrs.entity ?? undefined,
      fieldName: attrs.field ?? undefined
    },
    context: getPageContext(),
    sessionId: SESSION_ID
  }
}

export function TrackingProvider({ children }: PropsWithChildren) {
  const queueRef = useRef<UserAction[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  /** 刷新队列（批量上报） */
  const flush = useCallback(() => {
    if (queueRef.current.length === 0) return
    const batch = queueRef.current.splice(0)
    // 通过自定义事件广播，供 AI 感知层消费
    window.dispatchEvent(new CustomEvent("agui:actions", { detail: batch }))
  }, [])

  /** 入队 */
  const enqueue = useCallback(
    (action: UserAction) => {
      queueRef.current.push(action)
      if (queueRef.current.length >= MAX_QUEUE_SIZE) flush()
    },
    [flush]
  )

  /** 手动埋点接口 */
  const track = useCallback(
    (partial: Partial<UserAction> & Pick<UserAction, "type" | "target">) => {
      const action: UserAction = {
        timestamp: Date.now(),
        value: undefined,
        semantics: { componentId: "", semanticRole: "" },
        context: getPageContext(),
        sessionId: SESSION_ID,
        ...partial
      }
      enqueue(action)
    },
    [enqueue]
  )

  useEffect(() => {
    /** 点击事件委托 */
    const handleClick = (e: MouseEvent) => {
      const el = e.target as HTMLElement
      const trackable = findTrackableAncestor(el)
      if (!trackable) return
      const attrs = extractTrackAttrs(trackable)
      enqueue(buildAction("click", attrs.id ?? el.tagName, attrs))
    }

    /** 输入事件委托（防抖由消费方处理） */
    const handleInput = (e: Event) => {
      const el = e.target as HTMLInputElement
      const trackable = findTrackableAncestor(el)
      if (!trackable) return
      const attrs = extractTrackAttrs(trackable)
      enqueue(buildAction("input", attrs.id ?? el.name, attrs, el.value))
    }

    /** 表单提交 */
    const handleSubmit = (e: Event) => {
      const form = e.target as HTMLFormElement
      const trackable = findTrackableAncestor(form)
      const attrs = trackable ? extractTrackAttrs(trackable) : {}
      enqueue(buildAction("submit", attrs.id ?? (form.id || "form"), attrs))
    }

    document.addEventListener("click", handleClick, true)
    document.addEventListener("input", handleInput, true)
    document.addEventListener("submit", handleSubmit, true)

    // 定时刷新
    timerRef.current = setInterval(flush, FLUSH_INTERVAL)

    // 页面切换时刷新
    const handleVisibilityChange = () => {
      if (document.visibilityState === "hidden") flush()
    }
    document.addEventListener("visibilitychange", handleVisibilityChange)

    return () => {
      document.removeEventListener("click", handleClick, true)
      document.removeEventListener("input", handleInput, true)
      document.removeEventListener("submit", handleSubmit, true)
      document.removeEventListener("visibilitychange", handleVisibilityChange)
      if (timerRef.current) clearInterval(timerRef.current)
      flush()
    }
  }, [enqueue, flush])

  return <TrackingContext.Provider value={{ track }}>{children}</TrackingContext.Provider>
}

/** 手动埋点 hook */
export function useTracking() {
  return useContext(TrackingContext)
}
