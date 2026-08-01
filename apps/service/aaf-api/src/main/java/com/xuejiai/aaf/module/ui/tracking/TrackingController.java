package com.xuejiai.aaf.module.ui.tracking;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
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

/** 行为数据上报与分析接口。 */
@Tag(name = "行为追踪")
@RestController
@RequestMapping("/api/ui/tracking")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TrackingController {

    private final TrackingService trackingService;

    /**
     * M19：分析视图的组织过滤 orgId——平台管理员返回 null（全局），组织管理员强制当前组织。
     *
     * <p>ORG_ADMIN 不再等同平台管理员，避免埋点分析成为跨组织行为数据的读取通道。
     */
    private Long filterOrgId() {
        var auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        boolean platformAdmin =
                auth != null
                        && auth.getAuthorities().stream()
                                .anyMatch(
                                        a ->
                                                java.util.Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
                                                        .contains(a.getAuthority()));
        if (platformAdmin) return null;
        var orgId = com.xuejiai.aaf.framework.org.OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new com.xuejiai.aaf.common.exception.BusinessException(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.FORBIDDEN, "缺少组织上下文，无权查看行为分析");
        }
        return orgId;
    }

    @Operation(summary = "批量上报埋点事件")
    @PostMapping("/events")
    public Result<Integer> reportEvents(@Validated @RequestBody TrackingEventDTO dto) {
        return Result.success(trackingService.saveEvents(dto));
    }

    @Operation(summary = "获取热力图数据")
    @GetMapping("/heatmap")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORG_ADMIN')")
    public Result<HeatmapVO> heatmap(@RequestParam String page) {
        return Result.success(trackingService.getHeatmap(page, filterOrgId()));
    }

    @Operation(summary = "获取操作模式识别结果")
    @GetMapping("/patterns")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORG_ADMIN')")
    public Result<List<PatternVO>> patterns() {
        return Result.success(trackingService.getPatterns(filterOrgId()));
    }
}
