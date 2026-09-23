package com.xuejiai.aaf.module.digitalhuman.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.digitalhuman.service.DigitalHumanVideoService;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVideoVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 数字人视频管理
 * @author Kiro
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "数字人视频管理")
@RestController
@RequestMapping("/api/digital-humans/{digitalHumanId}/videos")
@RequiredArgsConstructor
public class DigitalHumanVideoController {

    private final DigitalHumanVideoService videoService;

    @Operation(summary = "获取数字人的视频历史列表")
    @GetMapping
    public Result<Page<DigitalHumanVideoVO>> listVideos(
            @PathVariable Long digitalHumanId,
            @Parameter(description = "项目名称（模糊查询）") @RequestParam(required = false) String projectName,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return Result.success(videoService.listVideos(digitalHumanId, projectName, pageable));
    }

    @Operation(summary = "获取视频详情")
    @GetMapping("/{videoId}")
    public Result<DigitalHumanVideoVO> getVideo(
            @PathVariable Long digitalHumanId,
            @PathVariable Long videoId
    ) {
        return Result.success(videoService.getVideo(digitalHumanId, videoId));
    }

    @Operation(summary = "删除视频记录", description = "仅删除关联记录，不删除实际视频文件")
    @DeleteMapping("/{videoId}")
    public Result<Void> deleteVideo(
            @PathVariable Long digitalHumanId,
            @PathVariable Long videoId
    ) {
        videoService.deleteVideo(digitalHumanId, videoId);
        return Result.success();
    }
}
