package com.xuejiai.aaf.module.ai.aigc.media.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetTag;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcAssetTagService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 资产标签")
@RestController
@RequestMapping("/api/aigc/asset-tags")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcAssetTagController
        extends BaseCrudController<
                AigcAssetTag,
                AigcAssetTagVO,
                AigcAssetTagCreateDTO,
                AigcAssetTagUpdateDTO,
                AigcAssetTagPageDTO> {

    private final AigcAssetTagService service;

    @Override
    protected AigcAssetTagService getService() {
        return service;
    }
}
