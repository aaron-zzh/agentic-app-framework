package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalDraft;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalRevisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationStartCommand;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectGenerationMaterializationService;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectGenerationProposalService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGenerationProposalItemDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * 物化前创建草稿的生成 Proposal 入口。
 *
 * <p>项目尚不存在，因此身份是服务端签发的 draftId + 已发布 Blueprint 版本；与项目域共用同一 Proposal 内核、同一 Approval 合同与同一受控
 * canonical Task 入口，不建第二条路径。草稿只允许初始化字段目标。
 */
@Tag(name = "AIGC 项目创建草稿")
@RestController
@RequestMapping("/api/aigc/project-creation-drafts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcProjectCreationDraftController {

    private static final String USER_PROPOSAL_SOURCE_TYPE = "USER";

    private final AigcProjectGenerationProposalService proposals;
    private final AigcProjectGenerationMaterializationService generations;

    @Operation(summary = "查询创建草稿下的生成 Proposal 列表")
    @GetMapping("/{draftId}/generation-proposals")
    public Result<?> listProposals(@PathVariable String draftId) {
        return Result.success(proposals.listDraft(draftId));
    }

    @Operation(summary = "创建物化前初始化字段的待确认 Proposal；首次调用由服务端签发 draftId")
    @PostMapping("/generation-proposals")
    public Result<?> propose(@Valid @RequestBody CreationDraftProposalDTO request) {
        return Result.success(
                proposals.propose(
                        new AigcProjectGenerationProposalDraft(
                                AigcGenerationProposalScope.creationDraft(
                                        request.draftId(), request.blueprintVersionId()),
                                null,
                                USER_PROPOSAL_SOURCE_TYPE,
                                null,
                                request.confirmationPolicy(),
                                null,
                                request.expiresAt(),
                                proposalItems(request.items()))));
    }

    @Operation(summary = "修订草稿 Proposal 并产生新版本")
    @PostMapping("/generation-proposals/_revise")
    public Result<?> revise(@Valid @RequestBody CreationDraftProposalReviseDTO request) {
        return Result.success(
                proposals.revise(
                        new AigcProjectGenerationProposalRevisionCommand(
                                AigcGenerationProposalScope.creationDraft(
                                        request.draftId(), request.blueprintVersionId()),
                                request.proposalId(),
                                request.proposalVersion(),
                                request.proposalHash(),
                                null,
                                null,
                                request.confirmationPolicy(),
                                null,
                                request.expiresAt(),
                                proposalItems(request.items()))));
    }

    @Operation(summary = "确认草稿 Proposal 并在草稿会话启动唯一 canonical Task")
    @PostMapping("/generation-proposals/_approve-and-start")
    public Result<?> approveAndStart(@Valid @RequestBody CreationDraftStartDTO request) {
        return Result.success(
                generations.approveAndStart(
                        new AigcProjectGenerationStartCommand(
                                AigcGenerationProposalScope.creationDraft(
                                        request.draftId(), request.blueprintVersionId()),
                                null,
                                request.proposalId(),
                                request.proposalVersion(),
                                request.proposalHash(),
                                request.approvedTargetKeys(),
                                request.approvalIdempotencyKey(),
                                request.assistantId())));
    }

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

    /** 首次提议省略 draftId，由服务端签发；后续提议复用同一 draftId 归集。 */
    public record CreationDraftProposalDTO(
            String draftId,
            @NotNull @Positive Long blueprintVersionId,
            String confirmationPolicy,
            LocalDateTime expiresAt,
            @NotEmpty List<@NotNull @Valid AigcProjectGenerationProposalItemDTO> items) {}

    public record CreationDraftProposalReviseDTO(
            @NotBlank String draftId,
            @NotNull @Positive Long blueprintVersionId,
            @NotBlank String proposalId,
            @NotNull Integer proposalVersion,
            @NotBlank String proposalHash,
            String confirmationPolicy,
            LocalDateTime expiresAt,
            @NotEmpty List<@NotNull @Valid AigcProjectGenerationProposalItemDTO> items) {}

    /** 草稿域没有项目受控会话，因此必须显式指定 canonical Assistant code。 */
    public record CreationDraftStartDTO(
            @NotBlank String draftId,
            @NotNull @Positive Long blueprintVersionId,
            @NotBlank String proposalId,
            @NotNull Integer proposalVersion,
            @NotBlank String proposalHash,
            @NotEmpty List<@NotBlank String> approvedTargetKeys,
            @NotBlank String approvalIdempotencyKey,
            @NotBlank String assistantId) {}
}
