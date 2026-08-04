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
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectTypePackage;
import com.xuejiai.aaf.module.ai.aigc.configuration.service.AigcProjectTypePackageService;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackageCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackagePageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackageUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypePackageVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AIGC 项目类型兼容包接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 项目类型兼容包")
@RestController
@RequestMapping("/api/aigc/project-type-packages")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcProjectTypePackageController
        extends BaseCrudController<
                AigcProjectTypePackage,
                AigcProjectTypePackageVO,
                AigcProjectTypePackageCreateDTO,
                AigcProjectTypePackageUpdateDTO,
                AigcProjectTypePackagePageDTO> {

    private final AigcProjectTypePackageService service;

    @Override
    protected AigcProjectTypePackageService getService() {
        return service;
    }

    @Operation(summary = "发布项目类型兼容包")
    @PreAuthorize("hasAuthority('aigc:project-type-package:publish')")
    @PostMapping("/{id}/publish")
    public Result<AigcProjectTypePackageVO> publish(
            @PathVariable Long id, @Valid @RequestBody AigcConfigurationPublishDTO command) {
        return Result.success(service.publish(id, command));
    }
}
