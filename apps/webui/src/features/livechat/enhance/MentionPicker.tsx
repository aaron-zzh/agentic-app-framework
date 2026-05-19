/**
 * MentionPicker——@提及实体选择器
 * 输入 @ 后弹出实体搜索面板，选中后插入提及标记
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckSquare, FileText, type LucideIcon, User } from "lucide-react"
import { useCallback, useState } from "react"
import { Badge } from "@/components/ui/badge"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList
} from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"

/** 实体类型 */
export type MentionEntityType = "user" | "document" | "task"

/** 可提及的实体 */
export interface MentionEntity {
  id: string
  name: string
  type: MentionEntityType
}

/** 实体类型图标映射 */
const entityIcons: Record<MentionEntityType, LucideIcon> = {
  user: User,
  document: FileText,
  task: CheckSquare
}

/** 实体类型标签 */
const entityLabels: Record<MentionEntityType, string> = {
  user: "用户",
  document: "文档",
  task: "任务"
}

/** 模拟数据 */
const mockEntities: MentionEntity[] = [
  { id: "u1", name: "张三", type: "user" },
  { id: "u2", name: "李四", type: "user" },
  { id: "d1", name: "产品设计文档", type: "document" },
  { id: "d2", name: "Q2 季度报告", type: "document" },
  { id: "t1", name: "完成登录模块", type: "task" },
  { id: "t2", name: "修复导出 Bug", type: "task" }
]

interface MentionPickerProps {
  /** 当前输入值 */
  inputValue: string
  /** 选中实体回调 */
  onMentionSelect: (entity: MentionEntity) => void
  /** 点击已提及实体跳转 */
  onNavigate?: (entityType: MentionEntityType, entityId: string) => void
  /** 弹出锚点 */
  children: React.ReactNode
}

/**
 * @提及选择器
 * 当 inputValue 包含 @ 时弹出实体搜索面板
 */
export function MentionPicker({ inputValue, onMentionSelect, children }: MentionPickerProps) {
  const [open, setOpen] = useState(false)

  /** 检测是否应该显示（最后一个 @ 后无空格） */
  const atIndex = inputValue.lastIndexOf("@")
  const shouldShow = atIndex >= 0 && !inputValue.slice(atIndex).includes(" ")

  const handleSelect = useCallback(
    (entityId: string) => {
      const entity = mockEntities.find((e) => e.id === entityId)
      if (entity) {
        onMentionSelect(entity)
      }
      setOpen(false)
    },
    [onMentionSelect]
  )

  return (
    <Popover open={shouldShow || open} onOpenChange={setOpen}>
      <PopoverTrigger render={<div>{children}</div>} />
      {shouldShow && (
        <PopoverContent side="top" align="start" className="w-72 p-0">
          <Command>
            <CommandInput placeholder="搜索用户、文档、任务…" />
            <CommandList>
              <CommandEmpty>未找到匹配实体</CommandEmpty>
              {(["user", "document", "task"] as MentionEntityType[]).map((type) => {
                const items = mockEntities.filter((e) => e.type === type)
                if (items.length === 0) return null
                const Icon = entityIcons[type]
                return (
                  <CommandGroup key={type} heading={entityLabels[type]}>
                    {items.map((entity) => (
                      <CommandItem
                        key={entity.id}
                        value={`${entity.name}-${entity.id}`}
                        onSelect={() => handleSelect(entity.id)}
                      >
                        <Icon className="size-4 text-muted-foreground" />
                        <span>{entity.name}</span>
                      </CommandItem>
                    ))}
                  </CommandGroup>
                )
              })}
            </CommandList>
          </Command>
        </PopoverContent>
      )}
    </Popover>
  )
}

/** 提及标记——Badge 样式展示已提及的实体 */
interface MentionBadgeProps {
  entity: MentionEntity
  onNavigate?: (entityType: MentionEntityType, entityId: string) => void
}

export function MentionBadge({ entity, onNavigate }: MentionBadgeProps) {
  const Icon = entityIcons[entity.type]
  return (
    <Badge
      variant="secondary"
      className="cursor-pointer gap-1"
      onClick={() => onNavigate?.(entity.type, entity.id)}
    >
      <Icon className="size-3" />@{entity.name}
    </Badge>
  )
}
