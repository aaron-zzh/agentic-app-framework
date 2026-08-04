/**
 * 唯一 AIGC Project 聚合 API：物化、图谱、对象版本与生命周期。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import type { AigcProductionMode, AigcProjectTypeCode } from "./configuration"

export type AigcProjectStatus =
  | "draft"
  | "in_progress"
  | "reviewing"
  | "delivering"
  | "completed"
  | "archived"
export type AigcGenerationMode = "manual" | "auto"
export type AigcObjectStatus =
  | "empty"
  | "draft"
  | "pending_confirm"
  | "adopted"
  | "blocked"
  | "done"
export type AigcObjectVersionStatus = "candidate" | "adopted" | "rejected" | "superseded"
export type AigcObjectSource = "blueprint" | "user" | "assistant" | "workflow" | "import"
export type AigcRelationLayer = "domain" | "reference" | "story_order" | "execution"
export type AigcRelationType =
  | "contains"
  | "constrains"
  | "derives"
  | "references"
  | "composes"
  | "orders"
  | "variant"
  | "execution_depends"
export type AigcObjectType =
  | "brief"
  | "creative_concept"
  | "deliverable_set"
  | "image_deliverable"
  | "video_deliverable"
  | "copy_deliverable"
  | "article_deliverable"
  | "topic"
  | "source_material_set"
  | "article_outline"
  | "seo_metadata"
  | "distribution_variant"
  | "episode"
  | "scene"
  | "shot"
  | "shot_keyframe"
  | "character"
  | "prop"
  | "review"
  | "canvas_board"
  | "property_subject"
  | "claim_evidence"

export interface AigcProject {
  id: number
  version: number
  name: string
  description?: string
  projectTypeCode: AigcProjectTypeCode
  blueprintCode?: string
  blueprintVersion?: string
  domainExtensionCode?: string
  domainExtensionVersion?: string
  productionMode: AigcProductionMode
  generationMode: AigcGenerationMode
  status: AigcProjectStatus
  brief?: string
  prompt?: string
  coverMediaVersionId?: number
  configSnapshotId?: number
  graphRevision: number
  primaryBrandProfileId?: number
  assistantId?: number
  budgetLimit?: number
  costUsed: number
  lastActiveTime?: string
  createTime: string
  updateTime: string
}

export interface AigcProjectView {
  id: number
  name: string
  lifecycleStage: AigcProjectStatus
  version: number
  configurationSnapshotId: number
  projectTypeCode: AigcProjectTypeCode
  domainExtensionCode?: string
  productionMode: AigcProductionMode
  generationMode: AigcGenerationMode
  channelSpecVersionIds: number[]
  budgetLimit?: number
  costUsed: number
  brief?: string
  userId: number
}

export interface AigcProjectObject {
  id: number
  version: number
  projectId: number
  objectType: AigcObjectType
  objectKey: string
  blueprintNodeKey?: string
  parentId?: number
  sortOrder: number
  title?: string
  status: AigcObjectStatus
  source: AigcObjectSource
  schemaVersion?: string
  entityResource?: string
  entityId?: number
  adoptedVersionId?: number
  summary?: string
  payload?: Record<string, unknown>
}

export interface AigcProjectRelation {
  id: number
  projectId: number
  relationType: AigcRelationType
  layer: AigcRelationLayer
  sourceObjectId: number
  targetObjectId: number
  relationMeta?: Record<string, unknown>
}

export interface AigcProjectChannelRef {
  id: number
  channelSpecId: number
  primaryChannel: boolean
  overrideConfig?: Record<string, unknown>
}

export interface AigcProjectGraph {
  project: AigcProject
  graphRevision: number
  objects: AigcProjectObject[]
  relations: AigcProjectRelation[]
}

export interface AigcProjectSummary {
  projectId: number
  objectCount: number
  deliverableCount: number
  pendingConfirmCount: number
  blockedCount: number
  executionRunningCount: number
  candidateVersionCount: number
  adoptedVersionCount: number
  activeWorkCount: number
  publicationCount: number
  unpublishedPublicationCount: number
  completionReady: boolean
  costUsed: number
}

export interface AigcObjectVersion {
  id: number
  projectId: number
  objectId: number
  versionNo: number
  status: AigcObjectVersionStatus
  contentPayload?: Record<string, unknown>
  documentVersionId?: number
  mediaVersionIds: number[]
  executionRunId?: number
  summary?: string
  adoptedTime?: string
  supersededByVersionId?: number
  createTime: string
}

export interface AigcObjectVersionView {
  id: number
  objectId: number
  versionNo: number
  status: AigcObjectVersionStatus
  executionRunId?: number
}

export interface AigcProjectMaterializeInput {
  name: string
  projectTypeCode: AigcProjectTypeCode
  blueprintVersionId: number
  domainExtensionVersionId?: number
  brandProfileVersionIds?: number[]
  channelSpecVersionIds?: number[]
  productionMode: AigcProductionMode
  briefJson?: string
}

export interface AigcProjectUpdateInput {
  name?: string
  description?: string
  brief?: string
  prompt?: string
  coverMediaVersionId?: number
  assistantId?: number
  budgetLimit?: number
  expectedVersion: number
}

export interface AigcProjectParams {
  pageNo?: number
  pageSize?: number
  status?: AigcProjectStatus
  projectTypeCode?: AigcProjectTypeCode
  productionMode?: AigcProductionMode
}

export interface AigcVersionDecisionInput {
  projectId: number
  objectId: number
  versionId: number
  expectedProjectVersion: number
  reason?: string
}

export interface AigcReviewApproveInput {
  projectId: number
  reviewObjectId: number
  expectedProjectVersion: number
  conclusion?: string
}

export type AigcProjectLifecycleAction = "submit-review" | "complete" | "archive"

export const aigcProjectApi = {
  projects: (params: AigcProjectParams = {}) =>
    backendApi.get<PageResult<AigcProject>>("/aigc/projects", {
      params: { pageNo: 1, pageSize: 50, ...params }
    }),
  project: (id: number) => backendApi.get<AigcProject>(`/aigc/projects/${id}`),
  materialize: (data: AigcProjectMaterializeInput) =>
    backendApi.post<AigcProjectView>("/aigc/projects/_materialize", data),
  update: (id: number, data: AigcProjectUpdateInput) =>
    backendApi.put<AigcProject>(`/aigc/projects/${id}`, data),
  graph: (id: number) => backendApi.get<AigcProjectGraph>(`/aigc/projects/${id}/graph`),
  summary: (id: number) => backendApi.get<AigcProjectSummary>(`/aigc/projects/${id}/summary`),
  channelRefs: (id: number) =>
    backendApi.get<AigcProjectChannelRef[]>(`/aigc/projects/${id}/channel-refs`),
  objects: (id: number) => backendApi.get<AigcProjectObject[]>(`/aigc/projects/${id}/objects`),
  versions: (projectId: number, objectId: number) =>
    backendApi.get<AigcObjectVersion[]>(`/aigc/projects/${projectId}/objects/${objectId}/versions`),
  adoptVersion: (input: AigcVersionDecisionInput) =>
    backendApi.post<AigcObjectVersionView>(
      `/aigc/projects/${input.projectId}/objects/${input.objectId}/versions/${input.versionId}/_adopt`,
      { expectedProjectVersion: input.expectedProjectVersion, reason: input.reason }
    ),
  rejectVersion: (input: AigcVersionDecisionInput) =>
    backendApi.post<AigcObjectVersionView>(
      `/aigc/projects/${input.projectId}/objects/${input.objectId}/versions/${input.versionId}/_reject`,
      { expectedProjectVersion: input.expectedProjectVersion, reason: input.reason }
    ),
  submitReview: (projectId: number, expectedVersion: number) =>
    backendApi.post<AigcProjectView>(`/aigc/projects/${projectId}/_submit-review`, {
      expectedVersion
    }),
  approveReview: (input: AigcReviewApproveInput) =>
    backendApi.post<AigcProjectView>(`/aigc/projects/${input.projectId}/_approve-review`, {
      reviewObjectId: input.reviewObjectId,
      expectedProjectVersion: input.expectedProjectVersion,
      conclusion: input.conclusion
    }),
  complete: (projectId: number, expectedVersion: number) =>
    backendApi.post<AigcProjectView>(`/aigc/projects/${projectId}/_complete`, { expectedVersion }),
  archive: (projectId: number, expectedVersion: number) =>
    backendApi.post<AigcProjectView>(`/aigc/projects/${projectId}/_archive`, { expectedVersion })
}

export const aigcProjectKeys = {
  all: ["aigc.project"] as const,
  list: (params: AigcProjectParams) => ["aigc.project", "list", params] as const,
  detail: (id: number) => ["aigc.project", "detail", id] as const,
  graph: (id: number) => ["aigc.project", "graph", id] as const,
  summary: (id: number) => ["aigc.project", "summary", id] as const,
  channelRefs: (id: number) => ["aigc.project", "channel-refs", id] as const,
  versions: (projectId: number, objectId: number) =>
    ["aigc.project", "versions", projectId, objectId] as const
}

export function useAigcProjects(params: AigcProjectParams = {}) {
  return useQuery({
    queryKey: aigcProjectKeys.list(params),
    queryFn: () => aigcProjectApi.projects(params)
  })
}

export function useAigcProject(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.detail(id ?? 0),
    queryFn: () => aigcProjectApi.project(id as number),
    enabled: id !== null
  })
}

export function useAigcProjectGraph(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.graph(id ?? 0),
    queryFn: () => aigcProjectApi.graph(id as number),
    enabled: id !== null
  })
}

export function useAigcProjectSummary(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.summary(id ?? 0),
    queryFn: () => aigcProjectApi.summary(id as number),
    enabled: id !== null
  })
}

export function useAigcProjectChannelRefs(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.channelRefs(id ?? 0),
    queryFn: () => aigcProjectApi.channelRefs(id as number),
    enabled: id !== null
  })
}

export function useAigcObjectVersions(projectId: number | null, objectId: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.versions(projectId ?? 0, objectId ?? 0),
    queryFn: () => aigcProjectApi.versions(projectId as number, objectId as number),
    enabled: projectId !== null && objectId !== null
  })
}

function invalidateProject(queryClient: ReturnType<typeof useQueryClient>, projectId: number) {
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.graph(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.all })
}

export function useMaterializeAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.materialize,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: aigcProjectKeys.all })
  })
}

export function useUpdateAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: AigcProjectUpdateInput }) =>
      aigcProjectApi.update(id, data),
    onSuccess: (project) => invalidateProject(queryClient, project.id)
  })
}

export function useAdoptAigcObjectVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.adoptVersion,
    onSuccess: (_version, input) => {
      invalidateProject(queryClient, input.projectId)
      queryClient.invalidateQueries({
        queryKey: aigcProjectKeys.versions(input.projectId, input.objectId)
      })
    }
  })
}

export function useRejectAigcObjectVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.rejectVersion,
    onSuccess: (_version, input) => {
      invalidateProject(queryClient, input.projectId)
      queryClient.invalidateQueries({
        queryKey: aigcProjectKeys.versions(input.projectId, input.objectId)
      })
    }
  })
}

export function useAigcProjectLifecycle() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      projectId,
      action,
      expectedVersion
    }: {
      projectId: number
      action: AigcProjectLifecycleAction
      expectedVersion: number
    }) => {
      if (action === "submit-review") return aigcProjectApi.submitReview(projectId, expectedVersion)
      if (action === "complete") return aigcProjectApi.complete(projectId, expectedVersion)
      return aigcProjectApi.archive(projectId, expectedVersion)
    },
    onSuccess: (_project, input) => invalidateProject(queryClient, input.projectId)
  })
}

export function useApproveAigcProjectReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.approveReview,
    onSuccess: (_project, input) => invalidateProject(queryClient, input.projectId)
  })
}
