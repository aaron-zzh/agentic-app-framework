package com.xuejiai.aaf.module.ai.prompt.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** 提示词资产更新请求。 */
public record PromptTemplateUpdateDTO(
        @Size(max = 128) String name,
        @Size(max = 30) String type,
        @Size(max = 64) String category,
        Patch<String> coverUrl,
        @Size(max = 100_000) String prompt,
        @Size(max = 100_000) String negativePrompt,
        @Size(max = 100) String model,
        Integer width,
        Integer height,
        Integer steps,
        Long seed,
        Boolean isPublic,
        @Size(max = 20) String scope,
        @Size(max = 512) String description,
        @Schema(description = "模板变量名；为空且提示词变化时由服务端重新推导") @Size(max = 50)
                List<@Size(max = 64) String> variables) {

    public PromptTemplateUpdateDTO {
        coverUrl = coverUrl == null ? Patch.absent() : coverUrl;
    }

    @JsonCreator
    public static PromptTemplateUpdateDTO fromJson(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("category") String category,
            @JsonProperty("coverUrl") JsonNode coverUrl,
            @JsonProperty("prompt") String prompt,
            @JsonProperty("negativePrompt") String negativePrompt,
            @JsonProperty("model") String model,
            @JsonProperty("width") Integer width,
            @JsonProperty("height") Integer height,
            @JsonProperty("steps") Integer steps,
            @JsonProperty("seed") Long seed,
            @JsonProperty("isPublic") Boolean isPublic,
            @JsonProperty("scope") String scope,
            @JsonProperty("description") String description,
            @JsonProperty("variables") List<String> variables) {
        return new PromptTemplateUpdateDTO(
                name,
                type,
                category,
                Patch.parse(coverUrl, PromptTemplateUpdateDTO::decodeText),
                prompt,
                negativePrompt,
                model,
                width,
                height,
                steps,
                seed,
                isPublic,
                scope,
                description,
                variables);
    }

    @JsonIgnore
    @AssertTrue(message = "coverUrl 长度不能超过 1000")
    public boolean isCoverUrlValid() {
        var value = coverUrl.valueOrNull();
        return value == null || value.length() <= 1000;
    }

    private static String decodeText(JsonNode node) {
        if (!node.isString()) {
            throw new IllegalArgumentException("coverUrl 必须是字符串");
        }
        return node.asString();
    }
}
