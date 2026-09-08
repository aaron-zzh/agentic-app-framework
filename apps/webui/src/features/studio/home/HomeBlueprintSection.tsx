/**
 * Studio 首页蓝图区：按项目类型展示已发布蓝图，点击后跳转统一新建项目页。
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowRight, LayoutTemplate, RefreshCw } from "lucide-react"
import { useRouter } from "next/navigation"
import { useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getProjectTypeConfig } from "@/features/studio/content/project-type-config"
import {
  type AigcProductionMode,
  type AigcProjectBlueprint,
  type AigcProjectType,
  useAigcProjectBlueprints,
  useAigcProjectTypes
} from "@/lib/api/rest/ai/aigc"
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

function normalizedCoverUrl(value: string | null | undefined): string | null {
  const url = value?.trim()
  if (!url || !/^(https?:\/\/|\/)/i.test(url)) return null
  return url
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
      className="group relative aspect-video w-full overflow-hidden rounded-2xl bg-background text-left text-white outline-none ring-1 ring-foreground/10 transition-shadow hover:shadow-xl focus-visible:ring-3 focus-visible:ring-primary"
    >
      <span aria-hidden="true" className={cn("absolute inset-0 bg-gradient-to-br", gradient)} />
      {coverUrl ? <BlueprintCoverImage key={`${blueprint.id}:${coverUrl}`} src={coverUrl} /> : null}
      <span
        aria-hidden="true"
        className="absolute inset-0 bg-gradient-to-b from-black/20 via-black/15 to-black/85"
      />
      <span className="absolute inset-x-0 top-0 flex items-start justify-between p-4">
        <span className="rounded-full bg-black/45 px-2.5 py-1 font-medium text-xs backdrop-blur-sm">
          {display.label}
        </span>
        <span className="flex size-7 items-center justify-center rounded-full bg-black/45 opacity-0 backdrop-blur-sm transition-all group-hover:translate-x-0.5 group-hover:opacity-100">
          <ArrowRight className="size-3.5" />
        </span>
      </span>
      <span className="absolute right-4 bottom-4 text-white/70 text-xs">
        {PRODUCTION_MODE_LABELS[blueprint.productionMode]}
      </span>
      <span className="absolute inset-x-0 bottom-0 flex flex-col gap-2 p-4">
        <span className="line-clamp-2 font-semibold text-base leading-tight">{blueprint.name}</span>
      </span>
    </button>
  )
}

export function HomeBlueprintSection() {
  const router = useRouter()
  const {
    data: typePage,
    isLoading: typesLoading,
    isError: typesError,
    refetch: refetchTypes
  } = useAigcProjectTypes()
  const [activeType, setActiveType] = useState("")

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

  function handleSelect(blueprint: AigcProjectBlueprint) {
    router.push(`/studio/projects/new?blueprintId=${blueprint.id}`)
  }

  return (
    <section aria-labelledby="studio-blueprints-title" className="flex flex-col gap-4">
      <h2 id="studio-blueprints-title" className="font-semibold text-base">
        项目模板
      </h2>

      {typesLoading ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-8 w-80 max-w-full" />
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }, (_, index) => (
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
            <EmptyDescription>项目模板区暂时不可用，其他首页功能不受影响。</EmptyDescription>
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
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                {Array.from({ length: 4 }, (_, index) => (
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
                  <EmptyTitle>项目模板加载失败</EmptyTitle>
                  <EmptyDescription>请重试当前项目类型的项目模板查询。</EmptyDescription>
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
                  <EmptyTitle>此类型暂无可用项目模板</EmptyTitle>
                  <EmptyDescription>项目模板发布后会显示在当前类型中。</EmptyDescription>
                </EmptyHeader>
              </Empty>
            ) : selectedType ? (
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                {blueprints.map((blueprint) => (
                  <BlueprintCard
                    key={blueprint.id}
                    blueprint={blueprint}
                    projectType={selectedType}
                    onSelect={handleSelect}
                  />
                ))}
              </div>
            ) : null}
          </TabsContent>
        </Tabs>
      )}
    </section>
  )
}
