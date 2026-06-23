/**
 * CommandPalette——⌘K 全局命令面板（基于 shadcn Command + cmdk）
 * @author AaronZZH & Kiro
 *
 * 功能：
 * - 跨实体搜索 + 导航 + 命令执行 + 最近访问
 * - 输入 `>` 前缀进入命令模式（仅显示命令组）
 * - 支持插件通过 commandRegistry 注册自定义命令
 * - 选择导航项自动记录到最近访问
 *
 * @example
 * ```tsx
 * const { open, onClose, recentItems, addRecent, commands } = useCommandPalette()
 * <CommandPalette open={open} onClose={onClose} recentItems={recentItems} addRecent={addRecent} commands={commands} />
 * ```
 */

"use client"

import { Clock, LayoutDashboard, Navigation, Zap } from "lucide-react"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useMemo, useState } from "react"

import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator
} from "@/components/ui/command"
import { paths } from "@/lib/constants/paths"
import type { CommandItem as CommandDef, RecentItem } from "@/lib/hooks/use-command-palette"
import { entityRegistry } from "@/lib/modules/entity-registry"

// ─── Props ────────────────────────────────────────────────────────────────

interface CommandPaletteProps {
  open: boolean
  onClose: () => void
  commands: CommandDef[]
  recentItems: RecentItem[]
  addRecent: (item: Omit<RecentItem, "timestamp">) => void
}

/** 全局命令面板 */
export function CommandPalette({
  open,
  onClose,
  commands,
  recentItems,
  addRecent
}: CommandPaletteProps) {
  const router = useRouter()
  const [query, setQuery] = useState("")

  // 打开时清空搜索
  useEffect(() => {
    if (open) setQuery("")
  }, [open])

  /** 是否为命令模式（> 前缀） */
  const isCommandMode = query.startsWith(">")

  /** 导航到目标并记录最近访问 */
  const navigate = useCallback(
    (href: string, label: string, subtitle?: string, icon?: string) => {
      addRecent({ id: href, label, subtitle, href, icon })
      router.push(href)
      onClose()
    },
    [router, onClose, addRecent]
  )

  /** 执行命令 */
  const executeCommand = useCallback(
    (cmd: CommandDef) => {
      cmd.action()
      onClose()
    },
    [onClose]
  )

  /** 从实体注册表生成导航项 */
  const navigationItems = useMemo(() => {
    const entities = entityRegistry.getAll()
    return entities.map((e) => ({
      slug: e.slug,
      label: e.label,
      group: e.groupLabel ?? e.group ?? "",
      icon: e.icon,
      href: paths.workspace.module(e.slug)
    }))
  }, [])

  /** 按 group 分组的命令 */
  const commandGroups = useMemo(() => {
    const groups: Record<string, CommandDef[]> = {}
    for (const cmd of commands) {
      const g = cmd.group || "命令"
      if (!groups[g]) groups[g] = []
      groups[g].push(cmd)
    }
    return groups
  }, [commands])

  return (
    <CommandDialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose()
      }}
      title="命令面板"
      description="搜索页面、记录或执行命令。"
    >
      <CommandInput
        placeholder={isCommandMode ? "输入命令..." : "搜索页面、记录、命令..."}
        value={query}
        onValueChange={setQuery}
      />
      <CommandList>
        <CommandEmpty>无匹配结果</CommandEmpty>

        {/* 命令模式：仅显示命令 */}
        {isCommandMode ? (
          Object.entries(commandGroups).map(([group, cmds]) => (
            <CommandGroup key={group} heading={group}>
              {cmds.map((cmd) => (
                <CommandItem key={cmd.id} onSelect={() => executeCommand(cmd)}>
                  <Zap className="text-muted-foreground" />
                  <span>{cmd.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          ))
        ) : (
          <>
            {/* 最近访问 */}
            {recentItems.length > 0 && (
              <>
                <CommandGroup heading="最近访问">
                  {recentItems.map((item) => (
                    <CommandItem
                      key={item.id}
                      onSelect={() => navigate(item.href, item.label, item.subtitle, item.icon)}
                    >
                      <Clock className="text-muted-foreground" />
                      <span>{item.label}</span>
                      {item.subtitle && (
                        <span className="ml-auto text-muted-foreground text-xs">
                          {item.subtitle}
                        </span>
                      )}
                    </CommandItem>
                  ))}
                </CommandGroup>
                <CommandSeparator />
              </>
            )}

            {/* 导航 */}
            <CommandGroup heading="导航">
              <CommandItem
                onSelect={() => navigate(paths.workspace.dashboard, "工作台", undefined, undefined)}
              >
                <LayoutDashboard className="text-muted-foreground" />
                <span>工作台</span>
              </CommandItem>
              {navigationItems.map((item) => (
                <CommandItem
                  key={item.slug}
                  onSelect={() => navigate(item.href, item.label, item.group, item.icon)}
                >
                  <Navigation className="text-muted-foreground" />
                  <span>{item.label}</span>
                  <span className="ml-auto text-muted-foreground text-xs">{item.group}</span>
                </CommandItem>
              ))}
            </CommandGroup>

            {/* 命令 */}
            {commands.length > 0 && (
              <>
                <CommandSeparator />
                {Object.entries(commandGroups).map(([group, cmds]) => (
                  <CommandGroup key={group} heading={group}>
                    {cmds.map((cmd) => (
                      <CommandItem key={cmd.id} onSelect={() => executeCommand(cmd)}>
                        <Zap className="text-muted-foreground" />
                        <span>{cmd.label}</span>
                      </CommandItem>
                    ))}
                  </CommandGroup>
                ))}
              </>
            )}
          </>
        )}
      </CommandList>
    </CommandDialog>
  )
}
