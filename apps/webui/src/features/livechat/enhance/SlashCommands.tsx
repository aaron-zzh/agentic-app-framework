/**
 * SlashCommands——斜杠命令系统
 * 输入 / 后弹出命令列表，支持模糊搜索和自定义命令注册
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Download,
  FilePlus,
  HelpCircle,
  type LucideIcon,
  Mic,
  Search,
  Settings
} from "lucide-react"
import { useCallback, useMemo, useRef, useState } from "react"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList
} from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"

/** 斜杠命令定义 */
export interface SlashCommand {
  name: string
  description: string
  icon: LucideIcon
  action: () => void
}

/** 预定义命令列表 */
const builtinCommands: SlashCommand[] = [
  { name: "create", description: "创建新实体", icon: FilePlus, action: () => {} },
  { name: "search", description: "搜索记录", icon: Search, action: () => {} },
  { name: "help", description: "查看帮助", icon: HelpCircle, action: () => {} },
  { name: "settings", description: "打开设置", icon: Settings, action: () => {} },
  { name: "export", description: "导出对话", icon: Download, action: () => {} },
  { name: "voice", description: "语音输入", icon: Mic, action: () => {} }
]

/** 命令注册表（模块级单例） */
const customCommands: SlashCommand[] = []

/** 注册自定义命令 */
export function registerCommand(cmd: SlashCommand): void {
  customCommands.push(cmd)
}

interface SlashCommandsProps {
  /** 当前输入值 */
  inputValue: string
  /** 命令执行回调（执行后清空输入） */
  onCommandExecute: (commandName: string) => void
  /** 弹出锚点（children 作为 trigger） */
  children: React.ReactNode
}

/**
 * 斜杠命令弹出面板
 * 当 inputValue 以 / 开头时自动弹出
 */
export function SlashCommands({ inputValue, onCommandExecute, children }: SlashCommandsProps) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef<HTMLDivElement>(null)

  /** 是否应该显示命令面板 */
  const shouldShow = inputValue.startsWith("/") && inputValue.length >= 1

  /** 所有可用命令 */
  const allCommands = useMemo(() => [...builtinCommands, ...customCommands], [])

  const handleSelect = useCallback(
    (commandName: string) => {
      const cmd = allCommands.find((c) => c.name === commandName)
      if (cmd) {
        cmd.action()
        onCommandExecute(commandName)
      }
      setOpen(false)
    },
    [allCommands, onCommandExecute]
  )

  return (
    <Popover open={shouldShow || open} onOpenChange={setOpen}>
      <PopoverTrigger render={<div ref={triggerRef}>{children}</div>} />
      {shouldShow && (
        <PopoverContent side="top" align="start" className="w-64 p-0">
          <Command>
            <CommandInput placeholder="搜索命令…" />
            <CommandList>
              <CommandEmpty>无匹配命令</CommandEmpty>
              <CommandGroup heading="命令">
                {allCommands.map((cmd) => (
                  <CommandItem key={cmd.name} value={cmd.name} onSelect={handleSelect}>
                    <cmd.icon className="size-4 text-muted-foreground" />
                    <span>/{cmd.name}</span>
                    <span className="ml-auto text-muted-foreground text-xs">{cmd.description}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      )}
    </Popover>
  )
}
