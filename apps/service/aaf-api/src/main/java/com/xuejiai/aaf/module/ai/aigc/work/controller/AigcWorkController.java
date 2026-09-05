package com.xuejiai.aaf.module.ai.aigc.work.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationCancelCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationRetryCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationView;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkArchiveCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkCollectCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkPublishCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkView;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWork;
import com.xuejiai.aaf.module.ai.aigc.work.service.AigcWorkService;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcPublicationCancelDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcPublicationRetryDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkArchiveDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkCollectDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPageDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPublicationVO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "AIGC 作品")
@RestController
@RequestMapping("/api/aigc/works")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcWorkController
        extends BaseCrudController<AigcWork, AigcWorkVO, Void, AigcWorkUpdateDTO, AigcWorkPageDTO> {

    private final AigcWorkService service;

    @Override
    protected AigcWorkService getService() {
        return service;
    }

    @Operation(summary = "收录已批准且 non-stale 的 manifest")
    @PreAuthorize(AigcAuthorities.HAS_WORK_COLLECT)
    @PostMapping("/_collect")
    public Result<AigcWorkView> collect(@Valid @RequestBody AigcWorkCollectDTO request) {
        return Result.success(
                service.collect(
                        new AigcWorkCollectCommand(
                                request.projectId(),
                                request.deliverableSetObjectId(),
                                request.manifestObjectVersionId(),
                                request.expectedProjectVersion(),
                                request.coverMediaVersionId(),
                                request.visibility(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "创建受控渠道发布尝试")
    @PreAuthorize(AigcAuthorities.HAS_WORK_PUBLISH)
    @PostMapping("/{id}/publications")
    public Result<AigcPublicationView> publish(
            @PathVariable Long id, @Valid @RequestBody AigcWorkPublishDTO request) {
        return Result.success(
                service.publish(
                        new AigcWorkPublishCommand(
                                id,
                                request.expectedProjectVersion(),
                                request.expectedWorkVersion(),
                                request.channelSpecVersionId(),
                                request.scheduledAt(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "取消活动发布尝试")
    @PreAuthorize(AigcAuthorities.HAS_WORK_PUBLISH)
    @PostMapping("/{id}/publications/{publicationId}/_cancel")
    public Result<AigcPublicationView> cancelPublication(
            @PathVariable Long id,
            @PathVariable Long publicationId,
            @Valid @RequestBody AigcPublicationCancelDTO request) {
        return Result.success(
                service.cancelPublication(
                        new AigcPublicationCancelCommand(
                                id,
                                publicationId,
                                request.expectedProjectVersion(),
                                request.expectedWorkVersion(),
                                request.expectedPublicationVersion(),
                                request.reason(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "重试失败发布并保留原失败历史")
    @PreAuthorize(AigcAuthorities.HAS_WORK_PUBLISH)
    @PostMapping("/{id}/publications/{publicationId}/_retry")
    public Result<AigcPublicationView> retryPublication(
            @PathVariable Long id,
            @PathVariable Long publicationId,
            @Valid @RequestBody AigcPublicationRetryDTO request) {
        return Result.success(
                service.retryPublication(
                        new AigcPublicationRetryCommand(
                                id,
                                publicationId,
                                request.expectedProjectVersion(),
                                request.expectedWorkVersion(),
                                request.expectedPublicationVersion(),
                                request.scheduledAt(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "查询作品发布历史")
    @PreAuthorize(AigcAuthorities.HAS_WORK_READ)
    @GetMapping("/{id}/publications")
    public Result<List<AigcWorkPublicationVO>> publications(@PathVariable Long id) {
        return Result.success(service.publications(id));
    }

    @Operation(summary = "归档作品")
    @PreAuthorize(AigcAuthorities.HAS_WORK_ARCHIVE)
    @PostMapping("/{id}/_archive")
    public Result<AigcWorkView> archiveWork(
            @PathVariable Long id, @Valid @RequestBody AigcWorkArchiveDTO request) {
        return Result.success(
                service.archive(
                        new AigcWorkArchiveCommand(
                                id,
                                request.expectedProjectVersion(),
                                request.expectedWorkVersion(),
                                request.idempotencyKey(),
                                request.reason())));
    }
}
