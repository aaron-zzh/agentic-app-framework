/**
 * 技能选择弹窗内容。
 *
 * 展示“我的/公共”技能目录，按搜索词查询；创建成功后可立即选中。
 *
 * @example
 * <SkillPickerContent onClose={() => setOpen(false)} />
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean, useTabs } from "@aaf/hooks"
import { Check, Pencil, Plus, Search, Zap } from "lucide-react"
import { useMemo, useState } from "react"
import { useDebounce } from "use-debounce"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { SkillEditorDialog } from "@/features/aigc/skills/SkillEditorDialog"
import { useAigcStore } from "@/features/aigc/store"
import { type AiSkillVO, useAiSkillMeta, useMyAiSkills, usePublicAiSkills } from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils"

function sourceLabel(skill: AiSkillVO): string {
  return skill.ownerId === null ? "内置" : "工作区"
}

function SkillCard({
  skill,
  selected,
  showSource,
  onSelect,
  onEdit
}: {
  skill: AiSkillVO
  selected: boolean
  showSource: boolean
  onSelect: () => void
  onEdit?: () => void
}) {
  return (
    <div className="relative">
      <button
        type="button"
        onClick={onSelect}
        className={cn(
          "flex w-full items-start gap-3 rounded-xl border px-3 py-2.5 text-left transition-colors",
          onEdit && "pr-11",
          selected
            ? "border-primary/40 bg-primary/10"
            : "border-foreground/6 bg-foreground/2 hover:bg-foreground/5"
        )}
      >
        <div
          className={cn(
            "mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg font-semibold text-sm",
            selected ? "bg-primary/20 text-primary" : "bg-foreground/8 text-foreground/60"
          )}
        >
          {skill.name.slice(0, 1)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <span className="truncate font-medium text-sm">{skill.name}</span>
            {showSource ? <Badge variant="secondary">{sourceLabel(skill)}</Badge> : null}
            {selected ? <Check className="shrink-0 text-primary" /> : null}
          </div>
          {skill.summary ? (
            <p className="mt-0.5 line-clamp-2 text-muted-foreground text-xs">{skill.summary}</p>
          ) : null}
        </div>
      </button>
      {onEdit ? (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="absolute top-2 right-2"
          onClick={onEdit}
          aria-label={`编辑${skill.name}`}
        >
          <Pencil />
        </Button>
      ) : null}
    </div>
  )
}

interface SkillPickerContentProps {
  onClose?: () => void
}

export function SkillPickerContent({ onClose }: SkillPickerContentProps) {
  const directory = useTabs("MINE")
  const editorDialog = useBoolean()
  const [editTarget, setEditTarget] = useState<AiSkillVO | null>(null)
  const [search, setSearch] = useState("")
  const [debouncedSearch] = useDebounce(search.trim(), 300)

  const selectedSkillId = useAigcStore((state) => state.selectedSkillId)
  const setSelectedSkillId = useAigcStore((state) => state.setSelectedSkillId)
  const queryParams = {
    search: debouncedSearch || undefined,
    pageNo: 1,
    pageSize: 100
  }
  const mineQuery = useMyAiSkills(queryParams, directory.value === "MINE")
  const publicQuery = usePublicAiSkills(queryParams, directory.value === "PUBLIC")
  const metaQuery = useAiSkillMeta()
  const activeQuery = directory.value === "PUBLIC" ? publicQuery : mineQuery
  const operations = useMemo(
    () => new Set(metaQuery.data?.operations ?? []),
    [metaQuery.data?.operations]
  )
  const canCreate = directory.value === "MINE" && operations.has("create")
  const canUpdate = operations.has("update")
  const skills = activeQuery.data?.list ?? []

  function handleSelect(skill: AiSkillVO) {
    setSelectedSkillId(selectedSkillId === skill.id ? null : skill.id)
    onClose?.()
  }

  function openCreate() {
    if (!canCreate) return
    setEditTarget(null)
    editorDialog.onTrue()
  }

  function openEdit(skill: AiSkillVO) {
    if (!canUpdate || !skill.ownedByCurrentUser) return
    setEditTarget(skill)
    editorDialog.onTrue()
  }

  function handleEditorOpenChange(open: boolean) {
    editorDialog.setValue(open)
    if (!open) setEditTarget(null)
  }

  function handleSaved(skill: AiSkillVO) {
    setSelectedSkillId(skill.id)
    onClose?.()
  }

  return (
    <div className="flex flex-col">
      <div className="flex items-center justify-between border-foreground/6 border-b px-4 py-3">
        <span className="font-semibold text-sm">选择技能</span>
        <div className="flex items-center gap-2">
          {canCreate ? (
            <Button variant="ghost" size="xs" onClick={openCreate}>
              <Plus data-icon="inline-start" />
              新建
            </Button>
          ) : null}
          {selectedSkillId !== null ? (
            <Button
              variant="ghost"
              size="xs"
              onClick={() => {
                setSelectedSkillId(null)
                onClose?.()
              }}
            >
              清除
            </Button>
          ) : null}
        </div>
      </div>

      <Tabs value={directory.value} onValueChange={directory.onChange} className="px-3 pt-2">
        <TabsList className="w-full">
          <TabsTrigger value="MINE">我的</TabsTrigger>
          <TabsTrigger value="PUBLIC">公共</TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="px-3 py-2">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="搜索技能..."
            aria-label="搜索技能"
            className="h-8 pl-8 text-xs"
          />
        </div>
      </div>

      <ScrollArea className="h-[300px]">
        <div className="flex flex-col gap-1.5 px-3 pb-3">
          {activeQuery.isLoading ? (
            ["one", "two", "three"].map((key) => (
              <Skeleton key={key} className="h-16 w-full rounded-xl" />
            ))
          ) : skills.length === 0 ? (
            <Empty className="min-h-56">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Zap />
                </EmptyMedia>
                <EmptyTitle>
                  {debouncedSearch
                    ? "没有匹配的技能"
                    : directory.value === "MINE"
                      ? "还没有我的技能"
                      : "暂无公共技能"}
                </EmptyTitle>
                <EmptyDescription>
                  {directory.value === "MINE"
                    ? "创建技能后可立即选择使用"
                    : "平台内置和工作区公开技能"}
                </EmptyDescription>
              </EmptyHeader>
              {canCreate && !debouncedSearch ? (
                <EmptyContent>
                  <Button size="sm" onClick={openCreate}>
                    <Plus data-icon="inline-start" />
                    新建技能
                  </Button>
                </EmptyContent>
              ) : null}
            </Empty>
          ) : (
            skills.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                selected={selectedSkillId === skill.id}
                showSource={directory.value === "PUBLIC"}
                onSelect={() => handleSelect(skill)}
                onEdit={
                  directory.value === "MINE" && canUpdate && skill.ownedByCurrentUser
                    ? () => openEdit(skill)
                    : undefined
                }
              />
            ))
          )}
        </div>
      </ScrollArea>

      <SkillEditorDialog
        open={editorDialog.value}
        onOpenChange={handleEditorOpenChange}
        initial={editTarget}
        onSaved={handleSaved}
      />
    </div>
  )
}
