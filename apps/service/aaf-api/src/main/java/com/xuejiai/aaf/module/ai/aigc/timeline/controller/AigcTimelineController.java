package com.xuejiai.aaf.module.ai.aigc.timeline.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineClipInput;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineCreateCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineDeleteCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineReplaceCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineTrackInput;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineView;
import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineComposition;
import com.xuejiai.aaf.module.ai.aigc.timeline.service.AigcTimelineService;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcStoryboardExportVO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelineCompositionVO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelineCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelineDeleteDTO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelinePageDTO;
import com.xuejiai.aaf.module.ai.aigc.timeline.vo.AigcTimelineReplaceDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AIGC 轻时间线")
@RestController
@RequestMapping("/api/aigc/timelines")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AigcTimelineController
        extends BaseCrudController<
                AigcTimelineComposition,
                AigcTimelineCompositionVO,
                Void,
                Void,
                AigcTimelinePageDTO> {

    private final AigcTimelineService service;

    @Override
    protected AigcTimelineService getService() {
        return service;
    }

    @Operation(summary = "创建 Timeline Composition")
    @PreAuthorize(AigcAuthorities.HAS_TIMELINE_CREATE)
    @PostMapping("/_create")
    public Result<AigcTimelineView> createComposition(
            @Validated @RequestBody AigcTimelineCreateDTO request) {
        return Result.success(
                service.create(
                        new AigcTimelineCreateCommand(
                                request.projectId(),
                                request.expectedProjectVersion(),
                                request.deliverableObjectId(),
                                request.title(),
                                request.durationMs(),
                                request.frameRate(),
                                request.width(),
                                request.height())));
    }

    @Operation(summary = "读取完整 Timeline Composition")
    @PreAuthorize(AigcAuthorities.HAS_TIMELINE_READ)
    @GetMapping("/{id}/composition")
    public Result<AigcTimelineCompositionVO> composition(@PathVariable Long id) {
        return Result.success(service.composition(id));
    }

    @Operation(summary = "整体替换 Timeline Track 与 Clip")
    @PreAuthorize(AigcAuthorities.HAS_TIMELINE_UPDATE)
    @PutMapping("/{id}/composition")
    public Result<AigcTimelineView> replaceComposition(
            @PathVariable Long id, @Validated @RequestBody AigcTimelineReplaceDTO request) {
        var tracks =
                request.tracks().stream()
                        .map(
                                track ->
                                        new AigcTimelineTrackInput(
                                                track.trackType(),
                                                track.name(),
                                                track.orderNo(),
                                                track.muted(),
                                                track.locked(),
                                                track.clips().stream()
                                                        .map(
                                                                clip ->
                                                                        new AigcTimelineClipInput(
                                                                                clip
                                                                                        .mediaVersionId(),
                                                                                clip
                                                                                        .sourceObjectId(),
                                                                                clip
                                                                                        .sourceObjectVersionId(),
                                                                                clip.positionMs(),
                                                                                clip.inMs(),
                                                                                clip.outMs(),
                                                                                clip
                                                                                        .propertiesJson(),
                                                                                clip
                                                                                        .transitionJson(),
                                                                                clip.volume()))
                                                        .toList()))
                        .toList();
        return Result.success(
                service.replaceComposition(
                        new AigcTimelineReplaceCommand(
                                request.projectId(),
                                id,
                                request.expectedProjectVersion(),
                                request.expectedVersion(),
                                tracks)));
    }

    @Operation(summary = "查询只读 Storyboard 导出记录")
    @PreAuthorize(AigcAuthorities.HAS_TIMELINE_READ)
    @GetMapping("/{id}/storyboard-exports")
    public Result<List<AigcStoryboardExportVO>> storyboardExports(@PathVariable Long id) {
        return Result.success(service.storyboardExportVOs(id));
    }

    @Operation(summary = "删除 Timeline Composition")
    @PreAuthorize(AigcAuthorities.HAS_TIMELINE_DELETE)
    @PostMapping("/{id}/_delete")
    public Result<Void> deleteComposition(
            @PathVariable Long id, @Validated @RequestBody AigcTimelineDeleteDTO request) {
        service.deleteComposition(
                new AigcTimelineDeleteCommand(
                        request.projectId(),
                        id,
                        request.expectedProjectVersion(),
                        request.expectedVersion()));
        return Result.success();
    }
}
