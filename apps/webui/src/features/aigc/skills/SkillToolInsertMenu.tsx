/**
 * 技能正文"插入工具"入口：可搜索弹出列表，按"已选工具""系统内置"分组，选中后回调工具名。
 *
 * 列表以中文名为主、工具名为副，中文名与工具名都可搜索；插入正文的始终是工具名。
 *
 * @example
 * <SkillToolInsertMenu
 *   selectedTools={[{ name: "agriSensorRealtime", label: "大棚实时环境" }]}
 *   systemTools={[{ name: "promote_to_task", label: "升级为任务" }]}
 *   onInsert={insert}
 * />
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Wrench } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList
} from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"

/** 可插入的工具：name 为插入正文的工具名，label 为展示用中文名（没有时与 name 相同）。 */
export interface SkillToolInsertItem {
  name: string
  label: string
}

export interface SkillToolInsertMenuProps {
  /** 当前白名单已选工具 */
  selectedTools: SkillToolInsertItem[]
  /** 系统内置工具 */
  systemTools: SkillToolInsertItem[]
  onInsert: (toolName: string) => void
  disabled?: boolean
}

function ToolItemLabel({ item }: { item: SkillToolInsertItem }) {
  return (
    <span className="flex min-w-0 items-baseline gap-2">
      <span className="truncate text-sm">{item.label}</span>
      {item.label !== item.name && (
        <code className="truncate font-mono text-muted-foreground text-xs">{item.name}</code>
      )}
    </span>
  )
}

export function SkillToolInsertMenu({
  selectedTools,
  systemTools,
  onInsert,
  disabled
}: SkillToolInsertMenuProps) {
  const { value: open, setValue: setOpen } = useBoolean()

  const handleSelect = (toolName: string) => {
    setOpen(false)
    onInsert(toolName)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={<Button type="button" variant="outline" size="sm" disabled={disabled} />}
      >
        <Wrench data-icon="inline-start" />
        插入工具
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="end">
        <Command>
          <CommandInput placeholder="搜索中文名或工具名…" />
          <CommandList>
            <CommandEmpty>无匹配工具</CommandEmpty>
            {selectedTools.length > 0 && (
              <CommandGroup heading="已选工具">
                {selectedTools.map((item) => (
                  <CommandItem
                    key={`selected:${item.name}`}
                    value={`selected:${item.name}`}
                    keywords={[item.name, item.label]}
                    onSelect={() => handleSelect(item.name)}
                  >
                    <ToolItemLabel item={item} />
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
            <CommandGroup heading="系统内置">
              {systemTools.map((item) => (
                <CommandItem
                  key={`system:${item.name}`}
                  value={`system:${item.name}`}
                  keywords={[item.name, item.label]}
                  onSelect={() => handleSelect(item.name)}
                >
                  <ToolItemLabel item={item} />
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
