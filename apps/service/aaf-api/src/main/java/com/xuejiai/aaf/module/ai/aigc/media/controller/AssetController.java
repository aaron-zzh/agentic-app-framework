package com.xuejiai.aaf.module.ai.aigc.media.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.media.domain.Asset;
import com.xuejiai.aaf.module.ai.aigc.media.service.AssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** AIGC 资产管理接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 资产")
@RestController
@RequestMapping("/api/aigc/assets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Validated
public class AssetController
        extends BaseCrudController<Asset, AssetVO, Void, AssetUpdateDTO, AssetPageDTO> {

    private final AssetService assetService;

    @Override
    protected AssetService getService() {
        return assetService;
    }
}
