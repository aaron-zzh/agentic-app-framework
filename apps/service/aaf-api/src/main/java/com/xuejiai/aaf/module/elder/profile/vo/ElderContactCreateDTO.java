package com.xuejiai.aaf.module.elder.profile.vo;

import java.util.Set;

import com.xuejiai.aaf.common.enums.elder.ContactRelationshipEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 长者联系人创建请求。 */
@Schema(description = "创建长者联系人")
public record ElderContactCreateDTO(
        @NotNull @Positive @Schema(description = "长者 ID") Long elderId,
        @Positive @Schema(description = "可选绑定的 AAF 用户 ID") Long userId,
        @NotBlank @Size(max = 100) @Schema(description = "联系人姓名") String name,
        @NotBlank @Size(max = 32) @Schema(description = "联系电话") String mobile,
        @NotBlank @InEnum(value = ContactRelationshipEnum.class, message = "联系人关系必须是 {value}")
                String relationship,
        @PositiveOrZero @Schema(description = "联系优先级，数值越小越优先") Integer priority,
        @Schema(description = "是否紧急联系人") Boolean emergencyContact,
        @NotNull @Size(max = 7) @Schema(description = "授权范围编码") Set<String> authorizationScopes) {}
