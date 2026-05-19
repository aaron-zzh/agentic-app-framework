/**
 * RelationshipPicker——关联字段（异步搜索 + 最近选择 + 多选 Tag + HoverCard 预览）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList
} from "@/components/ui/command"
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type { FieldProps } from "@/lib/types/entity"
import { useRelationshipPicker } from "@/lib/hooks/use-relationship-picker"

interface RelationshipPickerProps extends FieldProps<string | string[]> {
  multiple?: boolean
  searchEndpoint?: string
  displayField?: string
}

/** 关联字段组件 */
export function RelationshipPicker({
  name,
  value,
  onChange,
  error,
  disabled,
  multiple = false,
  searchEndpoint,
  displayField = "name"
}: RelationshipPickerProps) {
  const [open, setOpen] = useState(false)
  const { query, setQuery, displayOptions, loading, recordRecent } = useRelationshipPicker(
    searchEndpoint,
    displayField
  )

  const selectedIds = multiple
    ? Array.isArray(value)
      ? value
      : []
    : value
      ? [value as string]
      : []

  const handleSelect = useCallback(
    (id: string, label: string) => {
      recordRecent({ id, label })
      if (multiple) {
        const current = Array.isArray(value) ? value : []
        if (!current.includes(id)) {
          onChange([...current, id] as unknown as string | string[])
        }
      } else {
        onChange(id as unknown as string | string[])
        setOpen(false)
      }
      setQuery("")
    },
    [multiple, value, onChange, recordRecent, setQuery]
  )

  const handleRemove = useCallback(
    (id: string) => {
      if (multiple && Array.isArray(value)) {
        onChange(value.filter((v) => v !== id) as unknown as string | string[])
      } else {
        onChange("" as unknown as string | string[])
      }
    },
    [multiple, value, onChange]
  )

  return (
    <div className="flex flex-col gap-1.5">
      {/* 已选 Tag */}
      {selectedIds.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {selectedIds.map((id) => (
            <HoverCard key={id}>
              <HoverCardTrigger
                render={
                  <Badge variant="secondary" className="cursor-default gap-1">
                    {id}
                    {!disabled && (
                      <button
                        type="button"
                        className="ml-0.5 hover:text-destructive"
                        onClick={() => handleRemove(id)}
                        aria-label={`移除 ${id}`}
                      >
                        ×
                      </button>
                    )}
                  </Badge>
                }
              />
              <HoverCardContent className="w-48 text-muted-foreground text-xs">
                ID: {id}
              </HoverCardContent>
            </HoverCard>
          ))}
        </div>
      )}

      {/* 搜索下拉 */}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          render={
            <Button
              variant="outline"
              size="sm"
              disabled={disabled}
              className="w-full justify-start font-normal text-muted-foreground"
              aria-label={`选择${name}`}
            >
              {loading ? "搜索中…" : "搜索关联记录…"}
            </Button>
          }
        />
        <PopoverContent className="w-64 p-0" align="start">
          <Command>
            <CommandInput placeholder="输入关键词搜索…" value={query} onValueChange={setQuery} />
            <CommandList>
              <CommandEmpty>
                {loading ? "搜索中…" : query ? "无匹配结果" : "输入关键词开始搜索"}
              </CommandEmpty>
              {displayOptions.length > 0 && (
                <CommandGroup heading={query ? "搜索结果" : "最近选择"}>
                  {displayOptions.map((opt) => (
                    <CommandItem
                      key={opt.id}
                      value={opt.id}
                      onSelect={() => handleSelect(opt.id, opt.label)}
                      disabled={selectedIds.includes(opt.id)}
                    >
                      {opt.label}
                    </CommandItem>
                  ))}
                </CommandGroup>
              )}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
