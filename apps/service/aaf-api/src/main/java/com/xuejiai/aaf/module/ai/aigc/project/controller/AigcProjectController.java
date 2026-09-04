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
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionAdoptCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMediaRefCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcReviewApproveCommand;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionDecisionDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcObjectVersionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectChannelRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectConfigSnapshotVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocumentRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGraphVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMaterializeDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMediaRefDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectMediaRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectCommandDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectObjectVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectProfileRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectResourceRefVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectRevisionVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectStatusDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectSummaryVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVersionDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcReviewApproveDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    @PostMapping("/_materialize")
    public Result<com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectMaterializeView>
            materialize(@Valid @RequestBody AigcProjectMaterializeDTO request) {
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
                                request.briefJson(),
                                request.coverMode(),
                                request.coverFileId(),
                                request.coverPrompt(),
                                request.coverIdempotencyKey())));
    }

    @Operation(summary = "获取项目图谱")
    @GetMapping("/{id}/graph")
    public Result<AigcProjectGraphVO> graph(@PathVariable Long id) {
        return Result.success(service.graph(id));
    }

    @Operation(summary = "获取项目概览")
    @GetMapping("/{id}/summary")
    public Result<AigcProjectSummaryVO> summary(@PathVariable Long id) {
        return Result.success(service.summary(id));
    }

    @Operation(summary = "更新项目状态")
    @PutMapping("/{id}/status")
    public Result<AigcProjectVO> updateStatus(
            @PathVariable Long id, @Valid @RequestBody AigcProjectStatusDTO request) {
        return Result.success(
                service.updateStatus(id, request.status(), request.expectedVersion()));
    }

    @Operation(summary = "查询项目对象")
    @GetMapping("/{id}/objects")
    public Result<List<AigcProjectObjectVO>> objects(@PathVariable Long id) {
        return Result.success(service.objects(id));
    }

    @Operation(summary = "追加项目对象")
    @PostMapping("/{id}/objects")
    public Result<?> appendObject(
            @PathVariable Long id, @Valid @RequestBody AigcProjectObjectCommandDTO request) {
        return Result.success(
                service.appendObject(
                        new AigcProjectObjectCommand(
                                id,
                                request.parentObjectId(),
                                request.stableKey(),
                                request.objectType(),
                                request.orderNo(),
                                request.schemaVersion(),
                                request.payloadJson(),
                                request.expectedProjectVersion())));
    }

    @Operation(summary = "查询对象版本")
    @GetMapping("/{id}/objects/{objectId}/versions")
    public Result<List<AigcObjectVersionVO>> versions(
            @PathVariable Long id, @PathVariable Long objectId) {
        return Result.success(service.versions(id, objectId));
    }

    @Operation(summary = "采用候选对象版本")
    @PreAuthorize("hasAuthority('aigc:project:adopt')")
    @PostMapping("/{id}/objects/{objectId}/versions/{versionId}/_adopt")
    public Result<?> adoptVersion(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @PathVariable Long versionId,
            @Valid @RequestBody AigcObjectVersionDecisionDTO request) {
        return Result.success(
                service.adoptVersion(
                        new AigcObjectVersionAdoptCommand(
                                id,
                                objectId,
                                versionId,
                                request.expectedProjectVersion(),
                                request.reason())));
    }

    @Operation(summary = "否决候选对象版本")
    @PostMapping("/{id}/objects/{objectId}/versions/{versionId}/_reject")
    public Result<?> rejectVersion(
            @PathVariable Long id,
            @PathVariable Long objectId,
            @PathVariable Long versionId,
            @Valid @RequestBody AigcObjectVersionDecisionDTO request) {
        return Result.success(
                service.rejectVersion(id, objectId, versionId, request.expectedProjectVersion()));
    }

    @Operation(summary = "关联项目媒体版本")
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

    @Operation(summary = "查询项目媒体引用")
    @GetMapping("/{id}/media-refs")
    public Result<List<AigcProjectMediaRefVO>> mediaRefs(@PathVariable Long id) {
        return Result.success(service.mediaRefs(id));
    }

    @Operation(summary = "解除项目媒体引用")
    @DeleteMapping("/{id}/media-refs/{refId}")
    public Result<Void> detachMedia(
            @PathVariable Long id,
            @PathVariable Long refId,
            @jakarta.validation.constraints.NotNull Integer expectedProjectVersion) {
        service.detachMedia(id, refId, expectedProjectVersion);
        return Result.success();
    }

    @GetMapping("/{id}/configuration")
    public Result<List<AigcProjectConfigSnapshotVO>> configuration(@PathVariable Long id) {
        return Result.success(service.configuration(id));
    }

    @GetMapping("/{id}/profile-refs")
    public Result<List<AigcProjectProfileRefVO>> profileRefs(@PathVariable Long id) {
        return Result.success(service.profileRefs(id));
    }

    @GetMapping("/{id}/channel-refs")
    public Result<List<AigcProjectChannelRefVO>> channelRefs(@PathVariable Long id) {
        return Result.success(service.channelRefs(id));
    }

    @Operation(summary = "关联项目文档")
    @PostMapping("/{id}/document-refs")
    public Result<AigcProjectDocumentRefVO> attachDocument(
            @PathVariable Long id, @Valid @RequestBody AigcProjectDocumentRefDTO request) {
        return Result.success(service.attachDocument(id, request));
    }

    @Operation(summary = "查询项目文档引用")
    @GetMapping("/{id}/document-refs")
    public Result<List<AigcProjectDocumentRefVO>> documentRefs(@PathVariable Long id) {
        return Result.success(service.documentRefs(id));
    }

    @Operation(summary = "解除项目文档引用")
    @DeleteMapping("/{id}/document-refs/{refId}")
    public Result<Void> detachDocument(
            @PathVariable Long id,
            @PathVariable Long refId,
            @jakarta.validation.constraints.NotNull Integer expectedProjectVersion) {
        service.detachDocument(id, refId, expectedProjectVersion);
        return Result.success();
    }

    @GetMapping("/{id}/resource-refs")
    public Result<List<AigcProjectResourceRefVO>> resourceRefs(@PathVariable Long id) {
        return Result.success(service.resourceRefs(id));
    }

    @GetMapping("/{id}/revisions")
    public Result<List<AigcProjectRevisionVO>> revisions(@PathVariable Long id) {
        return Result.success(service.revisions(id));
    }

    @PostMapping("/{id}/_submit-review")
    public Result<AigcProjectView> submitReview(
            @PathVariable Long id, @Valid @RequestBody AigcProjectVersionDTO request) {
        return Result.success(service.submitReview(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/_approve-review")
    public Result<AigcProjectView> approveReview(
            @PathVariable Long id, @Valid @RequestBody AigcReviewApproveDTO request) {
        return Result.success(
                service.approveReview(
                        new AigcReviewApproveCommand(
                                id,
                                request.reviewObjectId(),
                                request.expectedProjectVersion(),
                                request.conclusion())));
    }

    @PostMapping("/{id}/_complete")
    public Result<AigcProjectView> complete(
            @PathVariable Long id, @Valid @RequestBody AigcProjectVersionDTO request) {
        return Result.success(service.complete(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/_archive")
    public Result<AigcProjectView> archive(
            @PathVariable Long id, @Valid @RequestBody AigcProjectVersionDTO request) {
        return Result.success(service.archive(id, request.expectedVersion()));
    }
}
