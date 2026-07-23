/**
 * EntityRecordReferencePicker——受控的跨实体记录引用选择器
 * @author AaronZZH & Kiro
 */

"use client"

import { ExternalLinkIcon, LinkIcon, XIcon } from "lucide-react"
import Link from "next/link"
import { useCallback, useState } from "react"

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
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { paths } from "@/lib/constants/paths"
import { useRelationshipPicker } from "@/lib/hooks/use-relationship-picker"
import { entityRegistry } from "@/lib/modules/entity-registry"
import type { EntityDef } from "@/lib/types/entity"

/** 可持久化的跨实体记录引用。 */
export interface EntityRecordReference {
  resource: string
  entitySlug: string
  id: string
  label: string
  imageUrl?: string
}

/** 选择器属性。 */
export interface EntityRecordReferencePickerProps {
  value?: EntityRecordReference
  onChange: (value: EntityRecordReference | undefined) => void
  /** 指定后锁定目标实体，并隐藏实体选择器。 */
  entitySlug?: string
  /** 允许引用的实体 slug 白名单。 */
  allowedEntitySlugs?: readonly string[]
  /** 禁止引用的实体 slug 黑名单；与白名单同时配置时优先排除。 */
  excludeEntitySlugs?: readonly string[]
  disabled?: boolean
  placeholder?: string
}

/** 从已完成 bootstrap 的注册表定义中筛选当前可引用的完整 CRUD 实体。 */
export function getReferenceableEntities(
  definitions: EntityDef[],
  allowedEntitySlugs?: readonly string[],
  excludeEntitySlugs?: readonly string[]
): EntityDef[] {
  const allowed = allowedEntitySlugs ? new Set(allowedEntitySlugs) : undefined
  const excluded = new Set(excludeEntitySlugs)

  return definitions.filter(
    (definition) =>
      definition.kind === "code" &&
      Boolean(definition.resource) &&
      Boolean(definition.apiPath) &&
      definition.referenceable === true &&
      (allowed === undefined || allowed.has(definition.slug)) &&
      !excluded.has(definition.slug)
  )
}

/** 选择记录前先选择受信任实体的受控引用选择器。 */
export function EntityRecordReferencePicker({
  value,
  onChange,
  entitySlug,
  allowedEntitySlugs,
  excludeEntitySlugs,
  disabled = false,
  placeholder = "搜索记录…"
}: EntityRecordReferencePickerProps) {
  const [selectedEntitySlug, setSelectedEntitySlug] = useState<string | undefined>()
  const [editing, setEditing] = useState(false)
  const [selectorOpen, setSelectorOpen] = useState(false)
  const definitions = entityRegistry.getAll()
  const referenceableEntities = getReferenceableEntities(
    definitions,
    allowedEntitySlugs,
    excludeEntitySlugs
  )
  const activeEntitySlug = entitySlug ?? selectedEntitySlug
  const activeEntity = referenceableEntities.find((entity) => entity.slug === activeEntitySlug)
  const selectedEntity = value
    ? definitions.find(
        (entity) => entity.slug === value.entitySlug && entity.resource === value.resource
      )
    : undefined
  const { query, setQuery, displayOptions, loading, recordRecent } = useRelationshipPicker(
    activeEntity ? `${activeEntity.apiPath}/_options` : undefined
  )

  const handleEntityChange = useCallback(
    (nextEntitySlug: string | null) => {
      const nextSlug = nextEntitySlug ?? undefined
      if (nextSlug === activeEntitySlug) return

      setSelectedEntitySlug(nextSlug)
      setQuery("")
      onChange(undefined)
    },
    [activeEntitySlug, onChange, setQuery]
  )

  const handleRecordSelect = useCallback(
    (id: string, label: string, imageUrl?: string) => {
      if (!activeEntity?.resource) return

      recordRecent({ id, label, imageUrl })
      onChange({
        resource: activeEntity.resource,
        entitySlug: activeEntity.slug,
        id,
        label,
        ...(imageUrl ? { imageUrl } : {})
      })
      setSelectorOpen(false)
      setEditing(false)
      setQuery("")
    },
    [activeEntity, onChange, recordRecent, setQuery]
  )

  const handleStartEditing = useCallback(() => {
    setSelectedEntitySlug(value?.entitySlug)
    setEditing(true)
    setSelectorOpen(true)
  }, [value?.entitySlug])

  const handleClear = useCallback(() => {
    onChange(undefined)
    setEditing(true)
    setSelectorOpen(true)
  }, [onChange])

  if (value && !editing) {
    const detailHref = selectedEntity
      ? paths.workspace.record(selectedEntity.slug, encodeURIComponent(value.id))
      : undefined
    const selectedText = `${selectedEntity?.label ?? value.entitySlug} · ${value.label}`

    return (
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="outline"
          className="h-10 min-w-0 flex-1 justify-start font-normal"
          disabled={disabled}
          onClick={handleStartEditing}
        >
          <LinkIcon data-icon="inline-start" />
          <span className="truncate">{selectedText}</span>
        </Button>
        {detailHref ? (
          <Button
            nativeButton={false}
            render={<Link href={detailHref} />}
            variant="outline"
            className="h-10 w-10 px-0"
            aria-label={`打开${selectedText}详情`}
          >
            <ExternalLinkIcon />
          </Button>
        ) : null}
        {!disabled ? (
          <Button
            type="button"
            variant="outline"
            className="h-10 w-10 px-0"
            onClick={handleClear}
            aria-label="清除引用记录"
          >
            <XIcon />
          </Button>
        ) : null}
      </div>
    )
  }

  return (
    <Popover open={selectorOpen} onOpenChange={setSelectorOpen}>
      <PopoverTrigger
        render={
          <Button
            type="button"
            variant="outline"
            disabled={disabled}
            className="h-10 w-full justify-start font-normal text-muted-foreground"
            aria-label="选择关联来源"
          >
            <LinkIcon data-icon="inline-start" />
            {activeEntity ? `选择${activeEntity.label}记录…` : placeholder}
          </Button>
        }
      />
      <PopoverContent className="w-72 space-y-2 p-2" align="start">
        {entitySlug ? null : (
          <Select
            value={activeEntitySlug ?? ""}
            onValueChange={handleEntityChange}
            disabled={disabled}
          >
            <SelectTrigger className="h-9 w-full" aria-label="选择引用实体">
              <SelectValue placeholder="选择实体…">{activeEntity?.label}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {referenceableEntities.map((entity) => (
                  <SelectItem key={entity.slug} value={entity.slug}>
                    {entity.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        )}
        {activeEntity ? (
          <Command>
            <CommandInput placeholder="输入关键词搜索…" value={query} onValueChange={setQuery} />
            <CommandList>
              <CommandEmpty>
                {loading ? "搜索中…" : query ? "无匹配记录" : "暂无可选记录"}
              </CommandEmpty>
              {displayOptions.length > 0 ? (
                <CommandGroup heading={query ? "搜索结果" : "可选记录"}>
                  {displayOptions.map((option) => (
                    <CommandItem
                      key={option.id}
                      value={option.id}
                      onSelect={() => handleRecordSelect(option.id, option.label, option.imageUrl)}
                    >
                      {option.label}
                    </CommandItem>
                  ))}
                </CommandGroup>
              ) : null}
            </CommandList>
          </Command>
        ) : (
          <p className="px-1 py-2 text-muted-foreground text-sm">
            {referenceableEntities.length === 0 ? "没有可引用的实体" : "请先选择实体"}
          </p>
        )}
      </PopoverContent>
    </Popover>
  )
}
