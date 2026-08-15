"use client"

import { useEffect, useRef } from "react"

interface LoadMoreOptions {
  hasNextPage: boolean
  isFetchingNextPage: boolean
  fetchNextPage: () => Promise<unknown>
}

/** 当列表底部进入视口时自动请求下一页。 */
export function useLoadMoreOnVisible({
  hasNextPage,
  isFetchingNextPage,
  fetchNextPage
}: LoadMoreOptions) {
  const targetRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const target = targetRef.current
    if (!target || !hasNextPage) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting && !isFetchingNextPage) void fetchNextPage()
      },
      { rootMargin: "160px" }
    )
    observer.observe(target)
    return () => observer.disconnect()
  }, [fetchNextPage, hasNextPage, isFetchingNextPage])

  return targetRef
}
