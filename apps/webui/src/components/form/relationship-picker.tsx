/**
 * RelationshipPicker——关联字段（异步搜索 + 最近选择 + 多选 Tag + HoverCard 预览）
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckIcon } from "lucide-react"
import { useCallback, useMemo, useState } from "react"

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
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
import { type RelationOption, useRelationshipPicker } from "@/lib/hooks/use-relationship-picker"
import type { FieldProps } from "@/lib/types/entity"

interface RelationshipPickerProps extends FieldProps<string | string[]> {
  multiple?: boolean
  placeholder?: string
  searchEndpoint?: string
  displayField?: string
  selectedOptions?: RelationOption[]
}

function avatarFallback(label: string) {
  return label.trim().charAt(0).toLocaleUpperCase() || "?"
}

/** 关联字段组件 */
export function RelationshipPicker({
  name,
  value,
  onChange,
  error,
  disabled,
  multiple = false,
  placeholder = "搜索关联记录…",
  searchEndpoint,
  displayField = "name",
  selectedOptions = []
}: RelationshipPickerProps) {
  const [open, setOpen] = useState(false)
  const { query, setQuery, displayOptions, loading, recordRecent } = useRelationshipPicker(
    searchEndpoint,
    displayField
  )

  const selectedIds = multiple
    ? Array.isArray(value)
      ? value.filter((id) => id.trim().length > 0)
      : []
    : typeof value === "string" && value.trim().length > 0
      ? [value]
      : []
  const optionsById = useMemo(
    () => new Map([...selectedOptions, ...displayOptions].map((option) => [option.id, option])),
    [displayOptions, selectedOptions]
  )

  const handleSelect = useCallback(
    (id: string, label: string, imageUrl?: string) => {
      recordRecent({ id, label, imageUrl })
      if (multiple) {
        const current = Array.isArray(value) ? value.filter((item) => item.trim().length > 0) : []
        onChange(current.includes(id) ? current.filter((item) => item !== id) : [...current, id])
      } else {
        onChange(id)
        setOpen(false)
      }
      setQuery("")
    },
    [multiple, value, onChange, recordRecent, setQuery]
  )

  const selectedOption =
    !multiple && selectedIds[0]
      ? (optionsById.get(selectedIds[0]) ?? { id: selectedIds[0], label: selectedIds[0] })
      : undefined

  return (
    <div className="flex flex-col gap-1.5">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          render={
            <Button
              variant="outline"
              size="sm"
              disabled={disabled}
              className="h-auto min-h-10 w-full justify-start py-1 font-normal text-muted-foreground"
              aria-label={`选择${name}`}
            >
              {multiple && selectedIds.length > 0 ? (
                <span className="flex min-w-0 flex-1 flex-wrap gap-1">
                  {selectedIds.map((id) => {
                    const option = optionsById.get(id) ?? { id, label: id }
                    return (
                      <HoverCard key={id}>
                        <HoverCardTrigger
                          render={
                            <Badge
                              variant="secondary"
                              className="h-7 max-w-full cursor-pointer gap-1.5 px-1.5"
                            >
                              <Avatar size="sm">
                                {option.imageUrl ? (
                                  <AvatarImage src={option.imageUrl} alt="" />
                                ) : null}
                                <AvatarFallback>{avatarFallback(option.label)}</AvatarFallback>
                              </Avatar>
                              <span className="max-w-40 truncate">{option.label}</span>
                            </Badge>
                          }
                        />
                        <HoverCardContent className="w-48 text-muted-foreground text-xs">
                          {option.label}
                        </HoverCardContent>
                      </HoverCard>
                    )
                  })}
                </span>
              ) : selectedOption ? (
                <>
                  <Avatar size="sm">
                    {selectedOption.imageUrl ? (
                      <AvatarImage src={selectedOption.imageUrl} alt="" />
                    ) : null}
                    <AvatarFallback>{avatarFallback(selectedOption.label)}</AvatarFallback>
                  </Avatar>
                  <span className="truncate text-foreground">{selectedOption.label}</span>
                </>
              ) : loading ? (
                "搜索中…"
              ) : (
                placeholder
              )}
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
              {displayOptions.length > 0 ? (
                <CommandGroup heading={query ? "搜索结果" : "可选记录"}>
                  {displayOptions.map((option) => {
                    const selected = selectedIds.includes(option.id)
                    return (
                      <CommandItem
                        key={option.id}
                        value={option.id}
                        onSelect={() => handleSelect(option.id, option.label, option.imageUrl)}
                      >
                        <Avatar size="sm">
                          {option.imageUrl ? <AvatarImage src={option.imageUrl} alt="" /> : null}
                          <AvatarFallback>{avatarFallback(option.label)}</AvatarFallback>
                        </Avatar>
                        <span className="truncate">{option.label}</span>
                        {selected ? <CheckIcon className="ml-auto size-4 text-primary" /> : null}
                      </CommandItem>
                    )
                  })}
                </CommandGroup>
              ) : null}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

      {error ? <p className="text-destructive text-xs">{error}</p> : null}
    </div>
  )
}
