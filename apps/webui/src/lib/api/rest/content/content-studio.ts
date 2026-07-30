/**
 * Content Studio API 与查询 Hooks。
 *
 * 所有 Studio 请求固定使用个人视角；服务端业务状态仅由 TanStack Query 管理。
 *
 * @author AaronZZH & Kiro
 */

import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../types"
import { backendApi } from "../backend-client"

export type ContentProjectStatus = "draft" | "in_progress" | "reviewing" | "completed" | "archived"
export type ContentProjectTypeCode =
  | "new_product"
  | "promotion"
  | "brand_visual"
  | "store"
  | "social"
  | "personal_ip"
  | "narrative_series"
export type ContentProductionMode = "standard" | "short_drama" | "motion_comic"
export type ContentGenerationMode = "manual" | "auto"
export type ContentObjectType =
  | "brief"
  | "creative_concept"
  | "deliverable_set"
  | "image_deliverable"
  | "video_deliverable"
  | "copy_deliverable"
  | "episode"
  | "scene"
  | "shot"
  | "shot_keyframe"
  | "review"
  | "canvas_board"
  | "property_subject"
  | "claim_evidence"
export type ContentObjectStatus =
  | "empty"
  | "draft"
  | "pending_confirm"
  | "adopted"
  | "blocked"
  | "done"
export type ContentObjectSource = "blueprint" | "user" | "assistant" | "workflow" | "import"
export type ContentRelationType =
  | "contains"
  | "constrains"
  | "derives"
  | "references"
  | "composes"
  | "orders"
  | "variant"
  | "execution_depends"
export type ContentRelationLayer = "domain" | "reference" | "story_order" | "execution"
export type ContentExecutionStatus = "pending" | "running" | "succeeded" | "failed" | "canceled"
export type ContentExecutionTargetType = "agent" | "tool" | "workflow"
export type ContentObjectVersionStatus = "candidate" | "adopted" | "rejected" | "superseded"
export type ContentBrandProfileKind = "enterprise" | "sub_brand" | "product_line" | "personal_ip"
export type ContentConfigStatus = "draft" | "verifying" | "published" | "deprecated" | "withdrawn"
export type ContentChannel =
  | "xiaohongshu"
  | "douyin"
  | "wechat_channels"
  | "wechat_mp"
  | "offline_poster"
  | "bilibili"
export type ContentProfileRefScope = "primary" | "auxiliary"

export interface ContentProjectTypeVO {
  id: number
  code: ContentProjectTypeCode
  name: string
  icon?: string
  description?: string
  briefPlaceholder?: string
  defaultChannels: ContentChannel[]
  defaultProductionMode?: ContentProductionMode
  quickEntry: boolean
  builtin: boolean
  sortOrder: number
  status: ContentConfigStatus
}

export interface ContentBrandProfileVO {
  id: number
  version: number
  name: string
  kind: ContentBrandProfileKind
  industry?: string
  logoUrl?: string
  positioning?: string
  audience?: string
  toneOfVoice?: string
  visualStyle?: string
  disclaimer?: string
  forbiddenItems?: string
  profileVersion?: string
  status: ContentConfigStatus
  createTime: string
  updateTime: string
}

export interface ContentChannelSpecVO {
  id: number
  code: ContentChannel
  name: string
  specVersion: string
  aspectRatio?: string
  width?: number
  height?: number
  maxDurationSeconds?: number
  requiredDisclaimers?: string
  exportFormat?: string
  sortOrder: number
  status: ContentConfigStatus
}

export interface ContentBlueprintVO {
  id: number
  code: string
  name: string
  projectTypeCode: ContentProjectTypeCode
  blueprintVersion: string
  productionMode: ContentProductionMode
  description?: string
  status: ContentConfigStatus
}

export interface ContentDomainExtensionVO {
  id: number
  code: string
  name: string
  extensionVersion: string
  industry?: string
  region?: string
  language?: string
  status: ContentConfigStatus
}

export interface ContentProjectVO {
  id: number
  version: number
  name: string
  projectTypeCode: ContentProjectTypeCode
  blueprintCode?: string
  blueprintVersion?: string
  domainExtensionCode?: string
  domainExtensionVersion?: string
  productionMode: ContentProductionMode
  generationMode: ContentGenerationMode
  status: ContentProjectStatus
  brief?: string
  coverUrl?: string
  channels: ContentChannel[]
  graphRevision: number
  primaryBrandProfileId?: number
  primaryBrandProfileName?: string
  budgetLimit?: number
  costUsed: number
  lastActiveTime?: string
  createTime: string
  updateTime: string
}

