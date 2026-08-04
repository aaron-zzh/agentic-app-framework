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
import com.xuejiai.aaf.module.ai.aigc.media.domain.Media;
import com.xuejiai.aaf.module.ai.aigc.media.service.AssetService;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetSaveDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AssetVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaPageDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaVO;

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
public class MediaController
        extends BaseCrudController<Media, MediaVO, Void, MediaUpdateDTO, MediaPageDTO> {

    private final MediaService mediaService;
    private final AssetService assetService;

    @Override
    protected MediaService getService() {
        return mediaService;
    }

    @Operation(summary = "将媒体保存到资产库")
    @PostMapping("/{id}/asset")
    public Result<AssetVO> saveAsAsset(
            @PathVariable Long id, @RequestBody(required = false) AssetSaveDTO request) {
        return Result.success(assetService.saveFromMedia(id, request));
    }
}
