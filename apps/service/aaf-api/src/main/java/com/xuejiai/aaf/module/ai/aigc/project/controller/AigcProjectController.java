package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetEvaluateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDeliverableSetManifestFreezeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDocumentCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionRejectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectConversationBindCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentInput;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationModeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalDraft;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalRevisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationStartCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycleCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMediaRefCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectContractCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectMoveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectRemoveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewDecisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcTextCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectGenerationMaterializationService;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectGenerationProposalService;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcDeliverableSetEvaluateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcDeliverableSetFreezeDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcDocumentCandidateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionAdoptDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionRejectDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectChannelRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectConfigSnapshotVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentCandidateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentDecisionDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentDecisionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGenerationModeDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGenerationProposalDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGenerationProposalItemDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGenerationProposalReviseDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGraphVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectLifecycleDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMaterializeDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMediaRefDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMediaRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectCommandDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectContractDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectMoveDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectRemoveDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectProfileRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectResourceRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectRevisionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectSummaryVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcReviewDecisionDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcReviewSubmitDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTextCandidateDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** 唯一 AIGC 项目聚合 REST 接口。 */
@Tag(name = "AIGC 项目")
@RestController
@RequestMapping("/api/aigc/projects")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcProjectController
        extends BaseCrudController<
                AigcProject, AigcProjectVO, Void, AigcProjectUpdateDTO, AigcProjectPageDTO> {

    /** 表单路径提议来源固定为用户，客户端不得声明 Assistant 来源。 */
    private static final String USER_PROPOSAL_SOURCE_TYPE = "USER";

    private final AigcProjectService service;
    private final AigcProjectGenerationProposalService proposals;
    private final AigcProjectGenerationMaterializationService generations;

    @Override
    protected AigcProjectService getService() {
        return service;
    }

    @Operation(summary = "按已确认来源物化项目")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_CREATE)
    @PostMapping("/_materialize")
    public Result<?> materialize(@Valid @RequestBody AigcProjectMaterializeDTO request) {
        AigcProjectMaterializeCommand command =
                switch (request.sourceType()) {
                    case CATALOG -> {
                        var catalog = request.catalog();
                        yield new AigcProjectMaterializeCommand.Catalog(
                                OrgContext.getCurrentWorkspaceId(),
                                catalog.name(),
                                catalog.description(),
                                catalog.background(),
                                catalog.targetAudience(),
                                catalog.requirements(),
                                catalog.constraints(),
                                catalog.keyFacts(),
                                catalog.keyMessages(),
                                catalog.styleGuidance(),
                                catalog.successCriteria(),
                                catalog.deliveryRequirements(),
                                catalog.locale(),
                                catalog.additionalContext(),
                                catalog.projectTypeCode(),
                                catalog.blueprintVersionId(),
                                catalog.brandProfileVersionIds(),
                                catalog.channelSpecVersionIds(),
                                catalog.documents().stream()
                                        .map(
                                                document ->
                                                        new AigcProjectDocumentInput(
                                                                document.fieldKey(),
                                                                document.documentId(),
                                                                document.documentVersionId(),
                                                                document.contextNote()))
                                        .toList(),
                                catalog.productionMode(),
                                catalog.slotOverrides(),
                                catalog.coverMode(),
                                catalog.coverFileId(),
                                catalog.coverPrompt(),
                                catalog.coverIdempotencyKey(),
                                catalog.approvedCreationProposal() == null
                                        ? null
                                        : new AigcProjectMaterializeCommand.CreationProposalRef(
                                                catalog.approvedCreationProposal().draftId(),
                                                catalog.approvedCreationProposal().proposalId(),
                                                catalog.approvedCreationProposal()
                                                        .proposalVersion(),
                                                catalog.approvedCreationProposal().proposalHash()));
                    }
                    case ASSISTANT_GENERATED -> {
                        var proposal = request.assistantProposal();
                        yield new AigcProjectMaterializeCommand.AssistantProposal(
                                proposal.proposalId(),
                                proposal.proposalVersion(),
                                proposal.proposalHash());
                    }
                };
        return Result.success(service.materialize(command));
    }

    @Operation(summary = "解析项目受控 Assistant 会话")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/conversation-context")
    public Result<?> conversationContext(@PathVariable Long id) {
        return Result.success(service.conversationContext(id));
    }

    @Operation(summary = "绑定项目 Assistant 并创建受控会话")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/conversation-context/_bind")
    public Result<?> bindConversation(
            @PathVariable Long id, @Valid @RequestBody ProjectConversationBindDTO request) {
        return Result.success(
                service.bindConversation(
                        new AigcProjectConversationBindCommand(
                                id, request.expectedProjectVersion(), request.assistantId())));
    }

    @Operation(summary = "查询项目生成 Proposal 列表")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/generation-proposals")
    public Result<?> generationProposals(@PathVariable Long id) {
        return Result.success(proposals.list(id));
    }

    @Operation(summary = "创建待确认生成 Proposal")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/generation-proposals")
    public Result<?> proposeGeneration(
            @PathVariable Long id, @Valid @RequestBody AigcProjectGenerationProposalDTO request) {
        return Result.success(
                proposals.propose(
                        new AigcProjectGenerationProposalDraft(
                                AigcGenerationProposalScope.project(id),
                                request.expectedProjectVersion(),
                                USER_PROPOSAL_SOURCE_TYPE,
                                null,
                                request.confirmationPolicy(),
                                null,
                                request.expiresAt(),
                                proposalItems(request.items()))));
    }

    @Operation(summary = "修订生成 Proposal 并产生新版本")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/generation-proposals/_revise")
    public Result<?> reviseGeneration(
            @PathVariable Long id,
            @Valid @RequestBody AigcProjectGenerationProposalReviseDTO request) {
        return Result.success(
                proposals.revise(
                        new AigcProjectGenerationProposalRevisionCommand(
                                AigcGenerationProposalScope.project(id),
                                request.proposalId(),
                                request.proposalVersion(),
                                request.proposalHash(),
                                request.expectedProjectVersion(),
                                null,
                                request.confirmationPolicy(),
                                null,
                                request.expiresAt(),
                                proposalItems(request.items()))));
    }

    @Operation(summary = "确认生成 Proposal 并启动唯一 canonical Task")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/generation-proposals/_approve-and-start")
    public Result<?> approveAndStartGeneration(
            @PathVariable Long id, @Valid @RequestBody ProjectGenerationStartDTO request) {
        return Result.success(
                generations.approveAndStart(
                        new AigcProjectGenerationStartCommand(
                                AigcGenerationProposalScope.project(id),
                                request.expectedProjectVersion(),
                                request.proposalId(),
                                request.proposalVersion(),
                                request.proposalHash(),
                                request.approvedTargetKeys(),
                                request.approvalIdempotencyKey(),
                                null)));
    }

    @Operation(summary = "获取项目图谱")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/graph")
    public Result<AigcProjectGraphVO> graph(@PathVariable Long id) {
        return Result.success(service.graph(id));
    }

    @Operation(summary = "获取项目概览")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/summary")
    public Result<AigcProjectSummaryVO> summary(@PathVariable Long id) {
        return Result.success(service.summary(id));
    }

    @Operation(summary = "获取服务端对象 readiness")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/readiness")
    public Result<?> readiness(
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> includedOptionalObjectIds) {
        return Result.success(
                service.readiness(id, new AigcGenerationScope(includedOptionalObjectIds)));
    }

    @Operation(summary = "获取项目进度快照")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/progress")
    public Result<?> progress(@PathVariable Long id) {
        return Result.success(service.progress(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/objects")
    public Result<List<AigcProjectObjectVO>> objects(@PathVariable Long id) {
        return Result.success(service.objects(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/objects")
    public Result<?> appendObject(
            @PathVariable Long id, @Valid @RequestBody AigcProjectObjectCommandDTO request) {
        return Result.success(
                service.appendObject(
                        new AigcProjectObjectCommand(
                                id,
                                request.parentObjectId(),
                                request.blueprintTemplateKey(),
                                request.objectType(),
                                request.displayName(),
                                request.contractRole(),
                                request.orderNo(),
                                request.semanticRole(),
                                request.outputContract(),
                                request.schemaVersion(),
                                request.payloadJson(),
                                request.expectedProjectVersion(),
                                request.expectedGraphRevision())));
    }

    @Operation(summary = "移动项目对象")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/objects/{objectId}/_move")
    public Result<?> moveObject(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @Valid @RequestBody AigcProjectObjectMoveDTO request) {
        return Result.success(
                service.moveObject(
                        new AigcProjectObjectMoveCommand(
                                id,
                                objectId,
                                request.newParentObjectId(),
                                request.orderNo(),
                                request.expectedProjectVersion(),
                                request.expectedGraphRevision())));
    }

    @Operation(summary = "将手工纯文本冻结为 DocumentVersion candidate")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/objects/{objectId}/candidates/text")
    public Result<?> recordTextCandidate(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @Valid @RequestBody AigcTextCandidateDTO request) {
        return Result.success(
                service.recordTextCandidate(
                        new AigcTextCandidateCommand(
                                id,
                                objectId,
                                request.expectedProjectVersion(),
                                request.expectedGraphRevision(),
                                request.content(),
                                request.summary(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "登记手工文档 candidate")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/objects/{objectId}/candidates/document")
    public Result<?> recordDocumentCandidate(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @Valid @RequestBody AigcDocumentCandidateDTO request) {
        return Result.success(
                service.recordDocumentCandidate(
                        new AigcDocumentCandidateCommand(
                                id,
                                objectId,
                                request.expectedProjectVersion(),
                                request.expectedGraphRevision(),
                                request.title(),
                                request.content(),
                                request.format(),
                                request.summary(),
                                request.idempotencyKey())));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PutMapping("/{id}/objects/{objectId}/contract")
    public Result<?> updateObjectContract(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @Valid @RequestBody AigcProjectObjectContractDTO request) {
        return Result.success(
                service.updateObjectContract(
                        new AigcProjectObjectContractCommand(
                                id,
                                objectId,
                                request.contractRole(),
                                request.expectedProjectVersion(),
                                request.expectedGraphRevision())));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @DeleteMapping("/{id}/objects/{objectId}")
    public Result<?> removeObject(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @Valid @RequestBody AigcProjectObjectRemoveDTO request) {
        return Result.success(
                service.removeObject(
                        new AigcProjectObjectRemoveCommand(
                                id,
                                objectId,
                                request.expectedProjectVersion(),
                                request.expectedGraphRevision(),
                                request.reason())));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/objects/{objectId}/versions")
    public Result<List<AigcObjectVersionVO>> versions(
            @PathVariable Long id, @PathVariable Long objectId) {
        return Result.success(service.versions(id, objectId));
    }

    @Operation(summary = "比较对象版本")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/objects/{objectId}/versions/_compare")
    public Result<?> compareVersions(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @RequestParam Long leftVersionId,
            @RequestParam Long rightVersionId) {
        return Result.success(
                service.compareObjectVersions(id, objectId, leftVersionId, rightVersionId));
    }

    @Operation(summary = "采用候选对象版本")
    @PreAuthorize(AigcAuthorities.HAS_OBJECT_VERSION_ADOPT)
    @PostMapping("/{id}/objects/{objectId}/versions/{versionId}/_adopt")
    public Result<?> adoptVersion(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @PathVariable Long versionId,
            @Valid @RequestBody AigcObjectVersionAdoptDTO request) {
        return Result.success(
                service.adoptVersion(
                        new AigcObjectVersionAdoptCommand(
                                id,
                                objectId,
                                versionId,
                                request.expectedAdoptedVersionId(),
                                request.expectedProjectVersion(),
                                request.confirmedReplacement(),
                                request.reason(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "否决候选对象版本")
    @PreAuthorize(AigcAuthorities.HAS_OBJECT_VERSION_ADOPT)
    @PostMapping("/{id}/objects/{objectId}/versions/{versionId}/_reject")
    public Result<?> rejectVersion(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @PathVariable Long versionId,
            @Valid @RequestBody AigcObjectVersionRejectDTO request) {
        return Result.success(
                service.rejectVersion(
                        new AigcObjectVersionRejectCommand(
                                id,
                                objectId,
                                versionId,
                                request.expectedProjectVersion(),
                                request.reason(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "评估 DeliverableSet 完整性")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @PostMapping("/{id}/deliverable-sets/{setObjectId}/_evaluate")
    public Result<?> evaluateDeliverableSet(
            @PathVariable Long id,
            @PathVariable Long setObjectId,
            @Valid @RequestBody AigcDeliverableSetEvaluateDTO request) {
        return Result.success(
                service.evaluateDeliverableSet(
                        new AigcDeliverableSetEvaluateCommand(
                                id,
                                setObjectId,
                                request.includedOptionalObjectIds(),
                                request.expectedGraphRevision())));
    }

    @Operation(summary = "冻结 DeliverableSet manifest")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/deliverable-sets/{setObjectId}/_freeze")
    public Result<?> freezeDeliverableSet(
            @PathVariable Long id,
            @PathVariable Long setObjectId,
            @Valid @RequestBody AigcDeliverableSetFreezeDTO request) {
        return Result.success(
                service.freezeDeliverableSetManifest(
                        new AigcDeliverableSetManifestFreezeCommand(
                                id,
                                setObjectId,
                                request.includedOptionalObjectIds(),
                                request.expectedGraphRevision(),
                                request.expectedEvidenceHash(),
                                request.expectedProjectVersion(),
                                request.idempotencyKey())));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/media-refs")
    public Result<?> attachMedia(
            @PathVariable Long id, @Valid @RequestBody AigcProjectMediaRefDTO request) {
        return Result.success(
                service.attachMedia(
                        new AigcProjectMediaRefCommand(
                                id,
                                request.objectId(),
                                request.mediaVersionId(),
                                request.role(),
                                request.sortOrder(),
                                request.adoptionStatus(),
                                request.expectedProjectVersion())));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/media-refs")
    public Result<List<AigcProjectMediaRefVO>> mediaRefs(@PathVariable Long id) {
        return Result.success(service.mediaRefs(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @DeleteMapping("/{id}/media-refs/{refId}")
    public Result<Void> detachMedia(
            @PathVariable Long id,
            @PathVariable Long refId,
            @NotNull Integer expectedProjectVersion) {
        service.detachMedia(id, refId, expectedProjectVersion);
        return Result.success();
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/configuration")
    public Result<List<AigcProjectConfigSnapshotVO>> configuration(@PathVariable Long id) {
        return Result.success(service.configuration(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/profile-refs")
    public Result<List<AigcProjectProfileRefVO>> profileRefs(@PathVariable Long id) {
        return Result.success(service.profileRefs(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/channel-refs")
    public Result<List<AigcProjectChannelRefVO>> channelRefs(@PathVariable Long id) {
        return Result.success(service.channelRefs(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/document-refs")
    public Result<AigcProjectDocumentRefVO> attachDocument(
            @PathVariable Long id, @Valid @RequestBody AigcProjectDocumentRefDTO request) {
        return Result.success(service.attachDocument(id, request));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/document-refs/{refId}/_update-to-current")
    public Result<AigcProjectDocumentRefVO> updateExternalDocumentReference(
            @PathVariable Long id,
            @PathVariable Long refId,
            @Valid @RequestBody AigcProjectDocumentRefUpdateDTO request) {
        return Result.success(service.updateExternalDocumentReference(id, refId, request));
    }

    @Operation(summary = "登记项目自产文档 candidate")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/document-refs/{refId}/candidates")
    public Result<AigcProjectDocumentDecisionVO> recordProjectDocumentCandidate(
            @PathVariable Long id,
            @PathVariable Long refId,
            @Valid @RequestBody AigcProjectDocumentCandidateDTO request) {
        return Result.success(service.recordProjectDocumentCandidate(id, refId, request));
    }

    @Operation(summary = "获取项目文档 candidate 与决策审计")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/document-refs/{refId}/candidates")
    public Result<List<AigcProjectDocumentDecisionVO>> projectDocumentCandidates(
            @PathVariable Long id, @PathVariable Long refId) {
        return Result.success(service.projectDocumentCandidates(id, refId));
    }

    @Operation(summary = "采用项目文档 candidate")
    @PreAuthorize(AigcAuthorities.HAS_OBJECT_VERSION_ADOPT)
    @PostMapping("/{id}/document-refs/{refId}/candidates/{candidateId}/_adopt")
    public Result<AigcProjectDocumentDecisionVO> adoptProjectDocumentCandidate(
            @PathVariable Long id,
            @PathVariable Long refId,
            @PathVariable Long candidateId,
            @Valid @RequestBody AigcProjectDocumentDecisionDTO request) {
        return Result.success(
                service.adoptProjectDocumentCandidate(id, refId, candidateId, request));
    }

    @Operation(summary = "否决项目文档 candidate")
    @PreAuthorize(AigcAuthorities.HAS_OBJECT_VERSION_ADOPT)
    @PostMapping("/{id}/document-refs/{refId}/candidates/{candidateId}/_reject")
    public Result<AigcProjectDocumentDecisionVO> rejectProjectDocumentCandidate(
            @PathVariable Long id,
            @PathVariable Long refId,
            @PathVariable Long candidateId,
            @Valid @RequestBody AigcProjectDocumentDecisionDTO request) {
        return Result.success(
                service.rejectProjectDocumentCandidate(id, refId, candidateId, request));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/document-refs")
    public Result<List<AigcProjectDocumentRefVO>> documentRefs(@PathVariable Long id) {
        return Result.success(service.documentRefs(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @DeleteMapping("/{id}/document-refs/{refId}")
    public Result<Void> detachDocument(
            @PathVariable Long id,
            @PathVariable Long refId,
            @NotNull Integer expectedProjectVersion) {
        service.detachDocument(id, refId, expectedProjectVersion);
        return Result.success();
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/resource-refs")
    public Result<List<AigcProjectResourceRefVO>> resourceRefs(@PathVariable Long id) {
        return Result.success(service.resourceRefs(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/revisions")
    public Result<List<AigcProjectRevisionVO>> revisions(@PathVariable Long id) {
        return Result.success(service.revisions(id));
    }

    @Operation(summary = "提交精确 manifest 审核")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_REVIEW)
    @PostMapping("/{id}/reviews")
    public Result<?> submitReview(
            @PathVariable Long id, @Valid @RequestBody AigcReviewSubmitDTO request) {
        return Result.success(
                service.submitReview(
                        new AigcReviewSubmitCommand(
                                id,
                                request.setObjectId(),
                                request.manifestObjectVersionId(),
                                request.expectedProjectVersion(),
                                request.idempotencyKey())));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/reviews")
    public Result<?> reviews(@PathVariable Long id) {
        return Result.success(service.reviews(id));
    }

    @Operation(summary = "审核通过精确 manifest")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_REVIEW)
    @PostMapping("/{id}/reviews/{reviewObjectId}/_approve")
    public Result<?> approveReview(
            @PathVariable Long id,
            @PathVariable Long reviewObjectId,
            @Valid @RequestBody AigcReviewDecisionDTO request) {
        return Result.success(service.approveReview(reviewCommand(id, reviewObjectId, request)));
    }

    @Operation(summary = "退回精确 manifest 审核")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_REVIEW)
    @PostMapping("/{id}/reviews/{reviewObjectId}/_return")
    public Result<?> returnReview(
            @PathVariable Long id,
            @PathVariable Long reviewObjectId,
            @Valid @RequestBody AigcReviewDecisionDTO request) {
        return Result.success(service.returnReview(reviewCommand(id, reviewObjectId, request)));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{id}/completion-evidence")
    public Result<?> completionEvidence(@PathVariable Long id) {
        return Result.success(service.completionEvidence(id));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_LIFECYCLE)
    @PostMapping("/{id}/_complete")
    public Result<?> complete(
            @PathVariable Long id, @Valid @RequestBody AigcProjectLifecycleDTO request) {
        return Result.success(
                service.complete(
                        new AigcProjectLifecycleCommand(
                                id,
                                request.expectedProjectVersion(),
                                request.idempotencyKey(),
                                request.reason())));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_LIFECYCLE)
    @PostMapping("/{id}/_archive")
    public Result<?> archive(
            @PathVariable Long id, @Valid @RequestBody AigcProjectLifecycleDTO request) {
        return Result.success(
                service.archive(
                        new AigcProjectLifecycleCommand(
                                id,
                                request.expectedProjectVersion(),
                                request.idempotencyKey(),
                                request.reason())));
    }

    @Operation(summary = "切换项目协作模式（manual/auto）")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_UPDATE)
    @PostMapping("/{id}/generation-mode")
    public Result<?> switchGenerationMode(
            @PathVariable Long id, @Valid @RequestBody AigcProjectGenerationModeDTO request) {
        return Result.success(
                service.switchGenerationMode(
                        new AigcProjectGenerationModeCommand(
                                id,
                                request.mode(),
                                request.expectedProjectVersion(),
                                request.idempotencyKey())));
    }

    private AigcReviewDecisionCommand reviewCommand(
            Long projectId, Long reviewObjectId, AigcReviewDecisionDTO request) {
        return new AigcReviewDecisionCommand(
                projectId,
                reviewObjectId,
                request.expectedManifestObjectVersionId(),
                request.expectedProjectVersion(),
                request.expectedReviewVersion(),
                request.comment(),
                request.idempotencyKey());
    }

    /** 目标项映射：只搬运业务字段，成本与全部 hash 仍由服务端解析和冻结。 */
    private static List<AigcProjectGenerationProposalDraft.Item> proposalItems(
            List<AigcProjectGenerationProposalItemDTO> items) {
        return items.stream()
                .map(
                        item ->
                                new AigcProjectGenerationProposalDraft.Item(
                                        item.targetType(),
                                        item.targetKey(),
                                        item.actionKey(),
                                        item.generationInstruction(),
                                        item.dependencyKeys(),
                                        item.requiredContextKeys(),
                                        item.modelPolicy(),
                                        null,
                                        item.confirmationPolicy()))
                .toList();
    }

    public record ProjectConversationBindDTO(Integer expectedProjectVersion, String assistantId) {}

    /** 单一原子生成启动请求；taskId 由服务端确定性派生，客户端不提交 Task 身份。 */
    public record ProjectGenerationStartDTO(
            @NotNull Integer expectedProjectVersion,
            @NotBlank String proposalId,
            @NotNull Integer proposalVersion,
            @NotBlank String proposalHash,
            @NotEmpty List<@NotBlank String> approvedTargetKeys,
            @NotBlank String approvalIdempotencyKey) {}
}
