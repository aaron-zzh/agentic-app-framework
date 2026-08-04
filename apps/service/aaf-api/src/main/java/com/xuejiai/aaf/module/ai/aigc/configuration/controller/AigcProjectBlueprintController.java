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
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;
import com.xuejiai.aaf.module.ai.aigc.configuration.service.AigcProjectBlueprintService;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectBlueprintVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AIGC 项目蓝图接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 项目蓝图")
@RestController
@RequestMapping("/api/aigc/project-blueprints")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcProjectBlueprintController
        extends BaseCrudController<
                AigcProjectBlueprint,
                AigcProjectBlueprintVO,
                AigcProjectBlueprintCreateDTO,
                AigcProjectBlueprintUpdateDTO,
                AigcProjectBlueprintPageDTO> {

    private final AigcProjectBlueprintService service;

    @Override
    protected AigcProjectBlueprintService getService() {
        return service;
    }

    @Operation(summary = "发布项目蓝图版本")
    @PostMapping("/{id}/publish")
    public Result<AigcProjectBlueprintVO> publish(
            @PathVariable Long id, @Valid @RequestBody AigcConfigurationPublishDTO command) {
        return Result.success(service.publish(id, command));
    }
}
