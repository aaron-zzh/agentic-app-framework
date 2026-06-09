package com.xuejiai.aaf.module.system.dashboard.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.dashboard.service.DashboardService;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardUpdateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardVO;
import com.xuejiai.aaf.module.system.dashboard.vo.WidgetDataVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 仪表盘接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/api/system/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final OperatorContext operatorContext;

    @Operation(summary = "查询当前用户仪表盘列表")
    @GetMapping
    public Result<List<DashboardVO>> list() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(dashboardService.listByOwner(userId));
    }

    @Operation(summary = "查询当前用户默认仪表盘")
    @GetMapping("/default")
    public Result<DashboardVO> getDefault() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(dashboardService.getDefault(userId));
    }

    @Operation(summary = "查询仪表盘详情")
    @GetMapping("/{id}")
    public Result<DashboardVO> get(@PathVariable Long id) {
        return Result.success(dashboardService.getById(id));
    }

    @Operation(summary = "创建仪表盘")
    @PostMapping
    public Result<DashboardVO> create(@Validated @RequestBody DashboardCreateDTO dto) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(dashboardService.create(userId, dto));
    }

    @Operation(summary = "更新仪表盘")
    @PutMapping("/{id}")
    public Result<DashboardVO> update(
            @PathVariable Long id, @Validated @RequestBody DashboardUpdateDTO dto) {
        return Result.success(dashboardService.update(id, dto));
    }

    @Operation(summary = "删除仪表盘")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dashboardService.delete(id);
        return Result.success();
    }

    @Operation(summary = "查询组件数据")
    @PostMapping("/widgets/{widgetId}/data")
    public Result<WidgetDataVO> getWidgetData(
            @PathVariable Long widgetId, @RequestBody(required = false) Object config) {
        return Result.success(dashboardService.getWidgetData(widgetId));
    }
}
