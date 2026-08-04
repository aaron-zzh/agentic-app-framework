package com.xuejiai.aaf.module.ai.aigc.configuration.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;
import com.xuejiai.aaf.module.ai.aigc.configuration.service.AigcProjectTypeService;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypeCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypeUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypeVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AIGC 项目类型接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 项目类型")
@RestController
@RequestMapping("/api/aigc/project-types")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcProjectTypeController
        extends BaseCrudController<
                AigcProjectType,
                AigcProjectTypeVO,
                AigcProjectTypeCreateDTO,
                AigcProjectTypeUpdateDTO,
                AigcProjectTypePageDTO> {

    private final AigcProjectTypeService service;

    @Override
    protected AigcProjectTypeService getService() {
        return service;
    }

    @Operation(summary = "发布项目类型版本")
    @PostMapping("/{id}/publish")
    public Result<AigcProjectTypeVO> publish(
            @PathVariable Long id,
            @Valid @RequestBody AigcConfigurationPublishDTO command) {
        return Result.success(service.publish(id, command));
    }
}
