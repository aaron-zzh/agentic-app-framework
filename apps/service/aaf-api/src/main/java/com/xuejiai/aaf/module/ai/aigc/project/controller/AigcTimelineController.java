package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcTimeline;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcTimelineService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcClipCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcClipVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelinePageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTrackCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTrackVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

/** AIGC 时间轴接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 时间轴")
@RestController
@RequestMapping("/api/aigc/timelines")
@RequiredArgsConstructor
public class AigcTimelineController
        extends BaseCrudController<
                AigcTimeline,
                AigcTimelineVO,
                AigcTimelineCreateDTO,
                AigcTimelineUpdateDTO,
                AigcTimelinePageDTO> {

    private final AigcTimelineService service;

    @Override
    protected BaseCrudService<
                    AigcTimeline,
                    AigcTimelineVO,
                    AigcTimelineCreateDTO,
                    AigcTimelineUpdateDTO,
                    AigcTimelinePageDTO>
            getService() {
        return service;
    }

    @Operation(summary = "查询时间轴所有轨道及片段（剪辑工作台加载）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/tracks")
    public Result<List<AigcTrackVO>> getTracks(@PathVariable Long id) {
        return Result.success(service.getTracks(id));
    }

    @Operation(summary = "新增轨道")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/tracks")
    public Result<AigcTrackVO> addTrack(
            @PathVariable Long id, @RequestBody AigcTrackCreateDTO dto) {
        return Result.success(service.addTrack(dto));
    }

    @Operation(summary = "删除轨道（同时删除轨道下所有片段）")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/tracks/{trackId}")
    public Result<Void> deleteTrack(@PathVariable Long id, @PathVariable Long trackId) {
        service.deleteTrack(trackId);
        return Result.success();
    }

    @Operation(summary = "新增片段")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/tracks/{trackId}/clips")
    public Result<AigcClipVO> addClip(
            @PathVariable Long id, @PathVariable Long trackId, @RequestBody AigcClipCreateDTO dto) {
        return Result.success(service.addClip(dto));
    }

    @Operation(summary = "删除片段")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/tracks/{trackId}/clips/{clipId}")
    public Result<Void> deleteClip(
            @PathVariable Long id, @PathVariable Long trackId, @PathVariable Long clipId) {
        service.deleteClip(clipId);
        return Result.success();
    }

    @Operation(summary = "更新片段位置（拖拽）")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{id}/tracks/{trackId}/clips/{clipId}/position")
    public Result<AigcClipVO> updateClipPosition(
            @PathVariable Long id,
            @PathVariable Long trackId,
            @PathVariable Long clipId,
            @RequestParam Long positionMs,
            @RequestParam Long inMs,
            @RequestParam Long outMs) {
        return Result.success(service.updateClipPosition(clipId, positionMs, inMs, outMs));
    }
}
