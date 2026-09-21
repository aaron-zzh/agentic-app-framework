/**
 * ProjectGraph 领域数据到 React Flow 的纯投影。
 *
 * 仅计算视图节点和边，不持久化或修改任何项目业务状态。
 * @author AaronZZH & Kiro
 */

import type { Edge, Node } from "@xyflow/react"
import type {
  AigcObjectReadiness,
  AigcObjectReadinessState,
  AigcObjectStatus,
  AigcObjectType,
  AigcOutputContract,
  AigcProjectGraph,
  AigcProjectObject,
  AigcRelationLayer,
  AigcSemanticRole
} from "@/lib/api/rest/ai/aigc"

export type ProjectGraphStage =
  | "context"
  | "planning"
  | "creative"
  | "package"
  | "material"
  | "governance"
export type ProjectGraphZoomTier = "global" | "overview" | "detail"

export const PROJECT_GRAPH_STAGES: {
  key: ProjectGraphStage
  label: string
  description: string
}[] = [
  { key: "context", label: "上下文", description: "品牌、事实与约束" },
  { key: "planning", label: "策划", description: "简报与目标" },
  { key: "creative", label: "创意", description: "创意方向与方案" },
  { key: "package", label: "内容包", description: "交付范围与组成" },
  { key: "material", label: "素材 / 版本", description: "内容对象与采用版本" },
  { key: "governance", label: "审核 / 导出", description: "检查、审核与完成" }
]

export const OBJECT_TYPE_LABELS: Record<AigcObjectType, string> = {
  brief: "简报",
  creative_concept: "创意方向",
  deliverable_set: "内容包",
  image_deliverable: "图片交付物",
  video_deliverable: "视频交付物",
  copy_deliverable: "文案交付物",
  article_deliverable: "博客文章",
  topic: "文章选题",
  source_material_set: "来源资料",
  article_outline: "文章提纲",
  seo_metadata: "SEO 元数据",
  distribution_variant: "渠道分发变体",
  episode: "分集",
  scene: "场次",
  shot: "镜头",
  shot_keyframe: "镜头关键帧",
  audio_deliverable: "音频交付物",
  character: "角色",
  prop: "道具",
  review: "审核",
  canvas_board: "画布节点",
  property_subject: "楼盘资料",
  claim_evidence: "主张证据"
}

export const OBJECT_STATUS_LABELS: Record<AigcObjectStatus, string> = {
  EMPTY: "空槽位",
  CANDIDATE_AVAILABLE: "候选可用",
  ADOPTED: "已采用"
}

export const READINESS_LABELS: Record<AigcObjectReadinessState, string> = {
  SATISFIED: "已满足",
  READY: "可执行",
  BLOCKED_BY_DEPENDENCY: "依赖阻断",
  BLOCKED_BY_POLICY: "策略阻断",
  ACTIVE_RUN: "Run 执行中",
  PENDING_CANDIDATE: "等待候选",
  AWAITING_CONFIRMATION: "等待决策"
}

/** 对象媒体动作的确定性呈现语义，结构视图与图谱视图共用。 */
export interface ObjectMediaActionPresentation {
  /** 按钮文案。 */
  label: string
  /** 点击后应进入的确定性入口。 */
  intent: "generate" | "candidate"
  disabled: boolean
  /** 禁用或状态原因，必须对用户可见。 */
  reason?: string
}

function mediaNoun(allowedActions: readonly string[]): string {
  if (allowedActions.includes("video.generate")) return "视频"
  if (allowedActions.some((action) => action.startsWith("audio."))) return "配音"
  if (allowedActions.some((action) => action.startsWith("image."))) return "图像"
  return "内容"
}

/**
 * 按服务端 readiness 与允许动作投影媒体按钮语义。
 *
 * 不做本地放行：服务端未允许即禁用，并给出可见原因。
 */
export function objectMediaActionPresentation(input: {
  readiness: AigcObjectReadiness | undefined
  generatable: boolean
  readOnly: boolean
}): ObjectMediaActionPresentation {
  const { readiness, generatable, readOnly } = input
  const noun = mediaNoun(readiness?.allowedActions ?? [])
  if (readiness === undefined) {
    return { label: `生成${noun}`, intent: "generate", disabled: true, reason: "Readiness 加载中" }
  }
  if (readiness.state === "ACTIVE_RUN") {
    return {
      label: "生成中",
      intent: "generate",
      disabled: true,
      reason: readiness.blockedReason || "对象存在活动 ExecutionRun"
    }
  }
  if (readiness.state === "PENDING_CANDIDATE" || readiness.state === "AWAITING_CONFIRMATION") {
    return {
      label: "查看候选",
      intent: "candidate",
      disabled: false,
      reason: readiness.blockedReason || READINESS_LABELS[readiness.state]
    }
  }
  if (readiness.state === "BLOCKED_BY_DEPENDENCY" || readiness.state === "BLOCKED_BY_POLICY") {
    return {
      label: `生成${noun}`,
      intent: "generate",
      disabled: true,
      reason: readiness.blockedReason || READINESS_LABELS[readiness.state]
    }
  }
  const label = readiness.state === "SATISFIED" ? "重新生成" : `生成${noun}`
  if (readOnly) {
    return { label, intent: "generate", disabled: true, reason: "当前项目内容只读" }
  }
  if (!generatable) {
    return {
      label,
      intent: "generate",
      disabled: true,
      reason: readiness.blockedReason || "服务端未允许该对象的媒体动作"
    }
  }
  return { label, intent: "generate", disabled: false }
}

