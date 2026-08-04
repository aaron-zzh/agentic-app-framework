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
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.service.AigcChannelSpecService;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecVO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPublishDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AIGC 渠道规格接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 渠道规格")
@RestController
@RequestMapping("/api/aigc/channel-specs")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcChannelSpecController
        extends BaseCrudController<
                AigcChannelSpec,
                AigcChannelSpecVO,
                AigcChannelSpecCreateDTO,
                AigcChannelSpecUpdateDTO,
                AigcChannelSpecPageDTO> {

    private final AigcChannelSpecService service;

    @Override
    protected AigcChannelSpecService getService() {
        return service;
    }

    @Operation(summary = "发布渠道规格版本")
    @PostMapping("/{id}/publish")
    public Result<AigcChannelSpecVO> publish(
            @PathVariable Long id, @Valid @RequestBody AigcConfigurationPublishDTO command) {
        return Result.success(service.publish(id, command));
    }
}
