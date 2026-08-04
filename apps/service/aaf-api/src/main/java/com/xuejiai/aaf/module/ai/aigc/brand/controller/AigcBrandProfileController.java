package com.xuejiai.aaf.module.ai.aigc.brand.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.brand.domain.AigcBrandProfile;
import com.xuejiai.aaf.module.ai.aigc.brand.service.AigcBrandProfileService;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfilePageDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVersionCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVersionPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.brand.vo.AigcBrandProfileVersionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AIGC 品牌/IP 资料接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 品牌/IP 资料")
@RestController
@RequestMapping("/api/aigc/brand-profiles")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcBrandProfileController
        extends BaseCrudController<
                AigcBrandProfile,
                AigcBrandProfileVO,
                AigcBrandProfileCreateDTO,
                AigcBrandProfileUpdateDTO,
                AigcBrandProfilePageDTO> {

    private final AigcBrandProfileService service;

    @Override
    protected AigcBrandProfileService getService() {
        return service;
    }

    @Operation(summary = "查询品牌资料版本")
    @GetMapping("/{id}/versions")
    public Result<List<AigcBrandProfileVersionVO>> versions(@PathVariable Long id) {
        return Result.success(service.listVersions(id));
    }

    @Operation(summary = "创建品牌资料草稿版本")
    @PostMapping("/{id}/versions")
    public Result<AigcBrandProfileVersionVO> createVersion(
            @PathVariable Long id, @Valid @RequestBody AigcBrandProfileVersionCreateDTO command) {
        return Result.success(service.createVersion(id, command));
    }

    @Operation(summary = "发布品牌资料版本")
    @PostMapping("/{id}/versions/{versionId}/publish")
    public Result<AigcBrandProfileVersionVO> publishVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @Valid @RequestBody AigcBrandProfileVersionPublishDTO command) {
        return Result.success(service.publishVersion(id, versionId, command));
    }
}
