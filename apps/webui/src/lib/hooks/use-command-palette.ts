/**
 * useCommandPalette——命令面板状态管理 + 命令注册 + 最近访问
 * @author AaronZZH & Kiro
 *
 * 提供全局命令注册、⌘K 快捷键、最近访问记录管理。
 *
 * @example
 * ```ts
 * import { useCommandPalette, commandRegistry } from "@/lib/hooks/use-command-palette"
 *
 * // 注册命令
 * commandRegistry.register({ id: "new-doc", label: "新建文档", icon: "file-plus", group: "命令", action: () => {} })
 *
 * // 组件中使用
 * const { open, onClose, recentItems, addRecent } = useCommandPalette()
 * ```
 */

"use client"

import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react"

/** 命令定义 */
export interface CommandItem {
  id: string
  label: string
  icon?: string
  group: string
  action: () => void
  /** 搜索关键词（可选，默认用 label） */
  keywords?: string[]
}

/** 最近访问项 */
export interface RecentItem {
  id: string
  label: string
  icon?: string
  /** 来源实体/模块 */
  subtitle?: string
  href: string
  timestamp: number
}

const RECENT_STORAGE_KEY = "aaf-command-palette-recent"
const MAX_RECENT = 10
const EMPTY_COMMANDS: CommandItem[] = []

// ─── 命令注册表（全局单例） ─────────────────────────────────────────────────

type Listener = () => void

class CommandRegistry {
  private commands = new Map<string, CommandItem>()
  private listeners = new Set<Listener>()
  private snapshot: CommandItem[] = EMPTY_COMMANDS

  /** 注册命令 */
  register(cmd: CommandItem): void {
    this.commands.set(cmd.id, cmd)
    this.updateSnapshot()
    this.notify()
  }

  /** 批量注册 */
  registerAll(cmds: CommandItem[]): void {
    for (const cmd of cmds) {
      this.commands.set(cmd.id, cmd)
    }
    this.updateSnapshot()
    this.notify()
  }

  /** 注销命令 */
  unregister(id: string): void {
    this.commands.delete(id)
    this.updateSnapshot()
    this.notify()
  }

  /** 获取所有命令 */
  getAll(): CommandItem[] {
    return this.snapshot
  }

  /** 订阅变更（useSyncExternalStore 用） */
  subscribe(listener: Listener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  /** 获取快照（useSyncExternalStore 用） */
  getSnapshot(): CommandItem[] {
    return this.snapshot
  }

  private updateSnapshot(): void {
    this.snapshot = this.commands.size > 0 ? Array.from(this.commands.values()) : EMPTY_COMMANDS
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener()
    }
  }
}

/** 全局命令注册表单例 */
export const commandRegistry = new CommandRegistry()

// ─── 最近访问管理 ─────────────────────────────────────────────────────────

function loadRecent(): RecentItem[] {
  if (typeof window === "undefined") return []
  try {
    const raw = localStorage.getItem(RECENT_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as RecentItem[]) : []
  } catch {
    return []
  }
}

function saveRecent(items: RecentItem[]): void {
  try {
    localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(items))
  } catch {
    /* localStorage 不可用时静默忽略 */
  }
}

// ─── Hook ─────────────────────────────────────────────────────────────────

/** 命令面板状态 Hook */
export function useCommandPalette() {
  const [open, setOpen] = useState(false)
  const [recentItems, setRecentItems] = useState<RecentItem[]>([])

  // 订阅命令注册表变更
  const commands = useSyncExternalStore(
    (cb) => commandRegistry.subscribe(cb),
    () => commandRegistry.getSnapshot(),
    () => EMPTY_COMMANDS
  )

  // 加载最近访问
  useEffect(() => {
    setRecentItems(loadRecent())
  }, [])

  // ⌘K / Ctrl+K 快捷键
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault()
        setOpen((v) => !v)
      }
    }
    document.addEventListener("keydown", handleKeyDown)
    return () => document.removeEventListener("keydown", handleKeyDown)
  }, [])

  const onClose = useCallback(() => setOpen(false), [])

  /** 添加最近访问记录 */
  const addRecent = useCallback((item: Omit<RecentItem, "timestamp">) => {
    setRecentItems((prev) => {
      const filtered = prev.filter((r) => r.id !== item.id)
      const next = [{ ...item, timestamp: Date.now() }, ...filtered].slice(0, MAX_RECENT)
      saveRecent(next)
      return next
    })
  }, [])

  /** 清空最近访问 */
  const clearRecent = useCallback(() => {
    setRecentItems([])
    saveRecent([])
  }, [])

  return useMemo(
    () => ({ open, onClose, commands, recentItems, addRecent, clearRecent }),
    [open, onClose, commands, recentItems, addRecent, clearRecent]
  )
}