export interface ContentProjectObjectVO {
  id: number
  version: number
  projectId: number
  objectType: ContentObjectType
  objectKey: string
  blueprintNodeKey?: string
  parentId?: number
  sortOrder: number
  title?: string
  status: ContentObjectStatus
  source: ContentObjectSource
  schemaVersion?: string
  entityResource?: string
  entityId?: number
  adoptedVersionRef?: string
  summary?: string
  payload?: Record<string, unknown>
}

export interface ContentProjectRelationVO {
  id: number
  projectId: number
  relationType: ContentRelationType
  layer: ContentRelationLayer
  sourceObjectId: number
  targetObjectId: number
  relationMeta?: Record<string, unknown>
}

export interface ContentProjectProfileRefVO {
  id: number
  projectId: number
  brandProfileId: number
  brandProfileName?: string
  refScope: ContentProfileRefScope
  profileVersion?: string
  scopeNote?: string
}

export interface ContentProjectGraphVO {
  projectId: number
  graphRevision: number
  objects: ContentProjectObjectVO[]
  relations: ContentProjectRelationVO[]
}

export interface ContentProjectSummaryVO {
  projectId: number
  objectCount: number
  deliverableCount: number
  pendingConfirmCount: number
  blockedCount: number
  executionRunningCount: number
  costUsed: number
}

export interface ContentExecutionRunVO {
  id: number
  projectId: number
  objectId?: number
  actionKey: string
  targetType: ContentExecutionTargetType
  targetRef?: string
  status: ContentExecutionStatus
  generationMode?: ContentGenerationMode
  roleProfileCode?: string
  selectedModelVersion?: string
  costCredits?: number
  errorMessage?: string
  startTime?: string
  endTime?: string
  createTime: string
}

export interface ContentActionOptionVO {
  actionKey: string
  label: string
  targetType: ContentExecutionTargetType
  confirmationRequired: boolean
  estimatedCredits?: number
  applicableObjectTypes: string[]
}

export interface ContentActionCommandDTO {
  actionKey: string
  objectId?: number
  prompt?: string
  snippetIds?: number[]
  attachmentRefs?: string[]
  generationMode?: ContentGenerationMode
  confirmed?: boolean
}

export interface ContentObjectVersionVO {
  id: number
  version: number
  projectId: number
  objectId: number
  versionNo: number
  status: ContentObjectVersionStatus
  contentPayload?: Record<string, unknown>
  assetRefs: string[]
  executionRunId?: number
  summary?: string
  adoptedTime?: string
  supersededByVersionId?: number
  createTime: string
}

export interface ContentSnippetVO {
  id: number
  name: string
  category?: string
  content?: string
  referenceImageUrls: string[]
  variableSlots?: Record<string, unknown>
  projectTypeCode?: ContentProjectTypeCode
  brandProfileId?: number
  useCount: number
  isPublic: boolean
}

export interface ContentProjectMaterializeDTO {
  projectTypeCode: ContentProjectTypeCode
  name: string
  brief?: string
  primaryBrandProfileId?: number
  auxiliaryBrandProfileIds?: number[]
  channels?: ContentChannel[]
  productionMode?: ContentProductionMode
  domainExtensionCode?: string
  blueprintCode?: string
}

export interface ContentProjectStatusDTO {
  status: ContentProjectStatus
  expectedVersion: number
}

export interface ContentBrandProfileInput {
  name: string
  kind: ContentBrandProfileKind
  industry?: string
  logoUrl?: string
  positioning?: string
  audience?: string
  toneOfVoice?: string
  visualStyle?: string
  disclaimer?: string
  forbiddenItems?: string
  profileVersion?: string
  status?: ContentConfigStatus
}

export interface ContentProjectUpdateInput {
  name?: string
  brief?: string
  coverUrl?: string
  channels?: ContentChannel[]
  generationMode?: ContentGenerationMode
  budgetLimit?: number
}

export interface ContentProjectObjectInput {
  projectId: number
  objectType: ContentObjectType
  objectKey: string
  blueprintNodeKey?: string
  parentId?: number
  sortOrder?: number
  title?: string
  status: ContentObjectStatus
  source: ContentObjectSource
  schemaVersion?: string
  entityResource?: string
  entityId?: number
  summary?: string
  payload?: Record<string, unknown>
}

