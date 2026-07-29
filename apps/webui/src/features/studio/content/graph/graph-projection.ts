/**
 * ProjectGraph 领域数据到 React Flow 的纯投影。
 *
 * 仅计算视图节点和边，不持久化或修改任何项目业务状态。
 * @author AaronZZH & Kiro
 */

import type { Edge, Node } from "@xyflow/react"
import type {
  ContentObjectStatus,
  ContentObjectType,
  ContentProjectGraphVO,
  ContentProjectObjectVO,
  ContentRelationLayer
} from "@/lib/api/rest/content"

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

export const OBJECT_TYPE_LABELS: Record<ContentObjectType, string> = {
  brief: "简报",
  creative_concept: "创意方向",
  deliverable_set: "内容包",
  image_deliverable: "图片交付物",
  video_deliverable: "视频交付物",
  copy_deliverable: "文案交付物",
  episode: "分集",
  scene: "场次",
  shot: "镜头",
  shot_keyframe: "镜头关键帧",
  review: "审核",
  inspiration_board: "灵感板",
  property_subject: "楼盘资料",
  claim_evidence: "主张证据"
}

export const OBJECT_STATUS_LABELS: Record<ContentObjectStatus, string> = {
  empty: "空槽位",
  draft: "草稿",
  pending_confirm: "待确认",
  adopted: "已采用",
  blocked: "已阻断",
  done: "已完成"
}

const TYPE_STAGE: Record<ContentObjectType, ProjectGraphStage> = {
  property_subject: "context",
  claim_evidence: "context",
  brief: "planning",
  creative_concept: "creative",
  deliverable_set: "package",
  image_deliverable: "material",
  video_deliverable: "material",
  copy_deliverable: "material",
  episode: "material",
  scene: "material",
  shot: "material",
  shot_keyframe: "material",
  inspiration_board: "material",
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
  status?: ContentObjectStatus
  objectId?: number
  objectType?: ContentObjectType
  objectCount: number
  pendingCount: number
  blockedCount: number
  collapsed: boolean
  zoomTier: ProjectGraphZoomTier
}

export type ContentGraphNode = Node<ContentGraphNodeData, "contentDomain">

export interface GraphProjectionOptions {
  collapsedGroups: ProjectGraphStage[]
  activeLayers: ContentRelationLayer[]
  focusObjectId?: number
  zoomTier: ProjectGraphZoomTier
}

export interface GraphProjection {
  nodes: ContentGraphNode[]
  edges: Edge[]
}

export function getObjectStage(object: ContentProjectObjectVO): ProjectGraphStage {
  return TYPE_STAGE[object.objectType]
}

function groupSummary(objects: ContentProjectObjectVO[]): string {
  const pending = objects.filter((object) => object.status === "pending_confirm").length
  const blocked = objects.filter((object) => object.status === "blocked").length
  const shots = objects.filter((object) => object.objectType === "shot").length
  const keyframes = objects.filter((object) => object.objectType === "shot_keyframe").length
  const base = shots > 0 ? `${shots} 镜头 · ${keyframes} 关键帧` : `${objects.length} 个对象`
  const gaps = objects.filter((object) => object.status === "empty").length
  return `${base} · ${gaps} 个缺口 · ${pending} 项待确认${blocked > 0 ? ` · ${blocked} 项阻断` : ""}`
}

export function toGraphProjection(
  graph: ContentProjectGraphVO,
  options: GraphProjectionOptions
): GraphProjection {
  const collapsed = new Set(options.collapsedGroups)
  const activeLayers = new Set(options.activeLayers)
  const objectsByStage = new Map<ProjectGraphStage, ContentProjectObjectVO[]>()
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
    const pendingCount = stageObjects.filter((object) => object.status === "pending_confirm").length
    const blockedCount = stageObjects.filter((object) => object.status === "blocked").length
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
        summary: groupSummary(stageObjects),
        objectCount: stageObjects.length,
        pendingCount,
        blockedCount,
        collapsed: collapsed.has(stage.key),
        zoomTier: options.zoomTier
      }
    })

    if (collapsed.has(stage.key)) return
    stageObjects.forEach((object, rowIndex) => {
      nodes.push({
        id: String(object.id),
        type: "contentDomain",
        position: { x: columnIndex * 300, y: 125 + rowIndex * 125 },
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
          objectCount: 1,
          pendingCount: object.status === "pending_confirm" ? 1 : 0,
          blockedCount: object.status === "blocked" ? 1 : 0,
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
      label: RELATION_LABELS[relation.relationType],
      animated: relation.layer === "execution",
      data: { relationType: relation.relationType, layer: relation.layer },
      style: { stroke: "var(--color-muted-foreground)", strokeWidth: 1.5 },
      labelStyle: { fill: "var(--color-muted-foreground)", fontSize: 10 }
    })
  }

  return { nodes, edges }
}
