/**
 * Studio 首页蓝图区：按项目类型展示已发布蓝图并完成快捷建项。
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowRight, ChevronDown, LayoutTemplate, RefreshCw, Sparkles } from "lucide-react"
import { useRouter } from "next/navigation"
import { useId, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { BrandProfileSelect, ChannelPicker } from "@/features/studio/content/NewProjectLauncher"
import { ProjectDocumentPicker } from "@/features/studio/content/ProjectDocumentPicker"
import { getProjectTypeConfig } from "@/features/studio/content/project-type-config"
import {
  type AigcProductionMode,
  type AigcProjectBlueprint,
  type AigcProjectType,
  useAigcBrandProfiles,
  useAigcChannelSpecs,
  useAigcProjectBlueprints,
  useAigcProjectTypes,
  useMaterializeAigcProject
} from "@/lib/api/rest/ai/aigc"
import { useDocList } from "@/lib/api/rest/system/document"
import { cn } from "@/lib/utils"

const PRODUCTION_MODE_LABELS: Record<AigcProductionMode, string> = {
  standard: "标准内容",
  short_drama: "短剧",
  motion_comic: "漫剧"
}

const TYPE_GRADIENTS = {
  neutral: "from-muted-foreground/70 via-muted-foreground/25 to-background",
  violet: "from-primary/90 via-primary/45 to-background",
  cyan: "from-chart-2/90 via-chart-2/40 to-background",
  emerald: "from-chart-3/90 via-chart-3/40 to-background",
  amber: "from-chart-4/90 via-chart-4/40 to-background",
  rose: "from-destructive/85 via-destructive/40 to-background"
} as const

function defaultProjectName(blueprintName: string): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, "0")
  const day = String(now.getDate()).padStart(2, "0")
  return `${blueprintName} · ${month}-${day}`
}

function normalizedCoverUrl(value: string | null | undefined): string | null {
  const url = value?.trim()
  if (!url || !/^(https?:\/\/|\/)/i.test(url)) return null
  return url
}

function materializeErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : "项目创建失败，请稍后重试。"
  if (/蓝图|blueprint/i.test(message)) {
    return "此蓝图已不可用，请关闭后刷新并重新选择。"
  }
  return message
}

function BlueprintCoverImage({ src }: { src: string }) {
  const [failed, setFailed] = useState(false)
  if (failed) return null

  return (
    // biome-ignore lint/performance/noImgElement: 蓝图封面允许远程地址，并需在加载失败时移除节点回退渐变
    <img
      src={src}
      alt=""
      aria-hidden="true"
      onError={() => setFailed(true)}
      className="absolute inset-0 size-full object-cover"
    />
  )
}

function BlueprintCard({
  blueprint,
  projectType,
  onSelect
}: {
  blueprint: AigcProjectBlueprint
  projectType: AigcProjectType
  onSelect: (blueprint: AigcProjectBlueprint) => void
}) {
  const display = getProjectTypeConfig(projectType)
  const coverUrl = normalizedCoverUrl(blueprint.coverUrl)
  const gradient = TYPE_GRADIENTS[display.tone]

  return (
    <button
      type="button"
      onClick={() => onSelect(blueprint)}
      aria-label={`${blueprint.name}，${display.label}，${PRODUCTION_MODE_LABELS[blueprint.productionMode]}，创建项目`}
      className="group relative aspect-video min-h-44 w-full overflow-hidden rounded-2xl bg-background text-left text-white outline-none ring-1 ring-foreground/10 transition-shadow hover:shadow-xl focus-visible:ring-3 focus-visible:ring-primary"
    >
      <span aria-hidden="true" className={cn("absolute inset-0 bg-gradient-to-br", gradient)} />
      {coverUrl ? <BlueprintCoverImage key={`${blueprint.id}:${coverUrl}`} src={coverUrl} /> : null}
      <span
        aria-hidden="true"
        className="absolute inset-0 bg-gradient-to-b from-black/20 via-black/15 to-black/85"
      />
      <span className="absolute inset-x-0 top-0 flex items-start justify-between gap-3 p-4">
        <span className="rounded-full bg-black/45 px-2.5 py-1 font-medium text-xs backdrop-blur-sm">
          {display.label}
        </span>
        <span className="rounded-full bg-black/45 px-2.5 py-1 text-xs backdrop-blur-sm">
          {PRODUCTION_MODE_LABELS[blueprint.productionMode]}
        </span>
      </span>
      <span className="absolute inset-x-0 bottom-0 flex flex-col gap-2 p-4">
        <span className="line-clamp-2 font-semibold text-lg leading-tight">{blueprint.name}</span>
        <span className="flex items-center justify-between gap-3 text-sm text-white/80">
          使用此蓝图创建项目
          <ArrowRight className="transition-transform group-hover:translate-x-1" />
        </span>
      </span>
    </button>
  )
}

function CreateProjectDialog({
  blueprint,
  projectType,
  onOpenChange
}: {
  blueprint: AigcProjectBlueprint
  projectType: AigcProjectType
  onOpenChange: (open: boolean) => void
}) {
  const router = useRouter()
  const nameId = useId()
  const nameErrorId = `${nameId}-error`
  const briefId = useId()
  const submitLock = useRef(false)
  const {
    data: profilePage,
    isLoading: profilesLoading,
    isError: profilesError
  } = useAigcBrandProfiles()
  const {
    data: channelPage,
    isLoading: channelsLoading,
    isError: channelsError
  } = useAigcChannelSpecs({ status: "published" })
  const { data: documents = [], isLoading: documentsLoading } = useDocList()
  const materialize = useMaterializeAigcProject()
  const [name, setName] = useState(() => defaultProjectName(blueprint.name))
  const [nameError, setNameError] = useState<string | null>(null)
  const [brandProfileId, setBrandProfileId] = useState<number>()
  const [documentVersionIds, setDocumentVersionIds] = useState<number[]>([])
  const [channels, setChannels] = useState(() => [...projectType.defaultChannels])
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const [brief, setBrief] = useState("")
  const [submitError, setSubmitError] = useState<string | null>(null)

  const profiles = (profilePage?.list ?? []).filter(
    (profile) => profile.currentVersionId !== undefined
  )
  const channelSpecs = (channelPage?.list ?? []).filter((channel) => channel.status === "published")
  const availableChannels = channelSpecs.map((channel) => channel.code)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitLock.current || materialize.isPending) return

    const trimmedName = name.trim()
    if (!trimmedName) {
      setNameError("项目名称不能为空")
      return
    }

    setNameError(null)
    setSubmitError(null)
    submitLock.current = true
    try {
      const selectedProfile = profiles.find((profile) => profile.id === brandProfileId)
      const project = await materialize.mutateAsync({
        name: trimmedName,
        projectTypeCode: blueprint.projectTypeCode,
        blueprintVersionId: blueprint.id,
        productionMode: blueprint.productionMode,
        brandProfileVersionIds: selectedProfile?.currentVersionId
          ? [selectedProfile.currentVersionId]
          : [],
        channelSpecVersionIds: channelSpecs
          .filter((channel) => channels.includes(channel.code))
          .map((channel) => channel.id),
        documentVersionIds,
        briefJson: brief.trim() || undefined
      })
      onOpenChange(false)
      router.push(`/studio/projects/${project.id}`)
    } catch (error) {
      setSubmitError(materializeErrorMessage(error))
    } finally {
      submitLock.current = false
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !materialize.isPending) onOpenChange(false)
      }}
    >
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col gap-0 overflow-hidden p-0 max-sm:h-dvh max-sm:max-h-dvh max-sm:max-w-none max-sm:rounded-none sm:max-w-2xl">
        <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="shrink-0 border-b px-5 py-4 pr-12">
            <DialogTitle>从蓝图创建项目</DialogTitle>
            <DialogDescription>
              使用所选蓝图初始化项目，创建后直接进入项目工作台。
            </DialogDescription>
          </DialogHeader>

          <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto p-5">
            <dl className="grid gap-3 rounded-xl bg-muted/60 p-4 sm:grid-cols-3">
              <div className="flex flex-col gap-1">
                <dt className="text-muted-foreground text-xs">项目类型</dt>
                <dd className="font-medium text-sm">{getProjectTypeConfig(projectType).label}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-muted-foreground text-xs">蓝图</dt>
                <dd className="font-medium text-sm">{blueprint.name}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-muted-foreground text-xs">生产模式</dt>
                <dd className="font-medium text-sm">
                  {PRODUCTION_MODE_LABELS[blueprint.productionMode]}
                </dd>
              </div>
            </dl>

            <div className="flex flex-col gap-2">
              <label htmlFor={nameId} className="font-medium text-sm">
                项目名称 <span className="text-destructive">*</span>
              </label>
              <Input
                id={nameId}
                autoFocus
                required
                aria-invalid={nameError !== null}
                aria-describedby={nameError ? nameErrorId : undefined}
                value={name}
                onChange={(event) => {
                  setName(event.target.value)
                  if (nameError) setNameError(null)
                }}
              />
              {nameError ? (
                <p id={nameErrorId} className="text-destructive text-sm">
                  {nameError}
                </p>
              ) : null}
            </div>

            <div className="flex flex-col gap-2">
              <span className="font-medium text-sm">IP（可选）</span>
              <BrandProfileSelect
                profiles={profiles}
                value={brandProfileId}
                onChange={setBrandProfileId}
                disabled={profilesLoading || profilesError}
              />
              {!profilesLoading && profiles.length === 0 ? (
                <p className="text-muted-foreground text-xs">暂无可用 IP，不影响创建。</p>
              ) : null}
              {profilesError ? (
                <p className="text-destructive text-xs">IP 加载失败，可不选择并继续创建。</p>
              ) : null}
            </div>

            <div className="flex flex-col gap-2">
              <span className="font-medium text-sm">渠道（可选）</span>
              <ChannelPicker channels={availableChannels} value={channels} onChange={setChannels} />
              {!channelsLoading && availableChannels.length === 0 ? (
                <p className="text-muted-foreground text-xs">暂无可用渠道，不影响创建。</p>
              ) : null}
              {channelsError ? (
                <p className="text-destructive text-xs">渠道加载失败，可不选择并继续创建。</p>
              ) : null}
            </div>

            <div className="flex flex-col gap-2">
              <span className="font-medium text-sm">项目文档（可选）</span>
              <ProjectDocumentPicker
                values={documentVersionIds}
                options={documents}
                loading={documentsLoading}
                disabled={materialize.isPending}
                onValueChange={setDocumentVersionIds}
              />
            </div>

            <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
              <CollapsibleTrigger
                render={<Button type="button" variant="ghost" className="w-full justify-between" />}
              >
                高级设置
                <ChevronDown className={cn("transition-transform", advancedOpen && "rotate-180")} />
              </CollapsibleTrigger>
              <CollapsibleContent className="pt-3">
                <div className="flex flex-col gap-2">
                  <label htmlFor={briefId} className="font-medium text-sm">
                    补充说明（可选）
                  </label>
                  <Textarea
                    id={briefId}
                    value={brief}
                    onChange={(event) => setBrief(event.target.value)}
                    placeholder="补充目标、受众、风格或交付要求"
                    className="min-h-28 resize-y"
                  />
                </div>
              </CollapsibleContent>
            </Collapsible>

            {submitError ? (
              <div
                role="alert"
                className="rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-destructive text-sm"
              >
                {submitError}
              </div>
            ) : null}
          </div>

          <DialogFooter className="mx-0 mb-0 shrink-0 rounded-none px-5 py-4">
            <Button
              type="button"
              variant="outline"
              disabled={materialize.isPending}
              onClick={() => onOpenChange(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={materialize.isPending}>
              <Sparkles data-icon="inline-start" />
              {materialize.isPending ? "正在创建" : "创建项目"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export function HomeBlueprintSection() {
  const {
    data: typePage,
    isLoading: typesLoading,
    isError: typesError,
    refetch: refetchTypes
  } = useAigcProjectTypes()
  const [activeType, setActiveType] = useState("")
  const [selectedBlueprint, setSelectedBlueprint] = useState<AigcProjectBlueprint | null>(null)

  const types = useMemo(
    () =>
      (typePage?.list ?? [])
        .filter((type) => type.status === "published")
        .toSorted((left, right) => left.sortOrder - right.sortOrder),
    [typePage?.list]
  )
  const resolvedActiveType = types.some((type) => type.code === activeType)
    ? activeType
    : (types[0]?.code ?? "")
  const selectedType = types.find((type) => type.code === resolvedActiveType)
  const {
    data: blueprintPage,
    isLoading: blueprintsLoading,
    isError: blueprintsError,
    refetch: refetchBlueprints
  } = useAigcProjectBlueprints(
    { projectTypeCode: resolvedActiveType || undefined, status: "published" },
    resolvedActiveType.length > 0
  )
  const blueprints = (blueprintPage?.list ?? []).filter(
    (blueprint) =>
      blueprint.status === "published" && blueprint.projectTypeCode === resolvedActiveType
  )

  return (
    <section aria-labelledby="studio-blueprints-title" className="flex flex-col gap-4">
      <h2 id="studio-blueprints-title" className="font-semibold text-base">
        蓝图
      </h2>

      {typesLoading ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-8 w-80 max-w-full" />
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={`blueprint-${index}`} className="aspect-video rounded-2xl" />
            ))}
          </div>
        </div>
      ) : typesError ? (
        <Empty className="min-h-44 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <LayoutTemplate />
            </EmptyMedia>
            <EmptyTitle>项目类型加载失败</EmptyTitle>
            <EmptyDescription>蓝图区暂时不可用，其他首页功能不受影响。</EmptyDescription>
          </EmptyHeader>
          <Button type="button" variant="outline" onClick={() => void refetchTypes()}>
            <RefreshCw data-icon="inline-start" />
            重试
          </Button>
        </Empty>
      ) : types.length === 0 ? (
        <Empty className="min-h-44 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <LayoutTemplate />
            </EmptyMedia>
            <EmptyTitle>暂无可用项目类型</EmptyTitle>
            <EmptyDescription>项目类型发布后会显示在这里。</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <Tabs value={resolvedActiveType} onValueChange={setActiveType}>
          <div className="overflow-x-auto pb-1">
            <TabsList variant="line" className="min-w-max justify-start">
              {types.map((type) => (
                <TabsTrigger key={type.code} value={type.code} className="px-3">
                  {getProjectTypeConfig(type).label}
                </TabsTrigger>
              ))}
            </TabsList>
          </div>
          <TabsContent value={resolvedActiveType}>
            {blueprintsLoading ? (
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: 3 }, (_, index) => (
                  <Skeleton
                    key={`active-blueprint-${index}`}
                    className="aspect-video rounded-2xl"
                  />
                ))}
              </div>
            ) : blueprintsError ? (
              <Empty className="min-h-44 border">
                <EmptyHeader>
                  <EmptyMedia variant="icon">
                    <LayoutTemplate />
                  </EmptyMedia>
                  <EmptyTitle>蓝图加载失败</EmptyTitle>
                  <EmptyDescription>请重试当前项目类型的蓝图查询。</EmptyDescription>
                </EmptyHeader>
                <Button type="button" variant="outline" onClick={() => void refetchBlueprints()}>
                  <RefreshCw data-icon="inline-start" />
                  重试
                </Button>
              </Empty>
            ) : blueprints.length === 0 ? (
              <Empty className="min-h-44 border">
                <EmptyHeader>
                  <EmptyMedia variant="icon">
                    <LayoutTemplate />
                  </EmptyMedia>
                  <EmptyTitle>此类型暂无可用蓝图</EmptyTitle>
                  <EmptyDescription>蓝图发布后会显示在当前类型中。</EmptyDescription>
                </EmptyHeader>
              </Empty>
            ) : selectedType ? (
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {blueprints.map((blueprint) => (
                  <BlueprintCard
                    key={blueprint.id}
                    blueprint={blueprint}
                    projectType={selectedType}
                    onSelect={setSelectedBlueprint}
                  />
                ))}
              </div>
            ) : null}
          </TabsContent>
        </Tabs>
      )}

      {selectedBlueprint && selectedType ? (
        <CreateProjectDialog
          blueprint={selectedBlueprint}
          projectType={selectedType}
          onOpenChange={(open) => {
            if (!open) setSelectedBlueprint(null)
          }}
        />
      ) : null}
    </section>
  )
}
