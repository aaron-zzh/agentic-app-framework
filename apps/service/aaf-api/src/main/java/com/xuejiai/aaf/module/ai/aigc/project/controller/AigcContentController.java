package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcContent;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcContentService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentAssetVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcContentVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

/** AIGC 内容产出接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 内容产出")
@RestController
@RequestMapping("/api/aigc/contents")
@RequiredArgsConstructor
public class AigcContentController
        extends BaseCrudController<
                AigcContent,
                AigcContentVO,
                AigcContentCreateDTO,
                AigcContentUpdateDTO,
                AigcContentPageDTO> {

    private final AigcContentService service;

    @Override
    protected BaseCrudService<
                    AigcContent,
                    AigcContentVO,
                    AigcContentCreateDTO,
                    AigcContentUpdateDTO,
                    AigcContentPageDTO>
            getService() {
        return service;
    }

    @Operation(summary = "发布内容")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/publish")
    public Result<AigcContentVO> publish(@PathVariable Long id) {
        return Result.success(service.publish(id));
    }

    @Operation(summary = "查询内容素材列表")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/assets")
    public Result<List<AigcContentAssetVO>> listAssets(@PathVariable Long id) {
        return Result.success(service.listAssets(id));
    }

    @Operation(summary = "为内容添加素材")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/assets")
    public Result<AigcContentAssetVO> addAsset(
            @PathVariable Long id,
            @RequestParam Long assetId,
            @RequestParam(defaultValue = "MAIN") String role) {
        return Result.success(service.addAsset(id, assetId, role));
    }

    @Operation(summary = "移除内容素材")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/assets/{assetId}")
    public Result<Void> removeAsset(@PathVariable Long id, @PathVariable Long assetId) {
        service.removeAsset(id, assetId);
        return Result.success();
    }
}
