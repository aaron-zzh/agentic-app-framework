/**
 * CommandPalette——⌘K 全局搜索（基于 shadcn Command + cmdk）
 * @author AaronZZH & Kiro
 *
 * 功能：跨实体搜索 + 导航 + 命令执行
 * 输入 > 前缀仅搜索命令
 */

"use client"

import { FileText, Search, Settings, Zap } from "lucide-react"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator
} from "@/components/ui/command"
import { entityRegistry } from "@/features/entity-engine"
import { paths } from "@/lib/constants/paths"

interface CommandPaletteProps {
  open: boolean
  onClose: () => void
}

/** 全局命令面板 */
export function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const router = useRouter()
  const [query, setQuery] = useState("")

  useEffect(() => {
    if (open) setQuery("")
  }, [open])

  const navigate = useCallback(
    (href: string) => {
      router.push(href)
      onClose()
    },
    [router, onClose]
  )

  const entities = entityRegistry.getAll()

  return (
    <CommandDialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose()
      }}
      title="搜索"
      description="搜索页面、记录或执行命令"
    >
      <CommandInput placeholder="搜索页面、记录、命令..." value={query} onValueChange={setQuery} />
      <CommandList>
        <CommandEmpty>无匹配结果</CommandEmpty>

        {/* 导航 */}
        <CommandGroup heading="页面">
          <CommandItem onSelect={() => navigate(paths.workspace.dashboard)}>
            <Search className="text-muted-foreground" />
            <span>工作台</span>
          </CommandItem>
          {entities.map((e) => (
            <CommandItem key={e.slug} onSelect={() => navigate(paths.workspace.module(e.slug))}>
              <FileText className="text-muted-foreground" />
              <span>{e.label}</span>
              <span className="ml-auto text-muted-foreground text-xs">{e.group}</span>
            </CommandItem>
          ))}
        </CommandGroup>

        <CommandSeparator />

        {/* 命令 */}
        <CommandGroup heading="命令">
          <CommandItem
            onSelect={() => {
              onClose()
            }}
          >
            <Zap className="text-muted-foreground" />
            <span>新建记录</span>
          </CommandItem>
          <CommandItem onSelect={() => navigate(paths.workspace.settings)}>
            <Settings className="text-muted-foreground" />
            <span>设置</span>
          </CommandItem>
        </CommandGroup>

        <CommandSeparator />

        {/* 最近访问（Mock） */}
        <CommandGroup heading="最近访问">
          <CommandItem onSelect={() => navigate("/workspace/document")}>
            <FileText className="text-muted-foreground" />
            <span>Q2 季度报告</span>
            <span className="ml-auto text-muted-foreground text-xs">文档</span>
          </CommandItem>
          <CommandItem onSelect={() => navigate("/workspace/task")}>
            <FileText className="text-muted-foreground" />
            <span>前端重构任务</span>
            <span className="ml-auto text-muted-foreground text-xs">任务</span>
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  )
}