export interface ContentProjectRelationInput {
  projectId: number
  relationType: ContentRelationType
  layer: ContentRelationLayer
  sourceObjectId: number
  targetObjectId: number
  relationMeta?: Record<string, unknown>
}

export interface ContentProjectProfileRefInput {
  projectId: number
  brandProfileId: number
  refScope: ContentProfileRefScope
  profileVersion?: string
  scopeNote?: string
}

export interface ContentSnippetInput {
  name: string
  category?: string
  content?: string
  referenceImageUrls?: string[]
  variableSlots?: Record<string, unknown>
  projectTypeCode?: ContentProjectTypeCode
  brandProfileId?: number
  isPublic?: boolean
}

export interface ContentPageParams {
  pageNo?: number
  pageSize?: number
}
export interface ContentBlueprintParams extends ContentPageParams {
  projectTypeCode?: ContentProjectTypeCode
  productionMode?: ContentProductionMode
  status?: ContentConfigStatus
}
export interface ContentProjectParams extends ContentPageParams {
  status?: ContentProjectStatus
  projectTypeCode?: ContentProjectTypeCode
  productionMode?: ContentProductionMode
}
export interface ContentProjectObjectParams extends ContentPageParams {
  projectId?: number
  objectType?: ContentObjectType
  status?: ContentObjectStatus
}
export interface ContentProjectRelationParams extends ContentPageParams {
  projectId?: number
  relationType?: ContentRelationType
  layer?: ContentRelationLayer
}
export interface ContentExecutionRunParams extends ContentPageParams {
  projectId?: number
  objectId?: number
  status?: ContentExecutionStatus
}
export interface ContentObjectVersionParams extends ContentPageParams {
  projectId?: number
  objectId?: number
  status?: ContentObjectVersionStatus
}
export interface ContentSnippetParams extends ContentPageParams {
  category?: string
  projectTypeCode?: ContentProjectTypeCode
}

const OWN_SCOPE_HEADERS = { "X-Scope": "own" } as const
const pageParams = <T extends ContentPageParams>(params: T) => ({
  pageNo: 1,
  pageSize: 200,
  ...params
})

