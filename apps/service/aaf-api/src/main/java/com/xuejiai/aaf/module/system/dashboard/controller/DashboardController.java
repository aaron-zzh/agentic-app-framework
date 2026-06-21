package com.xuejiai.aaf.module.system.dashboard.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
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
import com.xuejiai.aaf.framework.protection.RateLimit;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.dashboard.domain.DashboardPreset;
import com.xuejiai.aaf.module.system.dashboard.repository.DashboardPresetRepository;
import com.xuejiai.aaf.module.system.dashboard.service.DashboardService;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardLayoutDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardPresetCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardPresetUpdateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.DashboardPresetVO;
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

    private static final Set<String> ADMIN_ROLES =
            Set.of("ROLE_admin", "ROLE_super_admin", "ROLE_org_admin");

    private final DashboardService dashboardService;
    private final OperatorContext operatorContext;
    private final DashboardPresetRepository presetRepository;

    // ===== 预设 =====

    @Operation(summary = "查询预设列表（管理员可见全部，普通用户只见非 adminOnly）")
    @GetMapping("/presets")
    public Result<List<DashboardPresetVO>> listPresets() {
        var presets = presetRepository.findByStatusAndDeletedFalseOrderBySortOrderAsc((short) 0);
        if (!isAdmin()) {
            presets = presets.stream().filter(p -> !Boolean.TRUE.equals(p.getAdminOnly())).toList();
        }
        return Result.success(presets.stream().map(dashboardService::toPresetVO).toList());
    }

    @Operation(summary = "创建预设（管理员）")
    @PostMapping("/presets")
    public Result<DashboardPresetVO> createPreset(
            @Validated @RequestBody DashboardPresetCreateDTO dto) {
        var preset = new DashboardPreset();
        preset.setPresetKey("preset-" + System.currentTimeMillis());
        preset.setName(dto.name());
        preset.setDescription(dto.description());
        preset.setAdminOnly(Boolean.TRUE.equals(dto.adminOnly()));
        if (dto.refreshInterval() != null) preset.setRefreshInterval(dto.refreshInterval());
        preset.setWidgets(dashboardService.serializeWidgets(dto.widgets()));
        var saved = presetRepository.save(preset);
        return Result.success(dashboardService.toPresetVO(saved));
    }

    @Operation(summary = "更新预设（管理员）")
    @PutMapping("/presets/{id}")
    public Result<DashboardPresetVO> updatePreset(
            @PathVariable Long id, @Validated @RequestBody DashboardPresetUpdateDTO dto) {
        var preset =
                presetRepository
                        .findById(id)
                        .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                        .orElseThrow(() -> new IllegalArgumentException("预设不存在"));
        if (dto.name() != null) preset.setName(dto.name());
        if (dto.description() != null) preset.setDescription(dto.description());
        if (dto.adminOnly() != null) preset.setAdminOnly(dto.adminOnly());
        if (dto.refreshInterval() != null) preset.setRefreshInterval(dto.refreshInterval());
        if (dto.widgets() != null) {
            preset.setWidgets(dashboardService.serializeWidgets(dto.widgets()));
        }
        preset.setUpdateTime(java.time.LocalDateTime.now());
        var saved = presetRepository.save(preset);
        return Result.success(dashboardService.toPresetVO(saved));
    }

    @Operation(summary = "删除预设（管理员，软删除）")
    @DeleteMapping("/presets/{id}")
    public Result<Void> deletePreset(@PathVariable Long id) {
        presetRepository.deleteById(id);
        return Result.success();
    }

    // ===== 仪表盘 CRUD =====

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

    @Operation(summary = "保存仪表盘布局")
    @PutMapping("/{id}/layout")
    public Result<DashboardVO> saveLayout(
            @PathVariable Long id, @Validated @RequestBody DashboardLayoutDTO dto) {
        return Result.success(dashboardService.saveLayout(id, dto.layout()));
    }

    @Operation(summary = "删除仪表盘")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dashboardService.delete(id);
        return Result.success();
    }

    // ===== Widget =====

    @Operation(summary = "查询可用指标列表（counter Widget 配置用）")
    @GetMapping("/metrics")
    public Result<List<DashboardService.MetricMeta>> listMetrics() {
        return Result.success(dashboardService.listMetrics());
    }

    @Operation(summary = "查询组件数据")
    @RateLimit(limit = 100, windowSeconds = 60, message = "仪表盘数据请求过于频繁，请稍后再试")
    @PostMapping("/widgets/{widgetId}/data")
    public Result<WidgetDataVO> getWidgetData(
            @PathVariable String widgetId,
            @RequestBody(required = false) Map<String, Object> config) {
        Long userId = operatorContext.currentUserId().orElse(null);
        return Result.success(
                dashboardService.getWidgetData(widgetId, config, isAdmin() ? null : userId));
    }

    // ===== 内部工具 =====

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream()
                        .anyMatch(a -> ADMIN_ROLES.contains(a.getAuthority()));
    }
}
