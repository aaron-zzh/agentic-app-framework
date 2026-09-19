/**
 * 唯一 AIGC Project 聚合 API：物化、图谱、对象版本与交付生命周期。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ApiError } from "../../../errors"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import type {
  AigcProductionMode,
  AigcProjectInitializationFieldKey,
  AigcProjectTypeCode
} from "./configuration"

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
export type AigcObjectStatus = "EMPTY" | "CANDIDATE_AVAILABLE" | "ADOPTED"
export type AigcObjectVersionStatus = "candidate" | "adopted" | "rejected" | "superseded"
export type AigcObjectSource = "blueprint" | "user" | "assistant" | "workflow" | "import"
export type AigcContractRole = "REQUIRED" | "OPTIONAL" | "EXCLUDED"
export type AigcSemanticRole =
  | "STRUCTURE"
  | "WORKING_ARTIFACT"
  | "DELIVERABLE"
  | "EVIDENCE"
  | "REFERENCE"
export type AigcOutputContract =
  | "INLINE_OBJECT_VERSION"
  | "DOCUMENT_VERSION"
  | "MEDIA_VERSION"
  | "COMPOSITE_MANIFEST"
  | "REFERENCE_ONLY"
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
  background: string | null
  targetAudience: string | null
  requirements: string[]
  constraints: string[]
  keyFacts: string[]
  keyMessages: string[]
  styleGuidance: string | null
  successCriteria: string[]
  deliveryRequirements: Record<string, unknown>
  locale: string | null
  additionalContext: string | null
  projectTypeCode: AigcProjectTypeCode
  blueprintCode: string | null
  blueprintVersion: string | null
  productionMode: AigcProductionMode
  generationMode: AigcGenerationMode
  status: AigcProjectStatus
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

export interface AigcProjectConversationContextBase {
  projectId: number
  projectVersion: number
}

export interface AigcProjectConversationReadyContext
  extends AigcProjectConversationContextBase {
  state: "READY"
  assistantRecordId: number
  assistantId: string
  conversationRecordId: number
  threadId: string
}

export interface AigcProjectConversationBindingRequiredContext
  extends AigcProjectConversationContextBase {
  state: "NEEDS_ASSISTANT_BINDING"
  assistantRecordId: null
  assistantId: null
  conversationRecordId: null
  threadId: null
}

export type AigcProjectConversationContext =
  | AigcProjectConversationReadyContext
  | AigcProjectConversationBindingRequiredContext

export interface AigcProjectConversationBindInput {
  projectId: number
  expectedProjectVersion: number
  assistantId: string
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
  productionMode?: AigcProductionMode
  generationMode?: AigcGenerationMode
  channelSpecVersionIds?: number[]
  budgetLimit?: number | null
  costUsed?: number
  description?: string | null
  background?: string | null
  targetAudience?: string | null
  requirements?: string[]
  constraints?: string[]
  keyFacts?: string[]
  keyMessages?: string[]
  styleGuidance?: string | null
  successCriteria?: string[]
  deliveryRequirements?: Record<string, unknown>
  locale?: string | null
  additionalContext?: string | null
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
  blueprintTemplateKey: string | null
  instanceNo: number
  contractRole: AigcContractRole
  semanticRole: AigcSemanticRole
  outputContract: AigcOutputContract
  defaultActionKey: string | null
  parentId: number | null
  sortOrder: number
  title: string | null
  status: AigcObjectStatus
  source: AigcObjectSource
  schemaVersion: string | null
  entityResource: string | null
  entityId: number | null
  adoptedVersionId: number | null
  summary: string | null
  payload: Record<string, unknown> | null
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

export type AigcProjectDocumentReferenceType = "PROJECT_DOCUMENT" | "EXTERNAL_REFERENCE"
export type AigcProjectDocumentContextMode = "FULL_TEXT" | "SUMMARY" | "RAG" | "AUTO" | "NONE"

export interface DocumentSemanticTopic {
  name: string
  normalizedName: string | null
  confidence: number | null
  source: string
}

export interface AigcProjectDocumentRef {
  id: number
  objectId?: number
  referenceType: AigcProjectDocumentReferenceType
  documentId: number
  fixedDocumentVersionId: number | null
  effectiveDocumentVersionId: number
  currentDocumentVersionId: number
  effectiveVersionNo: number
  currentVersionNo: number
  outdated: boolean
  title: string
  fieldKey: string | null
  instanceNo: number
  selectedDocumentVersionId: number | null
  source: string
  contextNote: string | null
  contextMode: AigcProjectDocumentContextMode
  role: string
  sortOrder: number
  summary: string | null
  topics: DocumentSemanticTopic[]
  language: string | null
  semanticGenerationStatus: "PENDING" | "READY" | "FAILED" | null
  semanticManualOverride: boolean
  semanticMetadataVersion: number | null
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

export type AigcObjectReadinessState =
  | "SATISFIED"
  | "READY"
  | "BLOCKED_BY_DEPENDENCY"
  | "BLOCKED_BY_POLICY"
  | "ACTIVE_RUN"
  | "PENDING_CANDIDATE"
  | "AWAITING_CONFIRMATION"

export interface AigcFrozenInputVersionRef {
  projectObjectId: number
  objectVersionId: number
  source: string
}

export interface AigcProjectSlotTemplateSpec {
  templateKey: string
  objectType: AigcObjectType
  displayNamePattern: string | null
  parentTemplateKey: string | null
  userAddable: boolean
  defaultContractRole: AigcContractRole
  semanticRole: AigcSemanticRole
  outputContract: AigcOutputContract
}

export interface AigcProjectConfigSnapshot {
  id: number
  revisionNo: number
  sourceType: "CATALOG" | "ASSISTANT_GENERATED"
  sourceRef: string
  snapshotHash: string
  snapshot: {
    slotTemplates: AigcProjectSlotTemplateSpec[]
  }
}

export interface AigcObjectReadiness {
  projectObjectId: number
  actionKey: string | null
  state: AigcObjectReadinessState
  dependencyObjectIds: number[]
  inputVersions: AigcFrozenInputVersionRef[]
  blockedReason: string | null
  allowedActions: string[]
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

export interface AigcProjectProgress {
  projectionVersion: number
  updatedAt: string
  projectId: number
  projectVersion: number
  graphRevision: number
  currentTask: null
  objects: Array<{
    objectId: number
    maturity: AigcObjectStatus
    contractRole: AigcContractRole
    readiness: AigcObjectReadinessState
    credits: {
      estimated: number
      reserved: number
      settled: number
      refunded: number
      netActual: number
      unknown: number | null
      unknownTaskIds: number[]
    } | null
  }>
  readiness: AigcObjectReadiness[]
  executions: Array<{
    executionRunId: number
    projectObjectId: number | null
    actionKey: string
    targetType: string
    status: string
    tasks: Array<{
      taskId: number
      executionRunId: number | null
      taskType: string
      status: string
      mediaVersionId: number | null
      creditTxId: number | null
      failureReason: string | null
    }>
    estimatedCredits: number
    reservedCredits: number
    failureReason: string | null
  }>
  candidates: Array<{
    candidateVersionId: number
    objectId: number
    status: AigcObjectVersionStatus
    executionRunId: number | null
    documentVersionId: number | null
    mediaVersionIds: number[]
  }>
  delivery: {
    completionSatisfied: boolean
    reviewCount: number
    pendingReviewCount: number
    activeWorkCount: number
    publicationCount: number
    blockers: string[]
  }
  credits: {
    estimated: number
    reserved: number
    settled: number
    refunded: number
    netActual: number
    unknown: number | null
    unknownTaskIds: number[]
  }
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
  documentId?: number
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

export interface AigcProjectDocumentInput {
  fieldKey: string
  documentId?: number
  documentVersionId?: number
  contextNote?: string
}

export interface AigcProjectCreationProposalRef {
  draftId: string
  proposalId: string
  proposalVersion: number
  proposalHash: string
}

export interface AigcProjectMaterializeInput {
  name: string
  description?: string
  background?: string
  targetAudience?: string
  requirements?: string[]
  constraints?: string[]
  keyFacts?: string[]
  keyMessages?: string[]
  styleGuidance?: string
  successCriteria?: string[]
  deliveryRequirements?: Record<string, unknown>
  locale?: string
  additionalContext?: string
  sourceType: "CATALOG"
  sourceRef: string
  projectTypeCode: AigcProjectTypeCode
  blueprintVersionId: number
  brandProfileVersionIds?: number[]
  channelSpecVersionIds?: number[]
  documents: AigcProjectDocumentInput[]
  productionMode: AigcProductionMode
  slotOverrides?: AigcSlotCountOverride[]
  coverMode: AigcProjectCoverMode
  coverFileId?: number
  coverPrompt?: string
  coverIdempotencyKey?: string
  approvedCreationProposal?: AigcProjectCreationProposalRef
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
  background?: string | null
  targetAudience?: string | null
  requirements?: string[]
  constraints?: string[]
  keyFacts?: string[]
  keyMessages?: string[]
  styleGuidance?: string | null
  successCriteria?: string[]
  deliveryRequirements?: Record<string, unknown>
  locale?: string | null
  additionalContext?: string | null
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
  expectedAdoptedVersionId: number | null
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

interface AigcProjectDocumentRefInputBase {
  projectId: number
  objectId?: number
  fieldKey?: string
  contextNote?: string
  role?: string
  sortOrder?: number
  expectedProjectVersion: number
}

export type AigcProjectDocumentRefInput =
  | (AigcProjectDocumentRefInputBase & {
      referenceType: "PROJECT_DOCUMENT"
      documentId: number
      documentVersionId?: never
    })
  | (AigcProjectDocumentRefInputBase & {
      referenceType: "EXTERNAL_REFERENCE"
      documentId?: never
      documentVersionId: number
    })

export interface AigcProjectDocumentUpdateToCurrentInput {
  projectId: number
  refId: number
  expectedProjectVersion: number
}

export interface AigcProjectDocumentDetachInput {
  projectId: number
  refId: number
  expectedProjectVersion: number
}

export interface AigcProjectDocumentCandidateInput {
  projectId: number
  refId: number
  title?: string
  content: string
  format?: "MARKDOWN" | "HTML" | "PLAIN_TEXT" | "JSON"
  changeSummary?: string
  expectedDocumentCurrentVersionId: number
  expectedProjectVersion: number
  idempotencyKey: string
}

export interface AigcProjectDocumentDecisionInput {
  projectId: number
  refId: number
  candidateId: number
  expectedProjectVersion: number
  expectedSelectedDocumentVersionId: number
  confirmedReplacement: boolean
  reason?: string
  idempotencyKey: string
}

export interface AigcProjectDocumentDecision {
  id: number
  version: number
  projectDocumentRefId: number
  documentVersionId: number
  documentVersionNo: number
  title: string
  decision: "PENDING" | "ADOPTED" | "REJECTED"
  sourceType: "MANUAL" | "AIGC"
  sourceRef: string | null
  decisionMode: string | null
  decisionReason: string | null
  decidedBy: number | null
  decidedByType: "HUMAN" | "AI" | null
  decidedTime: string | null
  replacedDocumentVersionId: number | null
  createTime: string
}

export interface AigcManualCandidateInput {
  projectId: number
  objectId: number
  expectedProjectVersion: number
  expectedGraphRevision: number
  content: string
  summary?: string
  idempotencyKey: string
}

export interface AigcDocumentCandidateInput extends AigcManualCandidateInput {
  title?: string
  format?: "MARKDOWN" | "HTML" | "PLAIN_TEXT" | "JSON"
}

export type AigcGenerationProposalTargetType =
  | "INITIALIZATION_FIELD"
  | "PROJECT_DOCUMENT"
  | "PROJECT_OBJECT"
  | "PROJECT_COVER"
export type AigcGenerationProposalStatus =
  | "AWAITING_CONFIRMATION"
  | "APPROVED"
  | "SUPERSEDED"
  | "EXPIRED"
export type AigcGenerationProposalConfirmationPolicy = "REQUIRED" | "OPTIONAL"

export interface AigcProjectGenerationProposalItemInput {
  targetType: AigcGenerationProposalTargetType
  targetKey: string
  actionKey?: string
  generationInstruction: string
  dependencyKeys?: string[]
  requiredContextKeys?: string[]
  modelPolicy?: string
  confirmationPolicy?: AigcGenerationProposalConfirmationPolicy
}

export interface AigcProjectGenerationProposalCreateInput {
  projectId: number
  expectedProjectVersion: number
  confirmationPolicy?: AigcGenerationProposalConfirmationPolicy
  expiresAt?: string
  items: AigcProjectGenerationProposalItemInput[]
}

export interface AigcProjectGenerationProposalReviseInput
  extends AigcProjectGenerationProposalCreateInput {
  proposalId: string
  proposalVersion: number
  proposalHash: string
}

export interface AigcProjectGenerationApproveAndStartInput {
  projectId: number
  expectedProjectVersion: number
  proposalId: string
  proposalVersion: number
  proposalHash: string
  approvedTargetKeys: [string]
  approvalIdempotencyKey: string
}

export interface AigcProjectGenerationProposalItem {
  itemKey: string
  targetType: AigcGenerationProposalTargetType
  targetKey: string
  actionKey: string | null
  generationInstruction: string
  instructionHash: string
  dependencyKeys: string[]
  requiredContextKeys: string[]
  modelPolicy: string | null
  estimatedCredits: number
  confirmationPolicy: AigcGenerationProposalConfirmationPolicy
  itemVersion: number
  itemHash: string
}

export type AigcGenerationProposalScopeKind = "PROJECT" | "CREATION_DRAFT"

export interface AigcProjectGenerationProposal {
  scopeKind: AigcGenerationProposalScopeKind
  projectId: number | null
  draftId: string | null
  blueprintVersionId: number | null
  linkedProjectId: number | null
  proposalId: string
  proposalVersion: number
  proposalHash: string
  contextHash: string
  status: AigcGenerationProposalStatus
  confirmationPolicy: AigcGenerationProposalConfirmationPolicy
  estimatedCredits: number
  approvedTargetKeys: string[]
  linkedTaskId: string | null
  expiresAt: string | null
  items: AigcProjectGenerationProposalItem[]
}

export interface AigcProjectCreationDraftProposalItemInput
  extends Omit<AigcProjectGenerationProposalItemInput, "targetType" | "targetKey"> {
  targetType: "INITIALIZATION_FIELD"
  targetKey: AigcProjectInitializationFieldKey
}

export interface AigcProjectCreationDraftProposalCreateInput {
  draftId?: string
  blueprintVersionId: number
  confirmationPolicy?: AigcGenerationProposalConfirmationPolicy
  expiresAt?: string
  items: AigcProjectCreationDraftProposalItemInput[]
}

export interface AigcProjectCreationDraftProposalReviseInput
  extends Omit<AigcProjectCreationDraftProposalCreateInput, "draftId"> {
  draftId: string
  proposalId: string
  proposalVersion: number
  proposalHash: string
}

export interface AigcProjectCreationDraftApproveAndStartInput {
  draftId: string
  blueprintVersionId: number
  proposalId: string
  proposalVersion: number
  proposalHash: string
  approvedTargetKeys: [string]
  approvalIdempotencyKey: string
  assistantId: string
}

export const aigcProjectCreationDraftApi = {
  generationProposals: (draftId: string) =>
    backendApi.get<AigcProjectGenerationProposal[]>(
      `/aigc/project-creation-drafts/${draftId}/generation-proposals`
    ),
  proposeGeneration: (data: AigcProjectCreationDraftProposalCreateInput) =>
    backendApi.post<AigcProjectGenerationProposal>(
      "/aigc/project-creation-drafts/generation-proposals",
      data,
      { showError: false }
    ),
  reviseGenerationProposal: (data: AigcProjectCreationDraftProposalReviseInput) =>
    backendApi.post<AigcProjectGenerationProposal>(
      "/aigc/project-creation-drafts/generation-proposals/_revise",
      data,
      { showError: false }
    ),
  approveAndStartGeneration: (data: AigcProjectCreationDraftApproveAndStartInput) =>
    backendApi.post<AigcProjectGenerationProposal>(
      "/aigc/project-creation-drafts/generation-proposals/_approve-and-start",
      data,
      { showError: false }
    )
}

export const aigcProjectApi = {
  projects: (params: AigcProjectParams = {}) =>
    backendApi.get<PageResult<AigcProject>>("/aigc/projects", {
      params: { pageNo: 1, pageSize: 50, ...params }
    }),
  project: (id: number) => backendApi.get<AigcProject>(`/aigc/projects/${id}`),
  conversationContext: (id: number) =>
    backendApi.get<AigcProjectConversationContext>(`/aigc/projects/${id}/conversation-context`),
  bindConversation: ({ projectId, ...data }: AigcProjectConversationBindInput) =>
    backendApi.post<AigcProjectConversationContext>(
      `/aigc/projects/${projectId}/conversation-context/_bind`,
      data,
      { showError: false }
    ),
  materialize: (data: AigcProjectMaterializeInput) =>
    backendApi.post<AigcProjectMaterializeResult>("/aigc/projects/_materialize", data),
  update: (id: number, data: AigcProjectUpdateInput) =>
    backendApi.put<AigcProject>(`/aigc/projects/${id}`, data),
  graph: (id: number) => backendApi.get<AigcProjectGraph>(`/aigc/projects/${id}/graph`),
  summary: (id: number) => backendApi.get<AigcProjectSummary>(`/aigc/projects/${id}/summary`),
  readiness: (id: number, includedOptionalObjectIds: number[] = []) =>
    backendApi.get<AigcObjectReadiness[]>(`/aigc/projects/${id}/readiness`, {
      params: { includedOptionalObjectIds }
    }),
  progress: (id: number) => backendApi.get<AigcProjectProgress>(`/aigc/projects/${id}/progress`),
  generationProposals: (id: number) =>
    backendApi.get<AigcProjectGenerationProposal[]>(
      `/aigc/projects/${id}/generation-proposals`
    ),
  proposeGeneration: ({ projectId, ...data }: AigcProjectGenerationProposalCreateInput) =>
    backendApi.post<AigcProjectGenerationProposal>(
      `/aigc/projects/${projectId}/generation-proposals`,
      data,
      { showError: false }
    ),
  reviseGenerationProposal: ({
    projectId,
    ...data
  }: AigcProjectGenerationProposalReviseInput) =>
    backendApi.post<AigcProjectGenerationProposal>(
      `/aigc/projects/${projectId}/generation-proposals/_revise`,
      data,
      { showError: false }
    ),
  approveAndStartGeneration: ({
    projectId,
    ...data
  }: AigcProjectGenerationApproveAndStartInput) =>
    backendApi.post<AigcProjectGenerationProposal>(
      `/aigc/projects/${projectId}/generation-proposals/_approve-and-start`,
      data,
      { showError: false }
    ),
  configuration: (id: number) =>
    backendApi.get<AigcProjectConfigSnapshot[]>(`/aigc/projects/${id}/configuration`),
  channelRefs: (id: number) =>
    backendApi.get<AigcProjectChannelRef[]>(`/aigc/projects/${id}/channel-refs`),
  documentRefs: (id: number) =>
    backendApi.get<AigcProjectDocumentRef[]>(`/aigc/projects/${id}/document-refs`),
  mediaRefs: (id: number) =>
    backendApi.get<AigcProjectMediaRef[]>(`/aigc/projects/${id}/media-refs`),
  attachDocument: ({ projectId, ...data }: AigcProjectDocumentRefInput) =>
    backendApi.post<AigcProjectDocumentRef>(`/aigc/projects/${projectId}/document-refs`, data),
  updateDocumentToCurrent: ({
    projectId,
    refId,
    expectedProjectVersion
  }: AigcProjectDocumentUpdateToCurrentInput) =>
    backendApi.post<AigcProjectDocumentRef>(
      `/aigc/projects/${projectId}/document-refs/${refId}/_update-to-current`,
      { expectedProjectVersion }
    ),
  detachDocument: ({ projectId, refId, expectedProjectVersion }: AigcProjectDocumentDetachInput) =>
    backendApi.delete<void>(`/aigc/projects/${projectId}/document-refs/${refId}`, {
      params: { expectedProjectVersion }
    }),
  projectDocumentCandidates: (projectId: number, refId: number) =>
    backendApi.get<AigcProjectDocumentDecision[]>(
      `/aigc/projects/${projectId}/document-refs/${refId}/candidates`
    ),
  recordProjectDocumentCandidate: ({
    projectId,
    refId,
    ...data
  }: AigcProjectDocumentCandidateInput) =>
    backendApi.post<AigcProjectDocumentDecision>(
      `/aigc/projects/${projectId}/document-refs/${refId}/candidates`,
      data
    ),
  adoptProjectDocumentCandidate: ({
    projectId,
    refId,
    candidateId,
    ...data
  }: AigcProjectDocumentDecisionInput) =>
    backendApi.post<AigcProjectDocumentDecision>(
      `/aigc/projects/${projectId}/document-refs/${refId}/candidates/${candidateId}/_adopt`,
      data
    ),
  rejectProjectDocumentCandidate: ({
    projectId,
    refId,
    candidateId,
    ...data
  }: AigcProjectDocumentDecisionInput) =>
    backendApi.post<AigcProjectDocumentDecision>(
      `/aigc/projects/${projectId}/document-refs/${refId}/candidates/${candidateId}/_reject`,
      data
    ),
  recordTextCandidate: ({ projectId, objectId, ...data }: AigcManualCandidateInput) =>
    backendApi.post<AigcObjectVersionView>(
      `/aigc/projects/${projectId}/objects/${objectId}/candidates/text`,
      data,
      { showError: false }
    ),
  recordDocumentCandidate: ({ projectId, objectId, ...data }: AigcDocumentCandidateInput) =>
    backendApi.post<AigcObjectVersionView>(
      `/aigc/projects/${projectId}/objects/${objectId}/candidates/document`,
      data,
      { showError: false }
    ),
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
  conversationContext: (id: number) =>
    ["aigc.project", "conversation-context", id] as const,
  graph: (id: number) => ["aigc.project", "graph", id] as const,
  summary: (id: number) => ["aigc.project", "summary", id] as const,
  readiness: (id: number, includedOptionalObjectIds: number[] = []) =>
    ["aigc.project", "readiness", id, includedOptionalObjectIds] as const,
  progress: (id: number) => ["aigc.project", "progress", id] as const,
  generationProposals: (id: number) =>
    ["aigc.project", "generation-proposals", id] as const,
  configuration: (id: number) => ["aigc.project", "configuration", id] as const,
  channelRefs: (id: number) => ["aigc.project", "channel-refs", id] as const,
  documentRefs: (id: number) => ["aigc.project", "document-refs", id] as const,
  documentCandidates: (projectId: number, refId: number) =>
    ["aigc.project", "document-candidates", projectId, refId] as const,
  mediaRefs: (id: number) => ["aigc.project", "media-refs", id] as const,
  versions: (projectId: number, objectId: number) =>
    ["aigc.project", "versions", projectId, objectId] as const,
  comparison: (projectId: number, objectId: number, left: number, right: number) =>
    ["aigc.project", "comparison", projectId, objectId, left, right] as const,
  reviews: (projectId: number) => ["aigc.project", "reviews", projectId] as const,
  completionEvidence: (projectId: number) =>
    ["aigc.project", "completion-evidence", projectId] as const
}

export const aigcProjectCreationDraftKeys = {
  all: ["aigc.project-creation-draft"] as const,
  generationProposals: (draftId: string) =>
    ["aigc.project-creation-draft", draftId, "generation-proposals"] as const
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
export function useAigcProjectConversationContext(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.conversationContext(id ?? 0),
    queryFn: () => aigcProjectApi.conversationContext(id as number),
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
export function useAigcProjectReadiness(
  id: number | null,
  includedOptionalObjectIds: number[] = []
) {
  return useQuery({
    queryKey: aigcProjectKeys.readiness(id ?? 0, includedOptionalObjectIds),
    queryFn: () => aigcProjectApi.readiness(id as number, includedOptionalObjectIds),
    enabled: id !== null
  })
}
export function useAigcProjectProgress(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.progress(id ?? 0),
    queryFn: () => aigcProjectApi.progress(id as number),
    enabled: id !== null
  })
}
export function useAigcProjectGenerationProposals(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.generationProposals(id ?? 0),
    queryFn: () => aigcProjectApi.generationProposals(id as number),
    enabled: id !== null
  })
}
export function useAigcProjectCreationDraftProposals(draftId: string | null) {
  return useQuery({
    queryKey: aigcProjectCreationDraftKeys.generationProposals(draftId ?? ""),
    queryFn: () => aigcProjectCreationDraftApi.generationProposals(draftId as string),
    enabled: draftId !== null
  })
}
export function useAigcProjectConfiguration(id: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.configuration(id ?? 0),
    queryFn: () => aigcProjectApi.configuration(id as number),
    enabled: id !== null,
    select: (snapshots) => snapshots.at(0) ?? null
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
export function useAigcProjectDocumentCandidates(projectId: number | null, refId: number | null) {
  return useQuery({
    queryKey: aigcProjectKeys.documentCandidates(projectId ?? 0, refId ?? 0),
    queryFn: () => aigcProjectApi.projectDocumentCandidates(projectId as number, refId as number),
    enabled: projectId !== null && refId !== null
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
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.conversationContext(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.graph(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(projectId) }),
    queryClient.invalidateQueries({
      queryKey: ["aigc.project", "readiness", projectId]
    }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.progress(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.reviews(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.completionEvidence(projectId) }),
    queryClient.invalidateQueries({ queryKey: ["aigc.project", "versions", projectId] }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.all })
  ])
}

function refreshAigcProjectCreationDraftProposalState(
  queryClient: ReturnType<typeof useQueryClient>,
  proposal: AigcProjectGenerationProposal
) {
  if (proposal.scopeKind !== "CREATION_DRAFT" || proposal.draftId === null) {
    return Promise.resolve()
  }
  const queryKey = aigcProjectCreationDraftKeys.generationProposals(proposal.draftId)
  queryClient.setQueryData<AigcProjectGenerationProposal[]>(queryKey, (current = []) => [
    proposal,
    ...current.filter(
      (item) =>
        item.proposalId !== proposal.proposalId ||
        item.proposalVersion !== proposal.proposalVersion
    )
  ])
  return queryClient.invalidateQueries({ queryKey })
}

export function useProposeAigcProjectCreationDraftGeneration() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectCreationDraftApi.proposeGeneration,
    onSuccess: (proposal) =>
      refreshAigcProjectCreationDraftProposalState(queryClient, proposal)
  })
}

export function useReviseAigcProjectCreationDraftGenerationProposal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectCreationDraftApi.reviseGenerationProposal,
    onSuccess: (proposal) =>
      refreshAigcProjectCreationDraftProposalState(queryClient, proposal)
  })
}

export function useApproveAndStartAigcProjectCreationDraftGeneration() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectCreationDraftApi.approveAndStartGeneration,
    onSuccess: (proposal) =>
      refreshAigcProjectCreationDraftProposalState(queryClient, proposal)
  })
}

export function useProposeAigcProjectGeneration() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.proposeGeneration,
    onSuccess: (_proposal, input) =>
      refreshAigcGenerationProposalState(queryClient, input.projectId),
    onError: (error, input) => {
      if (error instanceof ApiError && error.code === 409)
        void refreshAigcGenerationProposalState(queryClient, input.projectId)
    }
  })
}

export function useReviseAigcProjectGenerationProposal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.reviseGenerationProposal,
    onSuccess: (_proposal, input) =>
      refreshAigcGenerationProposalState(queryClient, input.projectId),
    onError: (error, input) => {
      if (error instanceof ApiError && error.code === 409)
        void refreshAigcGenerationProposalState(queryClient, input.projectId)
    }
  })
}

export function useApproveAndStartAigcProjectGeneration() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.approveAndStartGeneration,
    onSuccess: (_proposal, input) =>
      refreshAigcGenerationProposalState(queryClient, input.projectId),
    onError: (error, input) => {
      if (error instanceof ApiError && error.code === 409)
        void refreshAigcGenerationProposalState(queryClient, input.projectId)
    }
  })
}

function refreshAigcGenerationProposalState(
  queryClient: ReturnType<typeof useQueryClient>,
  projectId: number
) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.generationProposals(projectId) }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(projectId) }),
    queryClient.invalidateQueries({ queryKey: ["aigc.project", "readiness", projectId] }),
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.progress(projectId) })
  ])
}

export function useMaterializeAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.materialize,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: aigcProjectKeys.all })
  })
}
export function useBindAigcProjectConversation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.bindConversation,
    onSuccess: async (context, input) => {
      queryClient.setQueryData(aigcProjectKeys.conversationContext(input.projectId), context)
      return Promise.all([
        queryClient.invalidateQueries({
          queryKey: aigcProjectKeys.conversationContext(input.projectId)
        }),
        queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(input.projectId) })
      ])
    }
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

function useCandidateMutation<T extends AigcManualCandidateInput>(
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

export function useRecordAigcTextCandidate() {
  return useCandidateMutation(aigcProjectApi.recordTextCandidate)
}

export function useRecordAigcDocumentCandidate() {
  return useCandidateMutation(aigcProjectApi.recordDocumentCandidate)
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
export function useUpdateAigcProjectDocumentToCurrent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.updateDocumentToCurrent,
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
export function useRecordAigcProjectDocumentCandidate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.recordProjectDocumentCandidate,
    onSuccess: async (_candidate, input) =>
      Promise.all([
        queryClient.invalidateQueries({
          queryKey: aigcProjectKeys.documentCandidates(input.projectId, input.refId)
        }),
        queryClient.invalidateQueries({ queryKey: aigcProjectKeys.documentRefs(input.projectId) }),
        invalidateAigcProject(queryClient, input.projectId)
      ])
  })
}
export function useAdoptAigcProjectDocumentCandidate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.adoptProjectDocumentCandidate,
    onSuccess: async (_candidate, input) =>
      Promise.all([
        queryClient.invalidateQueries({
          queryKey: aigcProjectKeys.documentCandidates(input.projectId, input.refId)
        }),
        queryClient.invalidateQueries({ queryKey: aigcProjectKeys.documentRefs(input.projectId) }),
        invalidateAigcProject(queryClient, input.projectId)
      ])
  })
}
export function useRejectAigcProjectDocumentCandidate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.rejectProjectDocumentCandidate,
    onSuccess: async (_candidate, input) =>
      Promise.all([
        queryClient.invalidateQueries({
          queryKey: aigcProjectKeys.documentCandidates(input.projectId, input.refId)
        }),
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
  semanticRole: AigcSemanticRole
  outputContract: AigcOutputContract
  orderNo?: number
  schemaVersion?: string
  payloadJson?: string
  expectedProjectVersion: number
  expectedGraphRevision: number
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
