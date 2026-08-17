/**
 * 技能资产的“我的/公共”根对象与不可变版本目录视图。
 *
 * 列表展示稳定根元数据及当前可执行版本状态，编辑时由 SkillEditorDialog 追加新版本。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean, useTabs } from "@aaf/hooks"
import { ChevronLeft, ChevronRight, Pencil, Plus, Search, Sparkles, Trash2 } from "lucide-react"
import { useMemo, useState } from "react"
import { useDebounce } from "use-debounce"

import { GlassCard, GlowButton } from "@/components/studio"
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
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { SkillEditorDialog } from "@/features/aigc/skills/SkillEditorDialog"
import {
  type AiSkillVersionStatus,
  type AiSkillVisibility,
  type AiSkillVO,
  useAiSkillMeta,
  useDeleteAiSkill,
  useMyAiSkills,
  usePublicAiSkills
} from "@/lib/api/rest/ai"

type SkillAssetView = "MINE" | "PUBLIC"

const VIEW_COPY: Record<SkillAssetView, { title: string; description: string; empty: string }> = {
  MINE: {
    title: "我的技能",
    description: "管理你在当前组织或工作区创建的技能根对象与版本",
    empty: "还没有技能资产"
  },
  PUBLIC: {
    title: "公共技能",
    description: "浏览平台内置及当前组织或工作区共享的已发布技能",
    empty: "暂无公共技能"
  }
}

const VISIBILITY_LABELS: Record<AiSkillVisibility, string> = {
  PRIVATE: "仅自己",
  WORKSPACE: "工作区",
  PUBLIC: "公开"
}

const VERSION_STATUS_LABELS: Record<AiSkillVersionStatus, string> = {
  DRAFT: "草稿",
  IN_REVIEW: "审核中",
  APPROVED: "已通过",
  REJECTED: "已拒绝",
  RETIRED: "已退役"
}

function sourceLabel(skill: AiSkillVO): string {
  return skill.builtIn ? "内置" : "工作区"
}

export function SkillAssetsView() {
  const view = useTabs("MINE")
  const editDialog = useBoolean()
  const [editTarget, setEditTarget] = useState<AiSkillVO | null>(null)
  const [search, setSearch] = useState("")
  const [pageNo, setPageNo] = useState(1)
  const [debouncedSearch] = useDebounce(search.trim(), 300)
  const pageSize = 20
  const queryParams = {
    search: debouncedSearch || undefined,
    pageNo,
    pageSize
  }
  const mineQuery = useMyAiSkills(queryParams, view.value === "MINE")
  const publicQuery = usePublicAiSkills(queryParams, view.value === "PUBLIC")
  const metaQuery = useAiSkillMeta()
  const deleteSkill = useDeleteAiSkill()
  const activeQuery = view.value === "PUBLIC" ? publicQuery : mineQuery
  const skills = activeQuery.data?.list ?? []
  const operations = useMemo(
    () => new Set(metaQuery.data?.operations ?? []),
    [metaQuery.data?.operations]
  )
  const currentView = view.value as SkillAssetView
  const viewCopy = VIEW_COPY[currentView]
  const canCreate = currentView === "MINE" && operations.has("create")
  const isLoading = metaQuery.isLoading || activeQuery.isLoading
  const total = activeQuery.data?.total ?? 0
  const pageCount = Math.max(1, Math.ceil(total / pageSize))

  function handleViewChange(value: string) {
    if (value !== "MINE" && value !== "PUBLIC") return
    view.onChange(value)
    setPageNo(1)
  }

  function openCreate() {
    setEditTarget(null)
    editDialog.onTrue()
  }

  function openEdit(skill: AiSkillVO) {
    if (!skill.ownedByCurrentUser) return
    setEditTarget(skill)
    editDialog.onTrue()
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-4 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-semibold text-xl">技能资产</h1>
          <p className="mt-1 text-muted-foreground text-sm">
            管理稳定技能身份、可见范围与不可变执行版本
          </p>
        </div>
        {canCreate ? (
          <GlowButton tone="primary" size="sm" onClick={openCreate}>
            <Plus data-icon="inline-start" />
            新建
          </GlowButton>
        ) : null}
      </header>

      <Tabs value={view.value} onValueChange={handleViewChange}>
        <TabsList>
          <TabsTrigger value="MINE">我的</TabsTrigger>
          <TabsTrigger value="PUBLIC">公共</TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setPageNo(1)
          }}
          placeholder="搜索代码、名称、摘要或版本正文"
          aria-label="搜索技能资产"
          className="pl-9"
        />
      </div>

      <div>
        <h2 className="font-medium text-base">{viewCopy.title}</h2>
        <p className="mt-1 text-muted-foreground text-sm">{viewCopy.description}</p>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          {["one", "two", "three", "four", "five"].map((key) => (
            <Skeleton key={key} className="h-24 w-full rounded-xl" />
          ))}
        </div>
      ) : skills.length === 0 ? (
        <Empty className="min-h-64 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Sparkles />
            </EmptyMedia>
            <EmptyTitle>{debouncedSearch ? "没有匹配的技能" : viewCopy.empty}</EmptyTitle>
            <EmptyDescription>
              {debouncedSearch
                ? "尝试更换搜索词。"
                : currentView === "MINE"
                  ? "创建技能后，可配置仅自己、工作区或公开可见。"
                  : viewCopy.description}
            </EmptyDescription>
          </EmptyHeader>
          {!debouncedSearch && canCreate ? (
            <EmptyContent>
              <Button onClick={openCreate}>
                <Plus data-icon="inline-start" />
                新建技能
              </Button>
            </EmptyContent>
          ) : null}
        </Empty>
      ) : (
        <div className="flex flex-col gap-2">
          {skills.map((skill) => {
            const canUpdate =
              currentView === "MINE" && skill.ownedByCurrentUser && operations.has("update")
            const canDelete =
              currentView === "MINE" && skill.ownedByCurrentUser && operations.has("delete")
            const displayedVersion =
              currentView === "MINE" ? skill.latestVersion : skill.currentVersion
            const versionStatus = displayedVersion?.status
            return (
              <GlassCard key={skill.id} glow="none" className="border border-foreground/6">
                <div className="flex items-start gap-3 p-4">
                  <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary/10 font-semibold text-primary">
                    {skill.name.slice(0, 1)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-medium text-sm">{skill.name}</p>
                      {currentView === "PUBLIC" ? (
                        <Badge variant="secondary">{sourceLabel(skill)}</Badge>
                      ) : (
                        <Badge variant="secondary">{VISIBILITY_LABELS[skill.visibility]}</Badge>
                      )}
                      <Badge variant="outline">{skill.locale}</Badge>
                      <Badge variant="outline">
                        {versionStatus ? VERSION_STATUS_LABELS[versionStatus] : "无版本"}
                      </Badge>
                    </div>
                    <p className="mt-1 line-clamp-2 text-muted-foreground text-xs leading-5">
                      {skill.summary}
                    </p>
                    <p className="mt-1 truncate text-muted-foreground text-xs">{skill.code}</p>
                  </div>
                  {canUpdate || canDelete ? (
                    <div className="flex shrink-0 gap-1">
                      {canUpdate ? (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => openEdit(skill)}
                          aria-label={`编辑${skill.name}`}
                        >
                          <Pencil />
                        </Button>
                      ) : null}
                      {canDelete ? (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          className="text-destructive hover:text-destructive"
                          disabled={deleteSkill.isPending && deleteSkill.variables === skill.id}
                          onClick={() => deleteSkill.mutate(skill.id)}
                          aria-label={`删除${skill.name}`}
                        >
                          <Trash2 />
                        </Button>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </GlassCard>
            )
          })}
          <div className="flex items-center justify-between gap-3 py-2">
            <p className="text-muted-foreground text-xs">共 {total} 个技能</p>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="xs"
                disabled={pageNo <= 1 || activeQuery.isFetching}
                onClick={() => setPageNo((current) => Math.max(1, current - 1))}
              >
                <ChevronLeft data-icon="inline-start" />
                上一页
              </Button>
              <span className="text-muted-foreground text-xs">
                {pageNo} / {pageCount}
              </span>
              <Button
                variant="outline"
                size="xs"
                disabled={pageNo >= pageCount || activeQuery.isFetching}
                onClick={() => setPageNo((current) => Math.min(pageCount, current + 1))}
              >
                下一页
                <ChevronRight data-icon="inline-end" />
              </Button>
            </div>
          </div>
        </div>
      )}

      <SkillEditorDialog
        open={editDialog.value}
        onOpenChange={editDialog.setValue}
        initial={editTarget}
      />
    </div>
  )
}
