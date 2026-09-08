/**
 * Content Studio 项目结构视图。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  CircleDashed,
  Eye,
  FilePenLine,
  Plus,
  Sparkles,
  Trash2
} from "lucide-react"
import { GlassCard, GlassCardBody, NeonChip } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { AigcObjectType, AigcProjectGraph, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import {
  getObjectStage,
  OBJECT_STATUS_LABELS,
  OBJECT_TYPE_LABELS,
  PROJECT_GRAPH_STAGES,
  type ProjectGraphStage
} from "./graph/graph-projection"
import { getObjectTypeConfig } from "./project-type-config"

const OBJECT_STATUS_TONE = {
  empty: "neutral",
  draft: "cyan",
  pending_confirm: "amber",
  adopted: "violet",
  blocked: "rose",
  done: "emerald"
} as const

function ObjectTypeIcon({ type }: { type: AigcObjectType }) {
  const Icon = getObjectTypeConfig(type).icon
  return <Icon />
}

export interface ProjectStructureViewProps {
  graph: AigcProjectGraph
  activeStage: ProjectGraphStage
  focusObjectId?: number
  readOnly?: boolean
  generatableObjectIds?: number[]
  onStageChange: (stage: ProjectGraphStage) => void
  onAppendObject: () => void
  onUpdateContract: (object: AigcProjectObject) => void
  onRemoveObject: (object: AigcProjectObject) => void
  onOpenDetails: (id: number) => void
  onGenerateObject: (id: number) => void
  onOpenCanvas: (id: number) => void
  onAnnotateImage: (id: number) => void
}

export function ProjectStructureView({
  graph,
  activeStage,
  focusObjectId,
  readOnly = false,
  generatableObjectIds = [],
  onStageChange,
  onAppendObject,
  onUpdateContract,
  onRemoveObject,
  onOpenDetails,
  onGenerateObject,
  onOpenCanvas,
  onAnnotateImage
}: ProjectStructureViewProps) {
  const generatableObjectIdSet = new Set(generatableObjectIds)
  const stageObjects = graph.objects
    .filter((object) => getObjectStage(object) === activeStage)
    .toSorted((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)
  const groups = new Map<AigcObjectType, typeof stageObjects>()
  for (const object of stageObjects) {
    const current = groups.get(object.objectType) ?? []
    groups.set(object.objectType, [...current, object])
  }
  const pendingCount = stageObjects.filter((object) => object.status === "pending_confirm").length
  const gapCount = stageObjects.filter((object) => object.status === "empty").length
  const blockedCount = stageObjects.filter((object) => object.status === "blocked").length

  return (
    <div className="grid h-full min-h-[520px] gap-4 lg:grid-cols-[220px_minmax(0,1fr)]">
      <GlassCard glow="none" className="h-fit">
        <GlassCardBody className="flex flex-col gap-3 p-3">
          <p className="px-2 font-medium text-sm">项目阶段</p>
          <ToggleGroup
            value={[activeStage]}
            onValueChange={(values: string[]) => {
              const next = values.at(-1)
              if (next) onStageChange(next as ProjectGraphStage)
            }}
            orientation="vertical"
            variant="outline"
            className="w-full items-stretch"
          >
            {PROJECT_GRAPH_STAGES.map((stage) => {
              const objects = graph.objects.filter((object) => getObjectStage(object) === stage.key)
              const blocked = objects.some((object) => object.status === "blocked")
              const pending = objects.some((object) => object.status === "pending_confirm")
              return (
                <ToggleGroupItem
                  key={stage.key}
                  value={stage.key}
                  aria-label={stage.label}
                  className="h-auto w-full justify-between px-3 py-2 text-left"
                >
                  <span>{stage.label}</span>
                  <span className="flex items-center gap-1 text-muted-foreground text-xs">
                    {blocked ? (
                      <AlertTriangle className="text-destructive" />
                    ) : pending ? (
                      <CircleDashed />
                    ) : (
                      <CheckCircle2 />
                    )}
                    {objects.length}
                  </span>
                </ToggleGroupItem>
              )
            })}
          </ToggleGroup>
        </GlassCardBody>
      </GlassCard>

      <div className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-semibold text-lg">
              {PROJECT_GRAPH_STAGES.find((stage) => stage.key === activeStage)?.label}
            </h2>
            <p className="text-muted-foreground text-sm">
              {PROJECT_GRAPH_STAGES.find((stage) => stage.key === activeStage)?.description}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <NeonChip tone="neutral">{stageObjects.length} 对象</NeonChip>
            <NeonChip tone="amber">{pendingCount} 待确认</NeonChip>
            <NeonChip tone="rose">{blockedCount} 阻断</NeonChip>
            <NeonChip tone="cyan">{gapCount} 缺口</NeonChip>
            {!readOnly ? (
              <Button type="button" size="sm" onClick={onAppendObject}>
                <Plus />
                追加对象
              </Button>
            ) : null}
          </div>
        </div>

        {stageObjects.length === 0 ? (
          <GlassCard glow="none" className="flex-1">
            <Empty className="min-h-72">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <CircleDashed />
                </EmptyMedia>
                <EmptyTitle>该阶段暂无对象</EmptyTitle>
                <EmptyDescription>项目模板或 Assistant 后续会在这里补充对象。</EmptyDescription>
              </EmptyHeader>
            </Empty>
          </GlassCard>
        ) : (
          Array.from(groups.entries()).map(([type, objects]) => (
            <GlassCard
              key={type}
              glow={objects.some((object) => object.status === "blocked") ? "rose" : "none"}
            >
              <div className="flex items-center justify-between border-b px-4 py-3">
                <div>
                  <h3 className="flex items-center gap-2 font-medium text-sm">
                    <ObjectTypeIcon type={type} />
                    {OBJECT_TYPE_LABELS[type]}
                  </h3>
                  <p className="text-muted-foreground text-xs">{objects.length} 项</p>
                </div>
                <NeonChip tone="neutral" size="sm">
                  {type}
                </NeonChip>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>对象</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead className="hidden md:table-cell">摘要</TableHead>
                    <TableHead className="w-64 text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {objects.map((object) => (
                    <TableRow
                      key={object.id}
                      data-state={focusObjectId === object.id ? "selected" : undefined}
                    >
                      <TableCell>
                        <p className="font-medium text-sm">
                          {object.title || OBJECT_TYPE_LABELS[object.objectType]}
                        </p>
                        <p className="text-muted-foreground text-xs">#{object.stableKey}</p>
                      </TableCell>
                      <TableCell>
                        <NeonChip tone={OBJECT_STATUS_TONE[object.status]} size="sm">
                          {OBJECT_STATUS_LABELS[object.status]}
                        </NeonChip>
                      </TableCell>
                      <TableCell className="hidden max-w-sm truncate text-muted-foreground text-sm md:table-cell">
                        {object.summary || "暂无摘要"}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          {!readOnly ? (
                            <>
                              <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => onUpdateContract(object)}
                              >
                                <FilePenLine />
                                合同
                              </Button>
                              <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => onRemoveObject(object)}
                              >
                                <Trash2 />
                                移除
                              </Button>
                            </>
                          ) : null}
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => onOpenDetails(object.id)}
                          >
                            <Eye />
                            详情
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={readOnly || !generatableObjectIdSet.has(object.id)}
                            onClick={() => onGenerateObject(object.id)}
                          >
                            <Sparkles />
                            生成
                          </Button>
                          {object.objectType === "canvas_board" ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              disabled={readOnly}
                              onClick={() => onOpenCanvas(object.id)}
                            >
                              打开画布
                              <ArrowRight />
                            </Button>
                          ) : object.objectType === "image_deliverable" ||
                            object.objectType === "shot_keyframe" ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              disabled={readOnly}
                              onClick={() => onAnnotateImage(object.id)}
                            >
                              标注
                              <ArrowRight />
                            </Button>
                          ) : null}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </GlassCard>
          ))
        )}
      </div>
    </div>
  )
}
