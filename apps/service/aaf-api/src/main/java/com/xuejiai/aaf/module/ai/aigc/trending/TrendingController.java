package com.xuejiai.aaf.module.ai.aigc.trending;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 热点搜索接口——联网实时抓取当前热榜并结构化返回。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "热点搜索")
@RestController
@RequestMapping("/api/aigc/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingService trendingService;

    @Operation(summary = "获取当前热点列表（20条）")
    @GetMapping
    public Result<List<TrendingItem>> list() {
        return Result.success(trendingService.fetchTrending());
    }
}
