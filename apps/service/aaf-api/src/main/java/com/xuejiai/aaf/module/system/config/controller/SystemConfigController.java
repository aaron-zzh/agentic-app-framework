package com.xuejiai.aaf.module.system.config.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.system.config.vo.SystemConfigCreateDTO;
import com.xuejiai.aaf.module.system.config.vo.SystemConfigUpdateDTO;
import com.xuejiai.aaf.module.system.config.vo.SystemConfigVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 系统配置管理接口。
 *
 * @author AaronZZH & Kiro
 */
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    /** 按分类查询配置列表（敏感配置 value 返回 null） */
    @GetMapping
    @PreAuthorize("hasRole('super_admin')")
    public Result<List<SystemConfigVO>> list(@RequestParam(required = false) String category) {
        var list =
                category != null
                        ? configService.listByCategory(category)
                        : configService.listByCategory("*");
        return Result.success(list.stream().map(configService::toVO).toList());
    }

    /** 更新配置值 */
    @PutMapping
    @PreAuthorize("hasRole('super_admin')")
    public Result<Void> update(@Valid @RequestBody SystemConfigUpdateDTO dto) {
        configService.set(dto.key(), dto.value());
        return Result.success(null);
    }

    /** 创建配置项 */
    @PostMapping
    @PreAuthorize("hasRole('super_admin')")
    public Result<SystemConfigVO> create(@Valid @RequestBody SystemConfigCreateDTO dto) {
        return Result.success(configService.create(dto));
    }

    /** 删除配置项 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('super_admin')")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.success(null);
    }

    /** 刷新指定配置缓存 */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('super_admin')")
    public Result<Void> refresh(@RequestParam String key) {
        configService.evict(key);
        return Result.success(null);
    }

    /** 刷新所有配置缓存 */
    @PostMapping("/refresh-all")
    @PreAuthorize("hasRole('super_admin')")
    public Result<Void> refreshAll() {
        configService.evictAll();
        return Result.success(null);
    }
}
