package com.xuejiai.aaf.module.ai.aigc.work.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationResultCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcPublicationView;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkCollectCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkPublishCommand;
import com.xuejiai.aaf.module.ai.aigc.work.api.AigcWorkView;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWork;
import com.xuejiai.aaf.module.ai.aigc.work.service.AigcWorkService;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcPublicationResultDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkCollectDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPageDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPublicationVO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@Tag(name = "AIGC 作品")
@RestController
@RequestMapping("/api/aigc/works")
@RequiredArgsConstructor
public class AigcWorkController
        extends BaseCrudController<AigcWork, AigcWorkVO, Void, AigcWorkUpdateDTO, AigcWorkPageDTO> {

    private final AigcWorkService service;

    @Override
    protected AigcWorkService getService() {
        return service;
    }

    @Operation(summary = "收录已审核且已采用的交付物")
    @PreAuthorize("hasAuthority('aigc:work:create')")
    @PostMapping("/_collect")
    public Result<AigcWorkView> collect(@Validated @RequestBody AigcWorkCollectDTO request) {
        return Result.success(
                service.collect(
                        new AigcWorkCollectCommand(
                                request.projectId(),
                                request.deliverableObjectId(),
                                request.adoptedObjectVersionId(),
                                request.coverMediaVersionId(),
                                request.visibility())));
    }

    @Operation(summary = "创建受控渠道发布记录")
    @PreAuthorize("hasAuthority('aigc:work:publish')")
    @PostMapping("/{id}/publications")
    public Result<AigcPublicationView> publish(
            @PathVariable Long id, @Validated @RequestBody AigcWorkPublishDTO request) {
        return Result.success(
                service.publish(
                        new AigcWorkPublishCommand(
                                id,
                                request.channelSpecVersionId(),
                                request.scheduledAt(),
                                request.idempotencyKey())));
    }

    @Operation(summary = "查询作品发布记录")
    @PreAuthorize("hasAuthority('aigc:work:read')")
    @GetMapping("/{id}/publications")
    public Result<List<AigcWorkPublicationVO>> publications(@PathVariable Long id) {
        return Result.success(service.publications(id));
    }

    @Operation(summary = "回写渠道发布结果")
    @PreAuthorize("hasAuthority('aigc:work:publish')")
    @PatchMapping("/{id}/publications/{publicationId}/result")
    public Result<AigcPublicationView> markPublicationResult(
            @PathVariable Long id,
            @PathVariable Long publicationId,
            @Validated @RequestBody AigcPublicationResultDTO request) {
        return Result.success(
                service.markPublicationResult(
                        new AigcPublicationResultCommand(
                                id,
                                publicationId,
                                request.status(),
                                request.externalId(),
                                request.externalUrl(),
                                request.responseJson())));
    }

    @Operation(summary = "归档作品")
    @PreAuthorize("hasAuthority('aigc:work:update')")
    @PostMapping("/{id}/_archive")
    public Result<AigcWorkView> archiveWork(
            @PathVariable Long id, @RequestParam @NotNull @PositiveOrZero Integer expectedVersion) {
        return Result.success(service.archive(id, expectedVersion));
    }
}