export const contentStudioApi = {
  projectTypes: (params: ContentPageParams = {}) =>
    backendApi.get<PageResult<ContentProjectTypeVO>>("/content/project-types", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  projectType: (id: number) =>
    backendApi.get<ContentProjectTypeVO>(`/content/project-types/${id}`, {
      headers: OWN_SCOPE_HEADERS
    }),
  blueprints: (params: ContentBlueprintParams = {}) =>
    backendApi.get<PageResult<ContentBlueprintVO>>("/content/blueprints", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  domainExtensions: (params: ContentPageParams = {}) =>
    backendApi.get<PageResult<ContentDomainExtensionVO>>("/content/domain-extensions", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  channelSpecs: (params: ContentPageParams = {}) =>
    backendApi.get<PageResult<ContentChannelSpecVO>>("/content/channel-specs", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  brandProfiles: (params: ContentPageParams = {}) =>
    backendApi.get<PageResult<ContentBrandProfileVO>>("/content/brand-profiles", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  brandProfile: (id: number) =>
    backendApi.get<ContentBrandProfileVO>(`/content/brand-profiles/${id}`, {
      headers: OWN_SCOPE_HEADERS
    }),
  createBrandProfile: (data: ContentBrandProfileInput) =>
    backendApi.post<ContentBrandProfileVO>("/content/brand-profiles", data, {
      headers: OWN_SCOPE_HEADERS
    }),
  updateBrandProfile: (id: number, data: ContentBrandProfileInput) =>
    backendApi.put<ContentBrandProfileVO>(`/content/brand-profiles/${id}`, data, {
      headers: OWN_SCOPE_HEADERS
    }),
  deleteBrandProfile: (id: number) =>
    backendApi.delete<void>(`/content/brand-profiles/${id}`, { headers: OWN_SCOPE_HEADERS }),
  projects: (params: ContentProjectParams = {}) =>
    backendApi.get<PageResult<ContentProjectVO>>("/content/projects", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  materializeProject: (data: ContentProjectMaterializeDTO) =>
    backendApi.post<ContentProjectVO>("/content/projects/_materialize", data, {
      headers: OWN_SCOPE_HEADERS
    }),
  project: (id: number) =>
    backendApi.get<ContentProjectVO>(`/content/projects/${id}`, { headers: OWN_SCOPE_HEADERS }),
  updateProject: (id: number, data: ContentProjectUpdateInput) =>
    backendApi.put<ContentProjectVO>(`/content/projects/${id}`, data, {
      headers: OWN_SCOPE_HEADERS
    }),
  deleteProject: (id: number) =>
    backendApi.delete<void>(`/content/projects/${id}`, { headers: OWN_SCOPE_HEADERS }),
  projectGraph: (id: number) =>
    backendApi.get<ContentProjectGraphVO>(`/content/projects/${id}/graph`, {
      headers: OWN_SCOPE_HEADERS
    }),
  projectSummary: (id: number) =>
    backendApi.get<ContentProjectSummaryVO>(`/content/projects/${id}/summary`, {
      headers: OWN_SCOPE_HEADERS
    }),
  projectActions: (id: number) =>
    backendApi.get<ContentActionOptionVO[]>(`/content/projects/${id}/actions`, {
      headers: OWN_SCOPE_HEADERS
    }),
  runProjectAction: (id: number, data: ContentActionCommandDTO) =>
    backendApi.post<ContentExecutionRunVO>(`/content/projects/${id}/actions`, data, {
      headers: OWN_SCOPE_HEADERS,
      showError: false
    }),
  updateProjectStatus: (id: number, data: ContentProjectStatusDTO) =>
    backendApi.put<ContentProjectVO>(`/content/projects/${id}/status`, data, {
      headers: OWN_SCOPE_HEADERS
    }),
  projectObjects: (params: ContentProjectObjectParams = {}) =>
    backendApi.get<PageResult<ContentProjectObjectVO>>("/content/project-objects", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  createProjectObject: (data: ContentProjectObjectInput) =>
    backendApi.post<ContentProjectObjectVO>("/content/project-objects", data, {
      headers: OWN_SCOPE_HEADERS
    }),
  updateProjectObject: (id: number, data: Partial<ContentProjectObjectInput>) =>
    backendApi.put<ContentProjectObjectVO>(`/content/project-objects/${id}`, data, {
      headers: OWN_SCOPE_HEADERS
    }),
  deleteProjectObject: (id: number) =>
    backendApi.delete<void>(`/content/project-objects/${id}`, { headers: OWN_SCOPE_HEADERS }),
  projectObjectVersions: (id: number) =>
    backendApi.get<ContentObjectVersionVO[]>(`/content/project-objects/${id}/versions`, {
      headers: OWN_SCOPE_HEADERS
    }),
  adoptProjectObjectVersion: (objectId: number, versionId: number) =>
    backendApi.post<ContentProjectObjectVO>(
      `/content/project-objects/${objectId}/versions/${versionId}/_adopt`,
      undefined,
      { headers: OWN_SCOPE_HEADERS }
    ),
  rejectProjectObjectVersion: (objectId: number, versionId: number) =>
    backendApi.post<ContentObjectVersionVO>(
      `/content/project-objects/${objectId}/versions/${versionId}/_reject`,
      undefined,
      { headers: OWN_SCOPE_HEADERS }
    ),
  objectVersions: (params: ContentObjectVersionParams = {}) =>
    backendApi.get<PageResult<ContentObjectVersionVO>>("/content/object-versions", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  projectRelations: (params: ContentProjectRelationParams = {}) =>
    backendApi.get<PageResult<ContentProjectRelationVO>>("/content/project-relations", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  createProjectRelation: (data: ContentProjectRelationInput) =>
    backendApi.post<ContentProjectRelationVO>("/content/project-relations", data, {
      headers: OWN_SCOPE_HEADERS
    }),
  deleteProjectRelation: (id: number) =>
    backendApi.delete<void>(`/content/project-relations/${id}`, { headers: OWN_SCOPE_HEADERS }),
  projectProfileRefs: (params: ContentPageParams & { projectId?: number } = {}) =>
    backendApi.get<PageResult<ContentProjectProfileRefVO>>("/content/project-profile-refs", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  createProjectProfileRef: (data: ContentProjectProfileRefInput) =>
    backendApi.post<ContentProjectProfileRefVO>("/content/project-profile-refs", data, {
      headers: OWN_SCOPE_HEADERS
    }),
  deleteProjectProfileRef: (id: number) =>
    backendApi.delete<void>(`/content/project-profile-refs/${id}`, { headers: OWN_SCOPE_HEADERS }),
  executionRuns: (params: ContentExecutionRunParams = {}) =>
    backendApi.get<PageResult<ContentExecutionRunVO>>("/content/execution-runs", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  cancelExecutionRun: (id: number) =>
    backendApi.post<ContentExecutionRunVO>(`/content/execution-runs/${id}/_cancel`, undefined, {
      headers: OWN_SCOPE_HEADERS
    }),
  retryExecutionRun: (id: number) =>
    backendApi.post<ContentExecutionRunVO>(`/content/execution-runs/${id}/_retry`, undefined, {
      headers: OWN_SCOPE_HEADERS
    }),
  snippets: (params: ContentSnippetParams = {}) =>
    backendApi.get<PageResult<ContentSnippetVO>>("/content/snippets", {
      params: pageParams(params),
      headers: OWN_SCOPE_HEADERS
    }),
  snippet: (id: number) =>
    backendApi.get<ContentSnippetVO>(`/content/snippets/${id}`, { headers: OWN_SCOPE_HEADERS }),
  createSnippet: (data: ContentSnippetInput) =>
    backendApi.post<ContentSnippetVO>("/content/snippets", data, { headers: OWN_SCOPE_HEADERS }),
  updateSnippet: (id: number, data: ContentSnippetInput) =>
    backendApi.put<ContentSnippetVO>(`/content/snippets/${id}`, data, {
      headers: OWN_SCOPE_HEADERS
    }),
  deleteSnippet: (id: number) =>
    backendApi.delete<void>(`/content/snippets/${id}`, { headers: OWN_SCOPE_HEADERS })
}

const KEYS = {
  all: ["content-studio"] as const,
  projectTypes: (params: ContentPageParams) => ["content-studio", "project-types", params] as const,
  brandProfiles: (params: ContentPageParams) =>
    ["content-studio", "brand-profiles", params] as const,
  channelSpecs: (params: ContentPageParams) => ["content-studio", "channel-specs", params] as const,
  projects: (params: ContentProjectParams) => ["content-studio", "projects", params] as const,
  project: (id: number) => ["content-studio", "projects", id] as const,
  graph: (id: number) => ["content-studio", "projects", id, "graph"] as const,
  summary: (id: number) => ["content-studio", "projects", id, "summary"] as const,
  actions: (id: number) => ["content-studio", "projects", id, "actions"] as const,
  objects: (params: ContentProjectObjectParams) =>
    ["content-studio", "project-objects", params] as const,
  versions: (objectId: number) =>
    ["content-studio", "project-objects", objectId, "versions"] as const,
  executionRuns: (params: ContentExecutionRunParams) =>
    ["content-studio", "execution-runs", params] as const,
  snippets: (params: ContentSnippetParams) => ["content-studio", "snippets", params] as const
}

export function useContentProjectTypes(params: ContentPageParams = {}) {
  return useQuery({
    queryKey: KEYS.projectTypes(params),
    queryFn: () => contentStudioApi.projectTypes(params)
  })
}
export function useContentBrandProfiles(params: ContentPageParams = {}) {
  return useQuery({
    queryKey: KEYS.brandProfiles(params),
    queryFn: () => contentStudioApi.brandProfiles(params)
  })
}
export function useContentChannelSpecs(params: ContentPageParams = {}) {
  return useQuery({
    queryKey: KEYS.channelSpecs(params),
    queryFn: () => contentStudioApi.channelSpecs(params)
  })
}
export function useContentProjects(params: ContentProjectParams = {}) {
  return useQuery({
    queryKey: KEYS.projects(params),
    queryFn: () => contentStudioApi.projects(params)
  })
}
export function useContentProject(id: number | null) {
  return useQuery({
    queryKey: KEYS.project(id ?? 0),
    queryFn: () => contentStudioApi.project(id as number),
    enabled: id !== null
  })
}
export function useContentProjectGraph(id: number | null) {
  return useQuery({
    queryKey: KEYS.graph(id ?? 0),
    queryFn: () => contentStudioApi.projectGraph(id as number),
    enabled: id !== null
  })
}
export function useContentProjectSummary(id: number | null) {
  return useQuery({
    queryKey: KEYS.summary(id ?? 0),
    queryFn: () => contentStudioApi.projectSummary(id as number),
    enabled: id !== null
  })
}
export function useContentProjectActions(id: number | null) {
  return useQuery({
    queryKey: KEYS.actions(id ?? 0),
    queryFn: () => contentStudioApi.projectActions(id as number),
    enabled: id !== null
  })
}
export function useContentProjectObjects(params: ContentProjectObjectParams = {}) {
  return useQuery({
    queryKey: KEYS.objects(params),
    queryFn: () => contentStudioApi.projectObjects(params)
  })
}
export function useContentObjectVersions(objectId: number | null) {
  return useQuery({
    queryKey: KEYS.versions(objectId ?? 0),
    queryFn: () => contentStudioApi.projectObjectVersions(objectId as number),
    enabled: objectId !== null
  })
}
export function useContentExecutionRuns(params: ContentExecutionRunParams = {}, enabled = true) {
  return useQuery({
    queryKey: KEYS.executionRuns(params),
    queryFn: () => contentStudioApi.executionRuns(params),
    enabled
  })
}
export function useContentSnippets(params: ContentSnippetParams = {}) {
  return useQuery({
    queryKey: KEYS.snippets(params),
    queryFn: () => contentStudioApi.snippets(params)
  })
}

function invalidateExecutionState(queryClient: QueryClient, projectId: number, objectId?: number) {
  queryClient.invalidateQueries({ queryKey: KEYS.graph(projectId) })
  queryClient.invalidateQueries({ queryKey: KEYS.summary(projectId) })
  queryClient.invalidateQueries({ queryKey: ["content-studio", "execution-runs"] })
  if (objectId !== undefined) {
    queryClient.invalidateQueries({ queryKey: KEYS.versions(objectId) })
  }
}

export function useRunContentAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, data }: { projectId: number; data: ContentActionCommandDTO }) =>
      contentStudioApi.runProjectAction(projectId, data),
    onSuccess: (run) => invalidateExecutionState(queryClient, run.projectId, run.objectId)
  })
}

export function useAdoptContentObjectVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ objectId, versionId }: { objectId: number; versionId: number }) =>
      contentStudioApi.adoptProjectObjectVersion(objectId, versionId),
    onSuccess: (object) => invalidateExecutionState(queryClient, object.projectId, object.id)
  })
}

export function useRejectContentObjectVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ objectId, versionId }: { objectId: number; versionId: number }) =>
      contentStudioApi.rejectProjectObjectVersion(objectId, versionId),
    onSuccess: (version) =>
      invalidateExecutionState(queryClient, version.projectId, version.objectId)
  })
}

