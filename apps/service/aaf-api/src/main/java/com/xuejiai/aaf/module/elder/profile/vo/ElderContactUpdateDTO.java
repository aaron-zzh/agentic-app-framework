package com.xuejiai.aaf.module.elder.profile.vo;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 长者联系人更新请求；业务字段使用 Patch 区分缺失、null 与设置值。 */
@Schema(description = "更新长者联系人")
public record ElderContactUpdateDTO(
        Patch<Long> elderId,
        Patch<Long> userId,
        Patch<String> name,
        Patch<String> mobile,
        Patch<String> relationship,
        Patch<Integer> priority,
        Patch<Boolean> emergencyContact,
        Patch<Set<String>> authorizationScopes,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public ElderContactUpdateDTO {
        elderId = normalize(elderId);
        userId = normalize(userId);
        name = normalize(name);
        mobile = normalize(mobile);
        relationship = normalize(relationship);
        priority = normalize(priority);
        emergencyContact = normalize(emergencyContact);
        authorizationScopes = normalize(authorizationScopes);
    }

    @JsonCreator
    public static ElderContactUpdateDTO fromJson(
            @JsonProperty("elderId") JsonNode elderId,
            @JsonProperty("userId") JsonNode userId,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("mobile") JsonNode mobile,
            @JsonProperty("relationship") JsonNode relationship,
            @JsonProperty("priority") JsonNode priority,
            @JsonProperty("emergencyContact") JsonNode emergencyContact,
            @JsonProperty("authorizationScopes") JsonNode authorizationScopes,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ElderContactUpdateDTO(
                Patch.parse(elderId, node -> ProfileJsonDecoder.longValue(node, "长者 ID")),
                Patch.parse(userId, node -> ProfileJsonDecoder.longValue(node, "关联用户 ID")),
                Patch.parse(name, node -> ProfileJsonDecoder.text(node, "联系人姓名")),
                Patch.parse(mobile, node -> ProfileJsonDecoder.text(node, "联系电话")),
                Patch.parse(relationship, node -> ProfileJsonDecoder.text(node, "联系人关系")),
                Patch.parse(priority, node -> ProfileJsonDecoder.integer(node, "联系优先级")),
                Patch.parse(emergencyContact, node -> ProfileJsonDecoder.bool(node, "紧急联系人")),
                Patch.parse(authorizationScopes, node -> ProfileJsonDecoder.textSet(node, "授权范围")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