/** 结构写动作的确定性禁用语义，结构视图与图谱视图共用。 */
export function objectStructureActionState(input: {
  readiness: AigcObjectReadiness | undefined
  actionKey: "object.contract" | "object.remove" | "object.append.child"
  readOnly: boolean
}): { disabled: boolean; reason?: string } {
  const { readiness, actionKey, readOnly } = input
  if (readOnly) return { disabled: true, reason: "当前项目内容只读" }
  if (readiness === undefined) return { disabled: true, reason: "Readiness 加载中" }
  if (readiness.allowedActions.includes(actionKey)) return { disabled: false }
  return {
    disabled: true,
    reason:
      readiness.blockedReason ||
      (actionKey === "object.append.child"
        ? "当前项目结构未开放该对象的下级类型"
        : "服务端未允许该结构动作")
  }
}

export const SEMANTIC_ROLE_LABELS: Record<AigcSemanticRole, string> = {
  STRUCTURE: "结构",
  WORKING_ARTIFACT: "工作产物",
  DELIVERABLE: "交付物",
  EVIDENCE: "证据",
  REFERENCE: "引用"
}

export const OUTPUT_CONTRACT_LABELS: Record<AigcOutputContract, string> = {
  INLINE_OBJECT_VERSION: "内联对象版本",
  DOCUMENT_VERSION: "文档版本",
  MEDIA_VERSION: "媒体版本",
  COMPOSITE_MANIFEST: "组合 Manifest",
  REFERENCE_ONLY: "仅引用"
}

export const RELATION_LAYER_LABELS: Record<AigcRelationLayer, string> = {
  domain: "领域",
  reference: "引用",
  story_order: "故事顺序",
  execution: "执行依赖"
}

const TYPE_STAGE: Record<AigcObjectType, ProjectGraphStage> = {
  property_subject: "context",
  claim_evidence: "context",
  brief: "planning",
  topic: "planning",
  source_material_set: "context",
  creative_concept: "creative",
  article_outline: "creative",
  deliverable_set: "package",
  image_deliverable: "material",
  video_deliverable: "material",
  copy_deliverable: "material",
  article_deliverable: "material",
  seo_metadata: "material",
  distribution_variant: "material",
  episode: "material",
  scene: "material",
  shot: "material",
  shot_keyframe: "material",
  audio_deliverable: "material",
  character: "material",
  prop: "material",
  canvas_board: "material",
  review: "governance"
}

const RELATION_LABELS = {
  contains: "包含",
  constrains: "约束",
  derives: "派生",
  references: "引用",
  composes: "组成",
  orders: "顺序",
  variant: "变体",
  execution_depends: "执行依赖"
} as const

export interface ContentGraphNodeData extends Record<string, unknown> {
  kind: "group" | "object"
  stage: ProjectGraphStage
  title: string
  description?: string
  summary: string
  status?: AigcObjectStatus
  objectId?: number
  objectType?: AigcObjectType
  semanticRole?: AigcSemanticRole
  outputContract?: AigcOutputContract
  readinessState?: AigcObjectReadinessState
  blockedReason?: string | null
  objectCount: number
  pendingCount: number
  blockedCount: number
  collapsed: boolean
  zoomTier: ProjectGraphZoomTier
}

export type ContentGraphNode = Node<ContentGraphNodeData, "contentDomain">

export interface GraphProjectionOptions {
  collapsedGroups: ProjectGraphStage[]
  activeLayers: AigcRelationLayer[]
  readiness: AigcObjectReadiness[]
  focusObjectId?: number
  zoomTier: ProjectGraphZoomTier
}

export interface GraphProjection {
  nodes: ContentGraphNode[]
  edges: Edge[]
}

export function getObjectStage(object: AigcProjectObject): ProjectGraphStage {
  return TYPE_STAGE[object.objectType]
}

function isBlocked(readiness: AigcObjectReadiness | undefined): boolean {
  return readiness?.state === "BLOCKED_BY_DEPENDENCY" || readiness?.state === "BLOCKED_BY_POLICY"
}

