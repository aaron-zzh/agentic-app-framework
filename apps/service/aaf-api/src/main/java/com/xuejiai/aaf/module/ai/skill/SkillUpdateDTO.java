package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 更新 Skill 根对象；任一版本字段非 null 时追加不可变版本。 */
public record SkillUpdateDTO(
        @Schema(description = "稳定业务码") @Size(min = 1, max = 100) String code,
        @Schema(description = "Skill 名称") @Size(min = 1, max = 128) String name,
        @Schema(description = "一句话摘要") @Size(min = 1, max = 512) String summary,
        @Schema(description = "默认语言地区") @Size(min = 1, max = 32) String locale,
        @Schema(description = "可见性：PRIVATE/WORKSPACE/PUBLIC")
                @Pattern(regexp = "PRIVATE|WORKSPACE|PUBLIC")
                String visibility,
        @Schema(description = "来源 Skill ID") Long sourceSkillId,
        @Schema(description = "规范 Markdown 正文；为空则继承上一版本") String content,
        @Schema(description = "输入 JSON Schema；为空则继承上一版本") String inputSchema,
        @Schema(description = "输出 JSON Schema；为空则继承上一版本") String outputSchema,
        @Schema(description = "输出契约；为空则继承上一版本") String outputContract,
        @Schema(description = "工具访问模式：RESTRICT/INHERIT") @Pattern(regexp = "RESTRICT|INHERIT")
                String toolAccessMode,
        @Schema(description = "工具需求；null 继承，空列表清空") @Valid
                List<SkillCreateDTO.ToolRequirementDTO> toolRequirements,
        @Schema(description = "模型能力需求；null 继承，空列表清空") @Valid
                List<SkillCreateDTO.ModelRequirementDTO> modelRequirements,
        @Schema(description = "版本变更摘要") @Size(max = 512) String changeSummary,
        @Schema(description = "新版本状态：DRAFT/IN_REVIEW/APPROVED/REJECTED/RETIRED")
                @Pattern(regexp = "DRAFT|IN_REVIEW|APPROVED|REJECTED|RETIRED")
                String status) {}
