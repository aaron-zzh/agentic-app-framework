package com.xuejiai.aaf.module.ai.aigc.media.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcAsset;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetTagsDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AIGC 资产管理接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 资产")
@RestController
@RequestMapping("/api/aigc/assets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Validated
public class AigcAssetController
        extends BaseCrudController<
                AigcAsset, AigcAssetVO, Void, AigcAssetUpdateDTO, AigcAssetPageDTO> {

    private final AigcAssetService assetService;

    @Override
    protected AigcAssetService getService() {
        return assetService;
    }

    @Operation(summary = "查询资产标签")
    @PreAuthorize("hasAuthority('aigc:asset:read')")
    @GetMapping("/{id}/tags")
    public Result<List<AigcAssetTagVO>> tags(@PathVariable Long id) {
        return Result.success(assetService.tags(id));
    }

    @Operation(summary = "整体替换资产标签")
    @PreAuthorize("hasAuthority('aigc:asset:tag')")
    @PutMapping("/{id}/tags")
    public Result<List<AigcAssetTagVO>> replaceTags(
            @PathVariable Long id, @Valid @RequestBody AigcAssetTagsDTO command) {
        return Result.success(assetService.replaceTags(id, command));
    }
}
