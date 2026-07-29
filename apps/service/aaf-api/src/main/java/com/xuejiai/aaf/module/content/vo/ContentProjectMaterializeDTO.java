package com.xuejiai.aaf.module.content.vo;

import java.util.List;

import com.xuejiai.aaf.common.enums.content.ContentChannelEnum;
import com.xuejiai.aaf.common.enums.content.ContentProductionModeEnum;
import com.xuejiai.aaf.common.enums.content.ContentProjectTypeEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 内容项目物化请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "内容项目物化请求")
public record ContentProjectMaterializeDTO(
        @NotBlank @InEnum(ContentProjectTypeEnum.class) String projectTypeCode,
        @NotBlank String name,
        String brief,
        Long primaryBrandProfileId,
        List<Long> auxiliaryBrandProfileIds,
        List<@InEnum(ContentChannelEnum.class) String> channels,
        @InEnum(ContentProductionModeEnum.class) String productionMode,
        String domainExtensionCode,
        String blueprintCode) {

    public ContentProjectMaterializeDTO {
        auxiliaryBrandProfileIds =
                auxiliaryBrandProfileIds == null
                        ? List.of()
                        : List.copyOf(auxiliaryBrandProfileIds);
        channels = channels == null ? List.of() : List.copyOf(channels);
    }
}
