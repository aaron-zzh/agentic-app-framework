package com.xuejiai.aaf.module.ai.aigc.media.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.media.domain.AigcMedia;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcMediaService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetSaveDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcAssetVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** AIGC 媒体管理接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 媒体")
@RestController
@RequestMapping("/api/aigc/media")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Validated
public class AigcMediaController
        extends BaseCrudController<
                AigcMedia, AigcMediaVO, Void, AigcMediaUpdateDTO, AigcMediaPageDTO> {

    private final AigcMediaService mediaService;
    private final AigcAssetService assetService;

    @Override
    protected AigcMediaService getService() {
        return mediaService;
    }

    @Operation(summary = "将媒体保存到资产库")
    @PreAuthorize("hasAuthority('aigc:asset:create')")
    @PostMapping("/{id}/asset")
    public Result<AigcAssetVO> saveAsAsset(
            @PathVariable Long id, @RequestBody(required = false) AigcAssetSaveDTO request) {
        return Result.success(assetService.saveFromMedia(id, request));
    }
}
