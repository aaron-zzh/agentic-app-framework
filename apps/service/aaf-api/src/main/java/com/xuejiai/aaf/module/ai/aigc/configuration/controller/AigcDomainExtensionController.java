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
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcDomainExtension;
import com.xuejiai.aaf.module.ai.aigc.configuration.service.AigcDomainExtensionService;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcDomainExtensionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AIGC 领域扩展接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 领域扩展")
@RestController
@RequestMapping("/api/aigc/domain-extensions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcDomainExtensionController
        extends BaseCrudController<
                AigcDomainExtension,
                AigcDomainExtensionVO,
                AigcDomainExtensionCreateDTO,
                AigcDomainExtensionUpdateDTO,
                AigcDomainExtensionPageDTO> {

    private final AigcDomainExtensionService service;

    @Override
    protected AigcDomainExtensionService getService() {
        return service;
    }

    @Operation(summary = "发布领域扩展版本")
    @PostMapping("/{id}/publish")
    public Result<AigcDomainExtensionVO> publish(
            @PathVariable Long id, @Valid @RequestBody AigcConfigurationPublishDTO command) {
        return Result.success(service.publish(id, command));
    }
}
