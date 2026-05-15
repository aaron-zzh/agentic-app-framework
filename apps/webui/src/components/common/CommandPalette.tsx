/**
 * CommandPalette——⌘K 全局搜索（跨实体搜索 + 命令 + 导航）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { useRouter } from "next/navigation"

import { entityRegistry } from "@/features/entity-engine"
import type { EntityDef } from "@/features/entity-engine/types"

/** 搜索结果条目 */
interface SearchResult {
  type: "entity" | "navigation" | "command"
  label: string
  description?: string
  href?: string
  action?: () => void
}

interface CommandPaletteProps {
  open: boolean
  onClose: () => void
}

/** 全局命令面板 */
export function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const [query, setQuery] = useState("")
  const [results, setResults] = useState<SearchResult[]>([])
  const [selectedIndex, setSelectedIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const router = useRouter()

  // 打开时聚焦
  useEffect(() => {
    if (open) {
      setQuery("")
      setResults(getDefaultResults())
      setSelectedIndex(0)
      setTimeout(() => inputRef.current?.focus(), 50)
    }
  }, [open])

  // 搜索逻辑
  useEffect(() => {
    if (!query.trim()) {
      setResults(getDefaultResults())
      return
    }

    const isCommand = query.startsWith(">")
    if (isCommand) {
      setResults(searchCommands(query.slice(1).trim()))
    } else {
      setResults(searchAll(query.trim()))
    }
    setSelectedIndex(0)
  }, [query])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "ArrowDown") {
        e.preventDefault()
        setSelectedIndex((i) => Math.min(i + 1, results.length - 1))
      } else if (e.key === "ArrowUp") {
        e.preventDefault()
        setSelectedIndex((i) => Math.max(i - 1, 0))
      } else if (e.key === "Enter") {
        e.preventDefault()
        const item = results[selectedIndex]
        if (item?.href) { router.push(item.href); onClose() }
        else if (item?.action) { item.action(); onClose() }
      } else if (e.key === "Escape") {
        onClose()
      }
    },
    [results, selectedIndex, router, onClose]
  )

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[20vh]">
      {/* 遮罩 */}
      <div className="absolute inset-0 bg-black/50" onClick={onClose} onKeyDown={undefined} />
      {/* 面板 */}
      <div className="relative w-full max-w-lg rounded-lg border bg-background shadow-xl">
        <input
          ref={inputRef}
          className="w-full border-b bg-transparent px-4 py-3 text-sm outline-none placeholder:text-muted-foreground"
          placeholder="搜索实体、记录、命令... (> 仅搜命令)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        <ul className="max-h-72 overflow-auto p-2">
          {results.map((item, i) => (
            <li key={`${item.type}-${item.label}`}>
              <button
                type="button"
                className={`flex w-full items-center gap-3 rounded px-3 py-2 text-left text-sm ${i === selectedIndex ? "bg-muted" : "hover:bg-muted/50"}`}
                onClick={() => {
                  if (item.href) { router.push(item.href); onClose() }
                  else if (item.action) { item.action(); onClose() }
                }}
              >
                <span className="text-xs text-muted-foreground">
                  {item.type === "entity" ? "📄" : item.type === "navigation" ? "→" : "⚡"}
                </span>
                <span className="flex-1">
                  <span>{item.label}</span>
                  {item.description && (
                    <span className="ml-2 text-xs text-muted-foreground">{item.description}</span>
                  )}
                </span>
              </button>
            </li>
          ))}
          {results.length === 0 && (
            <li className="px-3 py-4 text-center text-sm text-muted-foreground">无结果</li>
          )}
        </ul>
      </div>
    </div>
  )
}

/** 默认结果：导航到各实体 */
function getDefaultResults(): SearchResult[] {
  const entities = entityRegistry.getAll()
  return entities.map((e: EntityDef) => ({
    type: "navigation" as const,
    label: e.label,
    description: e.description,
    href: `/${e.slug}`,
  }))
}

/** 跨实体搜索（前端匹配实体名/描述） */
function searchAll(query: string): SearchResult[] {
  const q = query.toLowerCase()
  const entities = entityRegistry.getAll()

  const navResults: SearchResult[] = entities
    .filter((e: EntityDef) => e.label.toLowerCase().includes(q) || e.slug.includes(q))
    .map((e: EntityDef) => ({
      type: "navigation" as const,
      label: e.label,
      description: `前往 ${e.labelPlural ?? e.label}`,
      href: `/${e.slug}`,
    }))

  // TODO: 后端 /api/search?q= 跨实体记录搜索
  return navResults
}

/** 搜索命令 */
function searchCommands(query: string): SearchResult[] {
  const commands: SearchResult[] = [
    { type: "command", label: "新建记录", description: "在当前实体创建", action: () => {} },
    { type: "command", label: "切换主题", description: "深色/浅色", action: () => {} },
    { type: "command", label: "导出数据", description: "导出当前列表", action: () => {} },
  ]
  if (!query) return commands
  const q = query.toLowerCase()
  return commands.filter((c) => c.label.toLowerCase().includes(q))
}
