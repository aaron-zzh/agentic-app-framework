/**
 * TableOfContents——长文侧边目录
 *
 * 从指定容器（通过 ref selector 字符串）自动提取 h2/h3 标题，生成跳转链接。
 * 滚动时使用 IntersectionObserver 高亮当前可见 section。
 * 桌面端右侧固定显示，移动端折叠（外部 LegalDocLayout 控制 hidden lg:block）。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useState } from "react"
import { cn } from "@/lib/utils/cn"

interface TocItem {
  id: string
  text: string
  level: number
}

export interface TableOfContentsProps {
  /** 文章容器的 CSS 选择器，组件会从其中提取 h2/h3 */
  selector: string
  /** 内容变化时强制刷新 TOC（如 SSR 后 hydration） */
  contentKey?: string
}

/** 中文+英文+数字 slug 化标题文本，用于锚点 id */
function slugify(text: string): string {
  return (
    text
      .trim()
      .toLowerCase()
      .replace(/\s+/g, "-")
      .replace(/[^\w\u4e00-\u9fff-]/g, "") || "section"
  )
}

export function TableOfContents({ selector, contentKey }: TableOfContentsProps) {
  const [items, setItems] = useState<TocItem[]>([])
  const [activeId, setActiveId] = useState<string>("")

  // 提取 h2/h3 并写入 id（如果原生没有）
  // biome-ignore lint/correctness/useExhaustiveDependencies: contentKey 变化时强制重新提取 TOC（SSR/异步内容更新场景）
  useEffect(() => {
    const container = document.querySelector(selector)
    if (!container) return

    const headings = Array.from(container.querySelectorAll<HTMLElement>("h2, h3"))
    const collected: TocItem[] = []
    const usedIds = new Set<string>()

    for (const h of headings) {
      const text = h.textContent?.trim() ?? ""
      if (!text) continue
      let id = h.id
      if (!id) {
        const base = slugify(text)
        let candidate = base
        let i = 1
        while (usedIds.has(candidate) || document.getElementById(candidate)) {
          candidate = `${base}-${i++}`
        }
        id = candidate
        h.id = id
      }
      usedIds.add(id)
      collected.push({
        id,
        text,
        level: h.tagName === "H2" ? 2 : 3
      })
    }
    setItems(collected)
  }, [selector, contentKey])

  // 当前可见 heading 高亮
  useEffect(() => {
    if (items.length === 0) return
    const elements = items
      .map((item) => document.getElementById(item.id))
      .filter((el): el is HTMLElement => el !== null)
    if (elements.length === 0) return

    const observer = new IntersectionObserver(
      (entries) => {
        // 取最靠近视口顶部、可见的 heading
        const visible = entries.filter((e) => e.isIntersecting)
        if (visible.length > 0) {
          const top = visible.reduce((a, b) =>
            a.boundingClientRect.top < b.boundingClientRect.top ? a : b
          )
          setActiveId(top.target.id)
        }
      },
      {
        rootMargin: "-80px 0px -70% 0px",
        threshold: 0
      }
    )

    for (const el of elements) observer.observe(el)
    return () => observer.disconnect()
  }, [items])

  if (items.length === 0) return null

  return (
    <nav aria-label="目录" className="sticky top-24 max-h-[calc(100vh-8rem)] overflow-y-auto pl-4">
      <p className="mb-3 font-medium text-foreground text-sm">目录</p>
      <ul className="space-y-1.5 border-border border-l">
        {items.map((item) => (
          <li key={item.id}>
            <a
              href={`#${item.id}`}
              className={cn(
                "-ml-px block border-l-2 py-1 pl-3 text-sm transition-colors",
                item.level === 3 && "pl-6 text-xs",
                activeId === item.id
                  ? "border-primary text-primary"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              )}
            >
              {item.text}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  )
}