function groupSummary(
  objects: AigcProjectObject[],
  readinessByObjectId: ReadonlyMap<number, AigcObjectReadiness>
): string {
  const candidates = objects.filter((object) => object.status === "CANDIDATE_AVAILABLE").length
  const adopted = objects.filter((object) => object.status === "ADOPTED").length
  const blocked = objects.filter((object) => isBlocked(readinessByObjectId.get(object.id))).length
  const shots = objects.filter((object) => object.objectType === "shot").length
  const keyframes = objects.filter((object) => object.objectType === "shot_keyframe").length
  const base = shots > 0 ? `${shots} 镜头 · ${keyframes} 关键帧` : `${objects.length} 个对象`
  const gaps = objects.filter((object) => object.status === "EMPTY").length
  return `${base} · ${gaps} 个缺口 · ${candidates} 个候选 · ${adopted} 个已采用${
    blocked > 0 ? ` · ${blocked} 项阻断` : ""
  }`
}

export function toGraphProjection(
  graph: AigcProjectGraph,
  options: GraphProjectionOptions
): GraphProjection {
  const collapsed = new Set(options.collapsedGroups)
  const activeLayers = new Set(options.activeLayers)
  const readinessByObjectId = new Map(
    options.readiness.map((item) => [item.projectObjectId, item] as const)
  )
  const objectsByStage = new Map<ProjectGraphStage, AigcProjectObject[]>()
  const objectStage = new Map<number, ProjectGraphStage>()

  for (const stage of PROJECT_GRAPH_STAGES) objectsByStage.set(stage.key, [])
  for (const object of graph.objects) {
    const stage = getObjectStage(object)
    objectsByStage.get(stage)?.push(object)
    objectStage.set(object.id, stage)
  }
  for (const objects of objectsByStage.values()) {
    objects.sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)
  }

  const nodes: ContentGraphNode[] = []
  PROJECT_GRAPH_STAGES.forEach((stage, columnIndex) => {
    const stageObjects = objectsByStage.get(stage.key) ?? []
    const pendingCount = stageObjects.filter(
      (object) => object.status === "CANDIDATE_AVAILABLE"
    ).length
    const blockedCount = stageObjects.filter((object) =>
      isBlocked(readinessByObjectId.get(object.id))
    ).length
    nodes.push({
      id: `group:${stage.key}`,
      type: "contentDomain",
      position: { x: columnIndex * 300, y: 0 },
      selectable: true,
      data: {
        kind: "group",
        stage: stage.key,
        title: stage.label,
        description: stage.description,
        summary: groupSummary(stageObjects, readinessByObjectId),
        objectCount: stageObjects.length,
        pendingCount,
        blockedCount,
        collapsed: collapsed.has(stage.key),
        zoomTier: options.zoomTier
      }
    })

    if (collapsed.has(stage.key)) return
    stageObjects.forEach((object, rowIndex) => {
      const objectReadiness = readinessByObjectId.get(object.id)
      nodes.push({
        id: String(object.id),
        type: "contentDomain",
        position: { x: columnIndex * 300, y: 125 + rowIndex * 145 },
        selected: object.id === options.focusObjectId,
        data: {
          kind: "object",
          stage: stage.key,
          title: object.title || OBJECT_TYPE_LABELS[object.objectType],
          description: OBJECT_TYPE_LABELS[object.objectType],
          summary: object.summary || `${OBJECT_STATUS_LABELS[object.status]} · ${object.source}`,
          status: object.status,
          objectId: object.id,
          objectType: object.objectType,
          semanticRole: object.semanticRole,
          outputContract: object.outputContract,
          readinessState: objectReadiness?.state,
          blockedReason: objectReadiness?.blockedReason,
          objectCount: 1,
          pendingCount: object.status === "CANDIDATE_AVAILABLE" ? 1 : 0,
          blockedCount: isBlocked(objectReadiness) ? 1 : 0,
          collapsed: false,
          zoomTier: options.zoomTier
        }
      })
    })
  })

  const edges: Edge[] = []
  const edgeKeys = new Set<string>()
  for (const relation of graph.relations) {
    if (!activeLayers.has(relation.layer)) continue
    const sourceStage = objectStage.get(relation.sourceObjectId)
    const targetStage = objectStage.get(relation.targetObjectId)
    if (!sourceStage || !targetStage) continue
    const source = collapsed.has(sourceStage)
      ? `group:${sourceStage}`
      : String(relation.sourceObjectId)
    const target = collapsed.has(targetStage)
      ? `group:${targetStage}`
      : String(relation.targetObjectId)
    if (source === target) continue
    const key = `${source}:${target}:${relation.relationType}:${relation.layer}`
    if (edgeKeys.has(key)) continue
    edgeKeys.add(key)
    edges.push({
      id: key,
      source,
      target,
      label: `${RELATION_LABELS[relation.relationType]} · ${RELATION_LAYER_LABELS[relation.layer]}`,
      animated: relation.layer === "execution",
      data: { relationType: relation.relationType, layer: relation.layer },
      style: { stroke: "var(--color-muted-foreground)", strokeWidth: 1.5 },
      labelStyle: { fill: "var(--color-muted-foreground)", fontSize: 10 }
    })
  }

  return { nodes, edges }
}
