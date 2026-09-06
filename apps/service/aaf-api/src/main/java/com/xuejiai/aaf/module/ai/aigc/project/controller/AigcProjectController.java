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
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionRejectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycleCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMediaRefCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectContractCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectRemoveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewDecisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcDeliverableSetEvaluateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcDeliverableSetFreezeDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionAdoptDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionRejectDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectChannelRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectConfigSnapshotVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGraphVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectLifecycleDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMaterializeDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMediaRefDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMediaRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectCommandDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectContractDTO;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    private final AigcProjectService service;

    @Override
    protected AigcProjectService getService() {
        return service;
    }

    @Operation(summary = "按已发布配置物化项目")
    @PreAuthorize("hasAuthority('aigc:project:create')")
    @PostMapping("/_materialize")
    public Result<?> materialize(@Valid @RequestBody AigcProjectMaterializeDTO request) {
        return Result.success(
                service.materialize(
                        new AigcProjectMaterializeCommand(
                                OrgContext.getCurrentWorkspaceId(),
                                request.name(),
                                request.description(),
                                request.projectTypeCode(),
                                request.blueprintVersionId(),
                                request.domainExtensionVersionId(),
                                request.brandProfileVersionIds(),
                                request.channelSpecVersionIds(),
                                request.documentVersionIds(),
                                request.productionMode(),
                                request.budgetTier(),
                                request.qualityTier(),
                                request.slotOverrides(),
                                request.briefJson(),
                                request.coverMode(),
                                request.coverFileId(),
                                request.coverPrompt(),
                                request.coverIdempotencyKey())));
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
                                request.schemaVersion(),
                                request.payloadJson(),
                                request.expectedProjectVersion())));
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
                                request.expectedProjectVersion())));
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
                                id, objectId, request.expectedProjectVersion(), request.reason())));
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
}
