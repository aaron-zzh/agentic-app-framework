package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 创建 Skill 根对象及首个不可变版本。 */
public record SkillCreateDTO(
        @Schema(description = "稳定业务码；为空时由后端生成") @Size(max = 100) String code,
        @Schema(description = "Skill 名称") @NotBlank @Size(max = 128) String name,
        @Schema(description = "一句话摘要") @NotBlank @Size(max = 512) String summary,
        @Schema(description = "实例输入提示词，仅用于界面 placeholder") @Size(max = 2000) String instancePrompt,
        @Schema(description = "默认语言地区") @Size(max = 32) String locale,
        @Schema(description = "可见性：PRIVATE/WORKSPACE/PUBLIC")
                @Pattern(regexp = "PRIVATE|WORKSPACE|PUBLIC")
                String visibility,
        @Schema(description = "来源 Skill ID") Long sourceSkillId,
        @Schema(description = "分类业务码集合") @Valid
                List<@NotBlank @Size(max = 64) String> categoryCodes,
        @Schema(description = "规范 Markdown 正文") @NotBlank String content,
        @Schema(description = "输入 JSON Schema") String inputSchema,
        @Schema(description = "输出 JSON Schema") String outputSchema,
        @Schema(description = "输出契约") String outputContract,
        @Schema(description = "工具访问模式：RESTRICT/INHERIT") @Pattern(regexp = "RESTRICT|INHERIT")
                String toolAccessMode,
        @Schema(description = "工具需求") @Valid List<ToolRequirementDTO> toolRequirements,
        @Schema(description = "模型能力需求") @Valid List<ModelRequirementDTO> modelRequirements,
        @Schema(description = "版本变更摘要") @Size(max = 512) String changeSummary,
        @Schema(description = "版本状态：DRAFT/IN_REVIEW/APPROVED/REJECTED/RETIRED")
                @Pattern(regexp = "DRAFT|IN_REVIEW|APPROVED|REJECTED|RETIRED")
                String status) {

    /** 不可变版本的工具需求。 */
    public record ToolRequirementDTO(
            @NotBlank @Size(max = 128) String toolId,
            Long toolVersion,
            @NotBlank @Size(max = 128) String toolName,
            Boolean required,
            @NotBlank @Size(max = 512) String usagePurpose,
            @PositiveOrZero Integer sortOrder) {}

    /** 不可变版本的模型能力需求。 */
    public record ModelRequirementDTO(
            @NotBlank @Size(max = 32) String capability,
            Boolean required,
            @PositiveOrZero Integer minimumContextTokens,
            @NotNull @Size(max = 512) String rationale) {}
}
