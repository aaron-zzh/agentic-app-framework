package com.xuejiai.aaf.module.ui.tracking;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 行为数据上报与分析接口。
 */
@Tag(name = "行为追踪")
@RestController
@RequestMapping("/api/ui/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @Operation(summary = "批量上报埋点事件")
    @PostMapping("/events")
    public Result<Integer> reportEvents(@Validated @RequestBody TrackingEventDTO dto) {
        return Result.success(trackingService.saveEvents(dto));
    }

    @Operation(summary = "获取热力图数据")
    @GetMapping("/heatmap")
    public Result<HeatmapVO> heatmap(@RequestParam String page) {
        return Result.success(trackingService.getHeatmap(page));
    }

    @Operation(summary = "获取操作模式识别结果")
    @GetMapping("/patterns")
    public Result<List<PatternVO>> patterns() {
        return Result.success(trackingService.getPatterns());
    }
}
