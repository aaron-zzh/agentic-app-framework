/**
 * 页面语义状态 hook
 * 实时返回当前页面的语义描述，供 AI 感知层消费
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useEffect, useRef, useState } from "react"

import type { PageSemantics } from "../types"
import { collectPageSemantics, setPageMeta } from "./PageSemanticsCollector"

interface UsePageSemanticsOptions {
  route: string
  title: string
  description?: string
  entity?: string
  recordId?: string
  view?: string
  /** 刷新间隔（毫秒），默认 2000 */
  refreshInterval?: number
}

/**
 * 实时返回当前页面语义状态
 * 自动设置页面元数据并定期收集语义快照
 */
export function usePageSemantics(options: UsePageSemanticsOptions): PageSemantics {
  const {
    route,
    title,
    description = "",
    entity,
    recordId,
    view = "list",
    refreshInterval = 2000
  } = options

  const [semantics, setSemantics] = useState<PageSemantics>({
    route,
    title,
    description,
    currentEntity: entity,
    currentRecord: recordId,
    activeView: view,
    availableActions: [],
    pendingChanges: false,
    components: []
  })

  const optionsRef = useRef(options)
  optionsRef.current = options

  // 设置页面元数据
  useEffect(() => {
    setPageMeta({ route, title, description, entity, recordId, view })
  }, [route, title, description, entity, recordId, view])

  // 定期收集语义
  const collect = useCallback(() => {
    setSemantics(collectPageSemantics())
  }, [])

  useEffect(() => {
    collect()
    const timer = setInterval(collect, refreshInterval)
    return () => clearInterval(timer)
  }, [collect, refreshInterval])

  return semantics
}
