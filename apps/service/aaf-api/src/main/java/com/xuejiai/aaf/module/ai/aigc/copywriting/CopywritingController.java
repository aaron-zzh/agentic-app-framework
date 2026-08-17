package com.xuejiai.aaf.module.ai.aigc.copywriting;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** 文案资产查询接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "文案生成")
@RestController
@RequestMapping("/api/aigc/copywriting")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class CopywritingController {

    private final CopywritingService copywritingService;

    @Operation(summary = "分页查询文案资产")
    @GetMapping("/assets")
    public Result<CopywritingAssetPageVO> assets(
            @RequestParam(defaultValue = "ALL") CopywritingLinkStatus linkStatus,
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return Result.success(
                copywritingService.assets(linkStatus, projectId, keyword, pageNo, pageSize));
    }
}
