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

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalScope;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalDraft;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationProposalRevisionCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGenerationStartCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintCreationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalCommands;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectGenerationMaterializationService;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectGenerationProposalService;
import com.xuejiai.aaf.module.ai.aigc.project.service.AssistantBlueprintPlanningService;
import com.xuejiai.aaf.module.ai.aigc.project.service.AssistantBlueprintProposalService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectGenerationProposalItemDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    private final AssistantBlueprintPlanningService blueprintPlanning;
    private final AssistantBlueprintProposalService blueprintProposals;

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

    @Operation(summary = "启动 Assistant Blueprint 规划并预绑定 canonical Task")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_CREATE)
    @PostMapping("/blueprint-proposals/_start")
    public Result<?> startBlueprintProposal(@Valid @RequestBody BlueprintProposalStartDTO request) {
        return Result.success(
                blueprintPlanning.start(
                        new AssistantBlueprintProposalCommands.Start(
                                request.assistantId(),
                                request.instruction(),
                                request.creationContext())));
    }

    @Operation(summary = "查询当前工作区的 Assistant Blueprint Proposal")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_CREATE)
    @GetMapping("/blueprint-proposals")
    public Result<?> listBlueprintProposals() {
        return Result.success(blueprintProposals.listCurrent());
    }

    @Operation(summary = "修订 Assistant Blueprint Proposal 并启动新规划版本")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_CREATE)
    @PostMapping("/blueprint-proposals/_revise")
    public Result<?> reviseBlueprintProposal(
            @Valid @RequestBody BlueprintProposalReviseDTO request) {
        return Result.success(
                blueprintPlanning.revise(
                        new AssistantBlueprintProposalCommands.Revise(
                                request.proposalId(),
                                request.proposalVersion(),
                                request.proposalHash(),
                                request.instruction())));
    }

    @Operation(summary = "确认 Assistant Blueprint Proposal")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_CREATE)
    @PostMapping("/blueprint-proposals/_confirm")
    public Result<?> confirmBlueprintProposal(
            @Valid @RequestBody BlueprintProposalConfirmDTO request) {
        return Result.success(
                blueprintProposals.confirm(
                        new AssistantBlueprintProposalCommands.Confirm(
                                request.proposalId(),
                                request.proposalVersion(),
                                request.proposalHash())));
    }

    @Operation(summary = "取消 Assistant Blueprint Proposal 及其规划 Task")
    @PreAuthorize(AigcAuthorities.HAS_PROJECT_CREATE)
    @PostMapping("/blueprint-proposals/_cancel")
    public Result<?> cancelBlueprintProposal(
            @Valid @RequestBody BlueprintProposalCancelDTO request) {
        return Result.success(
                blueprintPlanning.cancel(
                        new AssistantBlueprintProposalCommands.Cancel(
                                request.proposalId(),
                                request.proposalVersion(),
                                request.proposalHash())));
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

    public record BlueprintProposalStartDTO(
            @NotBlank @Size(max = 128) String assistantId,
            @NotBlank @Size(max = 4000) String instruction,
            @NotNull @Valid AssistantBlueprintCreationContext creationContext) {

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("Blueprint Proposal start 不接受字段: " + field);
        }
    }

    public record BlueprintProposalReviseDTO(
            @NotBlank String proposalId,
            @NotNull @Positive Integer proposalVersion,
            @NotBlank String proposalHash,
            @NotBlank @Size(max = 4000) String instruction) {

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("Blueprint Proposal revise 不接受字段: " + field);
        }
    }

    /** 确认请求只允许 proposalId/version/hash，不接受 Snapshot、sourceRef 或绑定身份。 */
    public record BlueprintProposalConfirmDTO(
            @NotBlank String proposalId,
            @NotNull @Positive Integer proposalVersion,
            @NotBlank String proposalHash) {

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException(
                    "Blueprint Proposal 确认只接受 proposalId/version/hash，不接受: " + field);
        }
    }

    /** 取消请求只接受精确 Proposal 身份；PLANNING 尚未签发 hash 时允许 proposalHash 为 null。 */
    public record BlueprintProposalCancelDTO(
            @NotBlank String proposalId,
            @NotNull @Positive Integer proposalVersion,
            String proposalHash) {

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException(
                    "Blueprint Proposal 取消只接受 proposalId/version/hash，不接受: " + field);
        }
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