export function useCancelContentExecutionRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => contentStudioApi.cancelExecutionRun(id),
    onSuccess: (run) => invalidateExecutionState(queryClient, run.projectId, run.objectId)
  })
}

export function useRetryContentExecutionRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => contentStudioApi.retryExecutionRun(id),
    onSuccess: (run) => invalidateExecutionState(queryClient, run.projectId, run.objectId)
  })
}

export function useMaterializeContentProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: ContentProjectMaterializeDTO) => contentStudioApi.materializeProject(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEYS.all })
  })
}
export function useUpdateContentProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: ContentProjectUpdateInput }) =>
      contentStudioApi.updateProject(id, data),
    onSuccess: (_project, { id }) => {
      queryClient.invalidateQueries({ queryKey: KEYS.project(id) })
      queryClient.invalidateQueries({ queryKey: ["content-studio", "projects"] })
    }
  })
}
export function useDeleteContentProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => contentStudioApi.deleteProject(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["content-studio", "projects"] })
  })
}
export function useUpdateContentProjectStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: ContentProjectStatusDTO }) =>
      contentStudioApi.updateProjectStatus(id, data),
    onSuccess: (_project, { id }) => {
      queryClient.invalidateQueries({ queryKey: KEYS.project(id) })
      queryClient.invalidateQueries({ queryKey: ["content-studio", "projects"] })
    }
  })
}
export function useUpsertContentProjectObject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id?: number; data: ContentProjectObjectInput }) =>
      id === undefined
        ? contentStudioApi.createProjectObject(data)
        : contentStudioApi.updateProjectObject(id, data),
    onSuccess: (object) => {
      queryClient.invalidateQueries({ queryKey: ["content-studio", "project-objects"] })
      queryClient.invalidateQueries({ queryKey: KEYS.graph(object.projectId) })
      queryClient.invalidateQueries({ queryKey: KEYS.summary(object.projectId) })
    }
  })
}
