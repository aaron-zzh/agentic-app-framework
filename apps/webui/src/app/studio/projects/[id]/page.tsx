/**
 * Content Studio 项目结构 / 图谱双视图工作台。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { ChevronDown, ChevronUp, FolderKanban, Pencil } from "lucide-react"
import Link from "next/link"
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation"
import { useEffect, useMemo, useState } from "react"
import { GlowButton } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Skeleton } from "@/components/ui/skeleton"
import { useChatterLayoutPreference } from "@/features/chatter"
import {
  ContentActionBar,
  DeliveryWorkflowPanel,
  getObjectStage,
  isProjectContentWritable,
  ObjectDetailPanel,
  PROJECT_GRAPH_STAGES,
  ProjectAssistantControl,
  ProjectBasicInfoDialog,
  ProjectBriefPanel,
  ProjectDocumentPanel,
  type ProjectGraphStage,
  ProjectGraphView,
  ProjectLifecycleActions,
  ProjectObjectGenerationDialog,
  type ProjectObjectStructureAction,
  ProjectObjectStructureDialog,
  ProjectProgressPanel,
  ProjectScheduleSheet,
  ProjectStructureView,
  ProjectWorkbenchHeader,
  type ProjectWorkbenchView,
  useProjectGraphViewState
} from "@/features/studio/content"
import { GenerationProposalPanel } from "@/features/studio/content/GenerationProposalPanel"
import {
  useAigcProject,
  useAigcProjectActions,
  useAigcProjectConversationContext,
  useAigcProjectDocumentRefs,
  useAigcProjectGraph,
  useAigcProjectProgress,
  useAigcProjectReadiness
} from "@/lib/api/rest/ai/aigc"
import {
  AIGC_AUTHORITY_CODES,
  hasEntityCapability,
  hasEntityOperation,
  useEntityAccess
} from "@/lib/api/rest/user/permission"

function parseFocus(value: string | null): number | undefined {
  if (!value) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

function isStage(value: string | null): value is ProjectGraphStage {
  return PROJECT_GRAPH_STAGES.some((stage) => stage.key === value)
}

export default function StudioProjectDetailPage() {
  const params = useParams<{ id: string }>()
  const pathname = usePathname()
  const router = useRouter()
  const searchParams = useSearchParams()
  const projectId = Number(params.id)
  const validProjectId = Number.isFinite(projectId) && projectId > 0 ? projectId : null
  const { data: conversationContext } = useAigcProjectConversationContext(validProjectId)
  useChatterLayoutPreference(conversationContext?.state === "READY" ? "panel" : null)
  const storedView = useProjectGraphViewState(validProjectId ?? 0, (state) => state.view)
  const storedFocus = useProjectGraphViewState(validProjectId ?? 0, (state) => state.focusObjectId)
  const setStoredView = useProjectGraphViewState(validProjectId ?? 0, (state) => state.setView)
  const setStoredFocus = useProjectGraphViewState(
    validProjectId ?? 0,
    (state) => state.setFocusObjectId
  )
  const urlView = searchParams.get("view")
  const view: ProjectWorkbenchView =
    urlView === "graph" || urlView === "structure" ? urlView : storedView
  const urlFocus = parseFocus(searchParams.get("focus"))
  const focusObjectId = urlFocus ?? storedFocus ?? undefined
  const requestedStage = searchParams.get("stage")
  const [generationObjectId, setGenerationObjectId] = useState<number | null>(null)
  const [structureAction, setStructureAction] = useState<ProjectObjectStructureAction | null>(null)
  const detailPanel = useBoolean(false)
  const documentPanel = useBoolean(false)
  const basicInfoDialog = useBoolean(false)
  const proposalSheet = useBoolean(false)
  const briefPanel = useBoolean(false)
  const evidencePanel = useBoolean(false)
  const scheduleSheet = useBoolean(false)
  const { data: projectAccess } = useEntityAccess("project")
  const { data: workAccess } = useEntityAccess("work")
  const { data: timelineAccess } = useEntityAccess("timeline")
  const canProjectRead = hasEntityOperation(projectAccess, "read")
  const canProjectUpdate = hasEntityOperation(projectAccess, "update")
  const canAction = hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.PROJECT_ACTION)
  const canAdopt = hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.OBJECT_VERSION_ADOPT)
  const canReview = hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.PROJECT_REVIEW)
  const canLifecycle = hasEntityCapability(projectAccess, AIGC_AUTHORITY_CODES.PROJECT_LIFECYCLE)
  const canCollect = hasEntityCapability(workAccess, AIGC_AUTHORITY_CODES.WORK_COLLECT)
  const canTimelineRead = hasEntityCapability(timelineAccess, AIGC_AUTHORITY_CODES.TIMELINE_READ)
  const canTimelineCreate = hasEntityCapability(
    timelineAccess,
    AIGC_AUTHORITY_CODES.TIMELINE_CREATE
  )
  const canTimelineUpdate = hasEntityCapability(
    timelineAccess,
    AIGC_AUTHORITY_CODES.TIMELINE_UPDATE
  )
  const canTimelineDelete = hasEntityCapability(
    timelineAccess,
    AIGC_AUTHORITY_CODES.TIMELINE_DELETE
  )
  const { data: project, isLoading: projectLoading } = useAigcProject(validProjectId)
  const lifecycleWritable = project !== undefined && isProjectContentWritable(project.status)
  const canEditProject = canProjectUpdate && lifecycleWritable
  const canRunAction = canAction && lifecycleWritable
  const contentActionDisabledReason = !canAction
    ? "无项目动作权限"
    : !lifecycleWritable
      ? `${project?.status ?? "当前"} 生命周期阶段只读`
      : undefined
  const proposalDisabledReason = !canProjectUpdate
    ? "无项目更新权限"
    : !lifecycleWritable
      ? `${project?.status ?? "当前"} 生命周期阶段只读`
      : undefined
  const writableProjectId = canRunAction ? validProjectId : null
  const { data: actions = [] } = useAigcProjectActions(writableProjectId)
  const { data: documentRefs = [] } = useAigcProjectDocumentRefs(validProjectId)
  const { data: graph, isLoading: graphLoading } = useAigcProjectGraph(validProjectId)
  const optionalObjectIds = useMemo(
    () =>
      graph?.objects
        .filter((object) => object.contractRole === "OPTIONAL")
        .map((object) => object.id) ?? [],
    [graph]
  )
  const { data: readiness = [] } = useAigcProjectReadiness(validProjectId, optionalObjectIds)
  const { data: progress } = useAigcProjectProgress(validProjectId)
  const readOnly = !canEditProject
  const focusedObject = graph?.objects.find((object) => object.id === focusObjectId)
  const activeStage: ProjectGraphStage = isStage(requestedStage)
    ? requestedStage
    : focusedObject
      ? getObjectStage(focusedObject)
      : "planning"
  const generatableObjectIds = useMemo(
    () =>
      canRunAction
        ? (graph?.objects
            .filter((object) => {
              const allowedActions =
                readiness.find((item) => item.projectObjectId === object.id)?.allowedActions ?? []
              return actions.some(
                (action) =>
                  allowedActions.includes(action.actionKey) &&
                  (action.actionKey.startsWith("image.") ||
                    action.actionKey.startsWith("video.") ||
                    action.actionKey.startsWith("audio.")) &&
                  action.applicableObjectTypes.includes(object.objectType)
              )
            })
            .map((object) => object.id) ?? [])
        : [],
    [actions, canRunAction, graph, readiness]
  )

  useEffect(() => {
    if (urlFocus !== undefined) setStoredFocus(urlFocus)
  }, [setStoredFocus, urlFocus])
  useEffect(() => {
    if (urlView === "graph" || urlView === "structure") setStoredView(urlView)
  }, [setStoredView, urlView])

  function updateUrl(values: Record<string, string | number | null>) {
    const next = new URLSearchParams(searchParams.toString())
    for (const [key, value] of Object.entries(values))
      value === null ? next.delete(key) : next.set(key, String(value))
    const query = next.toString()
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
  }

  function handleViewChange(nextView: ProjectWorkbenchView) {
    setStoredView(nextView)
    updateUrl({ view: nextView })
  }

  function handleFocusObject(id: number) {
    const object = graph?.objects.find((item) => item.id === id)
    setStoredFocus(id)
    updateUrl({
      focus: id,
      ...(view === "structure" && object ? { stage: getObjectStage(object) } : {})
    })
  }

  function handleOpenDetails(id: number) {
    handleFocusObject(id)
    detailPanel.onTrue()
  }
  function handleGenerateObject(id: number) {
    if (canRunAction) {
      handleFocusObject(id)
      setGenerationObjectId(id)
    }
  }
  function handleStructureAction(action: ProjectObjectStructureAction) {
    if (canEditProject) setStructureAction(action)
  }
  function handleStructureActionByObjectId(
    kind: "contract" | "remove" | "append-child",
    id: number
  ) {
    const object = graph?.objects.find((item) => item.id === id)
    if (!object) return
    handleStructureAction(
      kind === "append-child" ? { kind: "append", parent: object } : { kind, object }
    )
  }

  if (projectLoading || graphLoading)
    return (
      <div className="flex h-full flex-col gap-3 p-6">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="min-h-[520px] flex-1" />
      </div>
    )
  if (!project || !graph)
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-muted-foreground">
        <FolderKanban className="size-12 opacity-30" />
        <p className="text-sm">项目不存在或无权访问</p>
        <GlowButton
          nativeButton={false}
          render={<Link href="/studio/projects" />}
          tone="ghost"
          size="sm"
        >
          返回项目列表
        </GlowButton>
      </div>
    )

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden">
      <ProjectWorkbenchHeader
        project={project}
        view={view}
        lifecycleActions={
          <>
            {canProjectUpdate ? (
              <Button
                variant="outline"
                size="sm"
                disabled={!lifecycleWritable}
                onClick={() => {
                  if (canEditProject) basicInfoDialog.onTrue()
                }}
              >
                <Pencil />
                修改
              </Button>
            ) : null}
            <ProjectLifecycleActions
              project={project}
              objects={graph.objects}
              canLifecycleUpdate={canLifecycle}
            />
          </>
        }
        assistantControl={
          <ProjectAssistantControl projectId={project.id} canBind={canProjectUpdate} />
        }
        onViewChange={handleViewChange}
        onOpenBrief={briefPanel.onTrue}
        briefOpen={briefPanel.value}
        onOpenDocuments={documentPanel.onTrue}
        documentsOpen={documentPanel.value}
        documentCount={documentRefs.length}
        onOpenSchedule={scheduleSheet.onTrue}
        scheduleOpen={scheduleSheet.value}
      />
      <ContentActionBar
        project={project}
        focusedObject={focusedObject}
        readiness={readiness.find((item) => item.projectObjectId === focusedObject?.id)}
        canAction={canRunAction}
        disabledReason={contentActionDisabledReason}
        progress={progress}
        canPropose={canEditProject}
        proposalDisabledReason={proposalDisabledReason}
        onOpenProposals={proposalSheet.onTrue}
      />

      <main className="min-h-0 flex-1 overflow-auto p-4 sm:p-6">
        <div className="flex min-h-full flex-col gap-5">
          <div className="min-h-[520px] flex-1">
            {view === "graph" ? (
              <ProjectGraphView
                projectId={project.id}
                graph={graph}
                readiness={readiness}
                focusObjectId={focusObjectId}
                readOnly={readOnly}
                generatableObjectIds={generatableObjectIds}
                onFocusObject={handleFocusObject}
                onOpenDetails={handleOpenDetails}
                onGenerateObject={handleGenerateObject}
                onUpdateContract={(id) => handleStructureActionByObjectId("contract", id)}
                onRemoveObject={(id) => handleStructureActionByObjectId("remove", id)}
                onAppendChildObject={(id) => handleStructureActionByObjectId("append-child", id)}
              />
            ) : (
              <ProjectStructureView
                graph={graph}
                readiness={readiness}
                activeStage={activeStage}
                focusObjectId={focusObjectId}
                readOnly={readOnly}
                generatableObjectIds={generatableObjectIds}
                onStageChange={(stage) => updateUrl({ stage })}
                onAppendObject={() => handleStructureAction({ kind: "append" })}
                onAppendChildObject={(object) =>
                  handleStructureAction({ kind: "append", parent: object })
                }
                onUpdateContract={(object) => handleStructureAction({ kind: "contract", object })}
                onRemoveObject={(object) => handleStructureAction({ kind: "remove", object })}
                onOpenDetails={handleOpenDetails}
                onGenerateObject={handleGenerateObject}
              />
            )}
          </div>
          {progress ? (
            <Collapsible open={evidencePanel.value} onOpenChange={evidencePanel.setValue}>
              <CollapsibleTrigger
                render={<Button type="button" variant="ghost" size="sm" />}
                aria-label={evidencePanel.value ? "收起进度明细" : "展开进度明细"}
              >
                {evidencePanel.value ? <ChevronUp /> : <ChevronDown />}
                进度明细（排障用，日常进度看对象状态与 Assistant 对话）
              </CollapsibleTrigger>
              <CollapsibleContent className="pt-3">
                <ProjectProgressPanel progress={progress} />
              </CollapsibleContent>
            </Collapsible>
          ) : null}
          <DeliveryWorkflowPanel
            project={project}
            objects={graph.objects}
            canEvaluate={canProjectRead}
            canFreeze={canProjectUpdate}
            canSubmitReview={canReview}
            canDecideReview={canReview}
            canCollect={canCollect}
          />
        </div>
      </main>

      <footer className="flex flex-wrap items-center gap-x-5 gap-y-1 border-t bg-background/80 px-4 py-2 text-muted-foreground text-xs sm:px-6">
        <span>图谱 revision {graph.graphRevision}</span>
        <span>{graph.objects.length} 个对象</span>
        <span>
          {progress?.objects.filter((item) => item.maturity === "CANDIDATE_AVAILABLE").length ?? 0}{" "}
          个候选可用
        </span>
        <span>
          {progress?.readiness.filter(
            (item) => item.state === "BLOCKED_BY_DEPENDENCY" || item.state === "BLOCKED_BY_POLICY"
          ).length ?? 0}{" "}
          项被阻断
        </span>
        <span>
          {progress?.executions.filter((execution) => execution.status === "RUNNING").length ?? 0}{" "}
          个 Run 执行中
        </span>
        <span>
          {progress?.candidates.filter((candidate) => candidate.status === "candidate").length ?? 0}{" "}
          个候选待决策
        </span>
        <span>实际积分 {progress ? progress.credits.netActual : "加载中"}</span>
        <span className={progress?.delivery.completionSatisfied ? "text-emerald-600" : undefined}>
          {progress?.delivery.completionSatisfied
            ? "交付证据已满足"
            : `${progress?.delivery.blockers.length ?? 0} 项交付阻断`}
        </span>
        {readOnly ? (
          <span className="text-amber-500">
            {canProjectUpdate ? `${project.status} · 内容全局只读` : "无项目更新权限"}
          </span>
        ) : null}
      </footer>

      <ProjectBasicInfoDialog
        open={basicInfoDialog.value}
        project={project}
        readOnly={!canEditProject}
        onOpenChange={basicInfoDialog.setValue}
      />
      <ProjectBriefPanel
        open={briefPanel.value}
        onOpenChange={briefPanel.setValue}
        project={project}
        readOnly={!canEditProject}
        progress={progress}
      />
      <ProjectDocumentPanel
        open={documentPanel.value}
        onOpenChange={documentPanel.setValue}
        project={project}
        readOnly={!canEditProject}
      />
      <GenerationProposalPanel
        open={proposalSheet.value}
        onOpenChange={proposalSheet.setValue}
        project={project}
        objects={graph.objects}
        documentRefs={documentRefs}
        actions={actions}
        canMutate={canEditProject}
        currentTask={progress?.currentTask ?? null}
      />
      <ObjectDetailPanel
        open={detailPanel.value}
        project={project}
        object={focusedObject}
        progress={progress}
        lifecycleWritable={lifecycleWritable}
        canAction={canAction}
        canAdopt={canAdopt}
        canTimelineRead={canTimelineRead}
        canTimelineCreate={canTimelineCreate}
        canTimelineUpdate={canTimelineUpdate}
        canTimelineDelete={canTimelineDelete}
        onOpenChange={detailPanel.setValue}
      />
      <ProjectObjectGenerationDialog
        open={generationObjectId !== null && canRunAction}
        project={project}
        object={graph.objects.find((object) => object.id === generationObjectId)}
        mutable={canRunAction}
        onOpenChange={(open) => {
          if (!open) setGenerationObjectId(null)
        }}
      />
      <ProjectObjectStructureDialog
        action={structureAction}
        project={project}
        objects={graph.objects}
        mutable={canEditProject}
        onOpenChange={(open) => {
          if (!open) setStructureAction(null)
        }}
      />
      <ProjectScheduleSheet
        projectId={project.id}
        conversationId={conversationContext?.state === "READY" ? conversationContext.threadId : ""}
        assistantId={conversationContext?.state === "READY" ? conversationContext.assistantId : ""}
        open={scheduleSheet.value}
        onOpenChange={scheduleSheet.setValue}
      />
    </div>
  )
}
