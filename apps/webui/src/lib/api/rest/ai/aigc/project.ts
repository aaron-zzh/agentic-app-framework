/**
 * 唯一 AIGC Project 聚合 API：物化、图谱、对象版本与交付生命周期。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import type { AigcProductionMode, AigcProjectTypeCode } from "./configuration"

export type AigcProjectStatus =
  | "CONFIGURING"
  | "MATERIALIZED"
  | "CREATING"
  | "EXECUTING"
  | "ADOPTING"
  | "REVIEWING"
  | "DELIVERING"
  | "COMPLETED"
  | "ARCHIVED"
export type AigcProjectCoverMode = "NONE" | "UPLOAD" | "AI_GENERATE"
export type AigcProjectCoverStatus = "NONE" | "READY" | "PENDING" | "FAILED"
export type AigcProjectCoverOperation = "UPLOAD" | "AI_GENERATE" | "REMOVE"
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
export type AigcContractRole = "REQUIRED" | "OPTIONAL" | "EXCLUDED"
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
  description: string | null
  projectTypeCode: AigcProjectTypeCode
  blueprintCode: string | null
  blueprintVersion: string | null
  domainExtensionCode: string | null
  domainExtensionVersion: string | null
  productionMode: AigcProductionMode
  generationMode: AigcGenerationMode
  status: AigcProjectStatus
  brief: string | null
  prompt: string | null
  coverMediaVersionId: number | null
  coverStatus: AigcProjectCoverStatus
  coverExecutionRunId: number | null
  configSnapshotId: number | null
  graphRevision: number
  primaryBrandProfileId: number | null
  assistantId: number | null
  budgetLimit: number | null
  costUsed: number
  lastActiveTime: string | null
  createTime: string
  updateTime: string
}

export interface AigcProjectView {
  id: number
  orgId?: number
  workspaceId?: number | null
  name: string
  lifecycleStage: AigcProjectStatus
  version: number
  configurationSnapshotId: number
  projectTypeCode?: AigcProjectTypeCode
  domainExtensionCode?: string | null
  productionMode?: AigcProductionMode
  generationMode?: AigcGenerationMode
  channelSpecVersionIds?: number[]
  budgetLimit?: number | null
  costUsed?: number
  description?: string | null
  brief?: string | null
  coverMediaVersionId?: number | null
  userId?: number
}

export interface AigcProjectMaterializeResult {
  project: AigcProjectView
  coverStatus: AigcProjectCoverStatus
  runId: number | null
}

export interface AigcProjectObject {
  id: number
  version: number
  projectId: number
  objectType: AigcObjectType
  stableKey: string
  blueprintTemplateKey?: string
  instanceNo?: number
  contractRole?: AigcContractRole
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

export interface AigcProjectDocumentRef {
  id: number
  objectId?: number
  documentVersionId: number
  role: string
  sortOrder: number
}

export interface AigcProjectMediaRef {
  id: number
  objectId?: number
  objectVersionId?: number
  mediaVersionId: number
  role: string
  sortOrder: number
  adoptionStatus: AigcObjectVersionStatus
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

export interface AigcObjectVersionComparisonItem {
  objectVersionId: number
  status: AigcObjectVersionStatus
  content: Record<string, unknown>
  documentVersionId?: number
  mediaVersionIds: number[]
  sourceExecutionRunId?: number
  creditCost?: number
  createdTime: string
}

export interface AigcObjectVersionComparison {
  left: AigcObjectVersionComparisonItem
  right: AigcObjectVersionComparisonItem
}

export interface AigcDeliverableSlotEvidence {
  stableKey: string
  objectId: number
  contractRole: AigcContractRole
  adoptedObjectVersionId?: number
  validationStatus: string
  activeExecutionReservationId?: number
  activeExecutionRunId?: number
}

export interface AigcDeliverableSetCompletion {
  setObjectId: number
  graphRevision: number
  includedProjectObjectIds: number[]
  complete: boolean
  slots: AigcDeliverableSlotEvidence[]
  blockers: string[]
  evidenceHash: string
}

export type AigcReviewStatus = "PENDING" | "APPROVED" | "RETURNED" | "STALE"
export interface AigcReview {
  reviewObjectId: number
  reviewVersion: number
  projectId: number
  subjectObjectId: number
  subjectObjectVersionId: number
  evidenceHash: string
  status: AigcReviewStatus
  comment?: string
  staleReason?: string
  submittedTime: string
  decidedTime?: string
}

export type AigcPublicationPolicy =
  | "NONE"
  | "OPTIONAL"
  | "AT_LEAST_ONE_SUCCESS"
  | "ALL_SELECTED_CHANNELS"
export interface AigcCompletionEvaluation {
  publicationPolicy: AigcPublicationPolicy
  satisfied: boolean
  activeWorkCount: number
  requiredChannelSpecVersionIds: number[]
  succeededChannelSpecVersionIds: number[]
  blockers: string[]
}

export interface AigcSlotCountOverride {
  templateKey: string
  requestedCount: number
}

export interface AigcProjectMaterializeInput {
  name: string
  description?: string
  projectTypeCode: AigcProjectTypeCode
  blueprintVersionId: number
  domainExtensionVersionId?: number
  brandProfileVersionIds?: number[]
  channelSpecVersionIds?: number[]
  documentVersionIds?: number[]
  productionMode: AigcProductionMode
  budgetTier?: string
  qualityTier?: string
  slotOverrides?: AigcSlotCountOverride[]
  briefJson?: string
  coverMode: AigcProjectCoverMode
  coverFileId?: number
  coverPrompt?: string
  coverIdempotencyKey?: string
}

export interface AigcProjectCoverPatchInput {
  operation: AigcProjectCoverOperation
  fileId?: number
  prompt?: string
  idempotencyKey?: string
}

export interface AigcProjectUpdateInput {
  name?: string
  description?: string | null
  brief?: string | null
  cover?: AigcProjectCoverPatchInput
  expectedVersion: number
}

export interface AigcProjectParams {
  pageNo?: number
  pageSize?: number
  status?: AigcProjectStatus
  projectTypeCode?: AigcProjectTypeCode
  productionMode?: AigcProductionMode
}

export interface AigcVersionAdoptInput {
  projectId: number
  objectId: number
  versionId: number
  expectedAdoptedVersionId?: number
  expectedProjectVersion: number
  confirmedReplacement: boolean
  reason?: string
  idempotencyKey: string
}

export interface AigcVersionRejectInput {
  projectId: number
  objectId: number
  versionId: number
  expectedProjectVersion: number
  reason: string
  idempotencyKey: string
}

export interface AigcDeliverableSetEvaluateInput {
  projectId: number
  setObjectId: number
  includedOptionalObjectIds: number[]
  expectedGraphRevision: number
}

export interface AigcDeliverableSetFreezeInput extends AigcDeliverableSetEvaluateInput {
  expectedEvidenceHash: string
  expectedProjectVersion: number
  idempotencyKey: string
}

export interface AigcReviewSubmitInput {
  projectId: number
  setObjectId: number
  manifestObjectVersionId: number
  expectedProjectVersion: number
  idempotencyKey: string
}

export interface AigcReviewDecisionInput {
  projectId: number
  reviewObjectId: number
  expectedManifestObjectVersionId: number
  expectedProjectVersion: number
  expectedReviewVersion: number
  comment?: string
  idempotencyKey: string
}

export interface AigcLifecycleInput {
  projectId: number
  action: "complete" | "archive"
  expectedProjectVersion: number
  reason: string
  idempotencyKey: string
}

export interface AigcProjectDocumentRefInput {
  projectId: number
  objectId?: number
  documentVersionId: number
  role?: string
  sortOrder?: number
  expectedProjectVersion: number
}

export interface AigcProjectDocumentDetachInput {
  projectId: number
  refId: number
  expectedProjectVersion: number
}

export const aigcProjectApi = {
  projects: (params: AigcProjectParams = {}) =>
    backendApi.get<PageResult<AigcProject>>("/aigc/projects", {
      params: { pageNo: 1, pageSize: 50, ...params }
    }),
  project: (id: number) => backendApi.get<AigcProject>(`/aigc/projects/${id}`),
  materialize: (data: AigcProjectMaterializeInput) =>
    backendApi.post<AigcProjectMaterializeResult>("/aigc/projects/_materialize", data),
  update: (id: number, data: AigcProjectUpdateInput) =>
    backendApi.put<AigcProject>(`/aigc/projects/${id}`, data),
  graph: (id: number) => backendApi.get<AigcProjectGraph>(`/aigc/projects/${id}/graph`),
  summary: (id: number) => backendApi.get<AigcProjectSummary>(`/aigc/projects/${id}/summary`),
  channelRefs: (id: number) =>
    backendApi.get<AigcProjectChannelRef[]>(`/aigc/projects/${id}/channel-refs`),
  documentRefs: (id: number) =>
    backendApi.get<AigcProjectDocumentRef[]>(`/aigc/projects/${id}/document-refs`),
  mediaRefs: (id: number) =>
    backendApi.get<AigcProjectMediaRef[]>(`/aigc/projects/${id}/media-refs`),
  attachDocument: ({ projectId, ...data }: AigcProjectDocumentRefInput) =>
    backendApi.post<AigcProjectDocumentRef>(`/aigc/projects/${projectId}/document-refs`, data),
  detachDocument: ({ projectId, refId, expectedProjectVersion }: AigcProjectDocumentDetachInput) =>
    backendApi.delete<void>(`/aigc/projects/${projectId}/document-refs/${refId}`, {
      params: { expectedProjectVersion }
    }),
  versions: (projectId: number, objectId: number) =>
    backendApi.get<AigcObjectVersion[]>(`/aigc/projects/${projectId}/objects/${objectId}/versions`),
  compareVersions: (
    projectId: number,
    objectId: number,
    leftVersionId: number,
    rightVersionId: number
  ) =>
    backendApi.get<AigcObjectVersionComparison>(
      `/aigc/projects/${projectId}/objects/${objectId}/versions/_compare`,
      {
        params: { leftVersionId, rightVersionId }
      }
    ),
  adoptVersion: (input: AigcVersionAdoptInput) =>
    backendApi.post<AigcObjectVersionView>(
      `/aigc/projects/${input.projectId}/objects/${input.objectId}/versions/${input.versionId}/_adopt`,
      {
        expectedAdoptedVersionId: input.expectedAdoptedVersionId,
        expectedProjectVersion: input.expectedProjectVersion,
        confirmedReplacement: input.confirmedReplacement,
        reason: input.reason,
        idempotencyKey: input.idempotencyKey
      },
      { showError: false }
    ),
  rejectVersion: (input: AigcVersionRejectInput) =>
    backendApi.post<AigcObjectVersionView>(
      `/aigc/projects/${input.projectId}/objects/${input.objectId}/versions/${input.versionId}/_reject`,
      {
        expectedProjectVersion: input.expectedProjectVersion,
        reason: input.reason,
        idempotencyKey: input.idempotencyKey
      },
      { showError: false }
    ),
  evaluateDeliverableSet: (input: AigcDeliverableSetEvaluateInput) =>
    backendApi.post<AigcDeliverableSetCompletion>(
      `/aigc/projects/${input.projectId}/deliverable-sets/${input.setObjectId}/_evaluate`,
      {
        includedOptionalObjectIds: input.includedOptionalObjectIds,
        expectedGraphRevision: input.expectedGraphRevision
      }
    ),
  freezeDeliverableSet: (input: AigcDeliverableSetFreezeInput) =>
    backendApi.post<AigcObjectVersionView>(
      `/aigc/projects/${input.projectId}/deliverable-sets/${input.setObjectId}/_freeze`,
      {
        includedOptionalObjectIds: input.includedOptionalObjectIds,
        expectedGraphRevision: input.expectedGraphRevision,
        expectedEvidenceHash: input.expectedEvidenceHash,
        expectedProjectVersion: input.expectedProjectVersion,
        idempotencyKey: input.idempotencyKey
      },
      { showError: false }
    ),
  reviews: (projectId: number) =>
    backendApi.get<AigcReview[]>(`/aigc/projects/${projectId}/reviews`),
  submitReview: (input: AigcReviewSubmitInput) =>
    backendApi.post<AigcReview>(`/aigc/projects/${input.projectId}/reviews`, {
      setObjectId: input.setObjectId,
      manifestObjectVersionId: input.manifestObjectVersionId,
      expectedProjectVersion: input.expectedProjectVersion,
      idempotencyKey: input.idempotencyKey
    }),
  approveReview: (input: AigcReviewDecisionInput) =>
    backendApi.post<AigcReview>(
      `/aigc/projects/${input.projectId}/reviews/${input.reviewObjectId}/_approve`,
      {
        expectedManifestObjectVersionId: input.expectedManifestObjectVersionId,
        expectedProjectVersion: input.expectedProjectVersion,
        expectedReviewVersion: input.expectedReviewVersion,
        comment: input.comment,
        idempotencyKey: input.idempotencyKey
      },
      { showError: false }
    ),
  returnReview: (input: AigcReviewDecisionInput) =>
    backendApi.post<AigcReview>(
      `/aigc/projects/${input.projectId}/reviews/${input.reviewObjectId}/_return`,
      {
        expectedManifestObjectVersionId: input.expectedManifestObjectVersionId,
        expectedProjectVersion: input.expectedProjectVersion,
        expectedReviewVersion: input.expectedReviewVersion,
        comment: input.comment,
        idempotencyKey: input.idempotencyKey
      },
      { showError: false }
    ),
  completionEvidence: (projectId: number) =>
    backendApi.get<AigcCompletionEvaluation>(`/aigc/projects/${projectId}/completion-evidence`),
  lifecycle: (input: AigcLifecycleInput) =>
    backendApi.post<AigcProjectView>(`/aigc/projects/${input.projectId}/_${input.action}`, {
      expectedProjectVersion: input.expectedProjectVersion,
      idempotencyKey: input.idempotencyKey,
      reason: input.reason
    })
}

export const aigcProjectKeys = {
  all: ["aigc.project"] as const,
  list: (params: AigcProjectParams) => ["aigc.project", "list", params] as const,
  detail: (id: number) => ["aigc.project", "detail", id] as const,
  graph: (id: number) => ["aigc.project", "graph", id] as const,
  summary: (id: number) => ["aigc.project", "summary", id] as const,
  channelRefs: (id: number) => ["aigc.project", "channel-refs", id] as const,
  documentRefs: (id: number) => ["aigc.project", "document-refs", id] as const,
  mediaRefs: (id: number) => ["aigc.project", "media-refs", id] as const,
  versions: (projectId: number, objectId: number) =>
    ["aigc.project", "versions", projectId, objectId] as const,
  comparison: (projectId: number, objectId: number, left: number, right: number) =>
    ["aigc.project", "comparison", projectId, objectId, left, right] as const,
  reviews: (projectId: number) => ["aigc.project", "reviews", projectId] as const,
  completionEvidence: (projectId: number) =>
    ["aigc.project", "completion-evidence", projectId] as const
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
    enabled: id !== null,
    refetchInterval: (query) => (query.state.data?.coverStatus === "PENDING" ? 3000 : false)
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
export function useAigcProjectDocumentRefs(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.documentRefs(id ?? 0),
    queryFn: () => aigcProjectApi.documentRefs(id as number),
    enabled: id !== null
  })
}
export function useAigcProjectMediaRefs(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.mediaRefs(id ?? 0),
    queryFn: () => aigcProjectApi.mediaRefs(id as number),
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
export function useAigcVersionComparison(
  projectId: number,
  objectId: number,
  left: number | null,
  right: number | null
) {
  return useQuery({
    queryKey: aigcProjectKeys.comparison(projectId, objectId, left ?? 0, right ?? 0),
    queryFn: () =>
      aigcProjectApi.compareVersions(projectId, objectId, left as number, right as number),
    enabled: left !== null && right !== null && left !== right
  })
}
export function useAigcReviews(projectId: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.reviews(projectId ?? 0),
    queryFn: () => aigcProjectApi.reviews(projectId as number),
    enabled: projectId !== null
  })
}
export function useAigcCompletionEvidence(projectId: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.completionEvidence(projectId ?? 0),
    queryFn: () => aigcProjectApi.completionEvidence(projectId as number),
    enabled: projectId !== null
  })
}

export function invalidateAigcProject(
  queryClient: ReturnType<typeof useQueryClient>,
  projectId: number
) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.graph(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.reviews(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.completionEvidence(projectId) }),
    queryClient.invalidateQueries({ queryKey: ["aigc.project", "versions", projectId] }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.all })
  ])
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
    onSuccess: (project) => invalidateAigcProject(queryClient, project.id)
  })
}
function useVersionMutation<T extends AigcVersionAdoptInput | AigcVersionRejectInput>(
  mutationFn: (input: T) => Promise<AigcObjectVersionView>
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: (_data, _error, input) => {
      invalidateAigcProject(queryClient, input.projectId)
      queryClient.invalidateQueries({
        queryKey: aigcProjectKeys.versions(input.projectId, input.objectId)
      })
    }
  })
}
export function useAdoptAigcObjectVersion() {
  return useVersionMutation(aigcProjectApi.adoptVersion)
}
export function useRejectAigcObjectVersion() {
  return useVersionMutation(aigcProjectApi.rejectVersion)
}
export function useEvaluateAigcDeliverableSet() {
  return useMutation({ mutationFn: aigcProjectApi.evaluateDeliverableSet })
}
export function useFreezeAigcDeliverableSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.freezeDeliverableSet,
    onSettled: (_value, _error, input) => invalidateAigcProject(queryClient, input.projectId)
  })
}
export function useSubmitAigcReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.submitReview,
    onSuccess: (_value, input) => invalidateAigcProject(queryClient, input.projectId)
  })
}
function useReviewMutation(mutationFn: (input: AigcReviewDecisionInput) => Promise<AigcReview>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: (_value, _error, input) => invalidateAigcProject(queryClient, input.projectId)
  })
}
export function useApproveAigcProjectReview() {
  return useReviewMutation(aigcProjectApi.approveReview)
}
export function useReturnAigcProjectReview() {
  return useReviewMutation(aigcProjectApi.returnReview)
}
export function useAigcProjectLifecycle() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.lifecycle,
    onSuccess: (_project, input) => invalidateAigcProject(queryClient, input.projectId)
  })
}
export function useAttachAigcProjectDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.attachDocument,
    onSuccess: async (_ref, input) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: aigcProjectKeys.documentRefs(input.projectId) }),
        invalidateAigcProject(queryClient, input.projectId)
      ])
  })
}
export function useDetachAigcProjectDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.detachDocument,
    onSuccess: async (_result, input) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: aigcProjectKeys.documentRefs(input.projectId) }),
        invalidateAigcProject(queryClient, input.projectId)
      ])
  })
}

export interface AigcProjectObjectAppendInput {
  projectId: number
  parentObjectId?: number
  blueprintTemplateKey?: string
  objectType: AigcObjectType
  displayName: string
  contractRole: AigcContractRole
  orderNo?: number
  schemaVersion?: string
  payloadJson?: string
  expectedProjectVersion: number
}

export interface AigcProjectObjectContractInput {
  projectId: number
  objectId: number
  contractRole: AigcContractRole
  expectedProjectVersion: number
}

export interface AigcProjectObjectRemoveInput {
  projectId: number
  objectId: number
  expectedProjectVersion: number
  reason?: string
}

/** 项目对象结构写 API。 */
export const aigcProjectStructureApi = {
  appendObject: ({ projectId, ...data }: AigcProjectObjectAppendInput) =>
    backendApi.post<AigcProjectObject>(`/aigc/projects/${projectId}/objects`, data, {
      showError: false
    }),
  updateObjectContract: ({ projectId, objectId, ...data }: AigcProjectObjectContractInput) =>
    backendApi.put<AigcProjectObject>(
      `/aigc/projects/${projectId}/objects/${objectId}/contract`,
      data,
      { showError: false }
    ),
  removeObject: ({ projectId, objectId, ...data }: AigcProjectObjectRemoveInput) =>
    backendApi.delete<AigcProjectObject>(`/aigc/projects/${projectId}/objects/${objectId}`, {
      data,
      showError: false
    })
}

function useProjectStructureMutation<TInput extends { projectId: number }>(
  mutationFn: (input: TInput) => Promise<AigcProjectObject>
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: (_object, _error, input) => {
      void invalidateAigcProject(queryClient, input.projectId)
    }
  })
}

export function useAppendAigcProjectObject() {
  return useProjectStructureMutation(aigcProjectStructureApi.appendObject)
}

export function useUpdateAigcProjectObjectContract() {
  return useProjectStructureMutation(aigcProjectStructureApi.updateObjectContract)
}

export function useRemoveAigcProjectObject() {
  return useProjectStructureMutation(aigcProjectStructureApi.removeObject)
}
