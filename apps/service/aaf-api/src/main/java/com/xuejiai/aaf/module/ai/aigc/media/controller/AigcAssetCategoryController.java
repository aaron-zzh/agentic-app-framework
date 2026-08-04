package com.xuejiai.aaf.module.ai.aigc.media.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAssetCategory;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcAssetCategoryService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetCategoryVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 资产分类")
@RestController
@RequestMapping("/api/aigc/asset-categories")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcAssetCategoryController
        extends BaseCrudController<
                AigcAssetCategory,
                AigcAssetCategoryVO,
                AigcAssetCategoryCreateDTO,
                AigcAssetCategoryUpdateDTO,
                AigcAssetCategoryPageDTO> {

    private final AigcAssetCategoryService service;

    @Override
    protected AigcAssetCategoryService getService() {
        return service;
    }
}
