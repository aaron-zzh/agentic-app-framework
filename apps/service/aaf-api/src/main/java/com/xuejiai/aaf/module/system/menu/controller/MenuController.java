package com.xuejiai.aaf.module.system.menu.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.menu.service.MenuService;
import com.xuejiai.aaf.module.system.menu.vo.MenuCreateDTO;
import com.xuejiai.aaf.module.system.menu.vo.MenuVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 菜单管理接口
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "获取当前用户菜单树")
    @GetMapping
    public Result<List<MenuVO>> getUserMenuTree() {
        // 当前简化：不依赖 userId，后续接入 RBAC
        return Result.success(menuService.getUserMenuTree(null));
    }

    @Operation(summary = "获取全部菜单树（管理用）")
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public Result<List<MenuVO>> getFullTree() {
        return Result.success(menuService.getFullTree());
    }

    @Operation(summary = "创建菜单")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<MenuVO> create(@Valid @RequestBody MenuCreateDTO dto) {
        return Result.success(menuService.create(dto));
    }

    @Operation(summary = "更新菜单")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<MenuVO> update(@PathVariable Long id, @Valid @RequestBody MenuCreateDTO dto) {
        return Result.success(menuService.update(id, dto));
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }

    @Operation(summary = "更新菜单排序")
    @PutMapping("/{id}/sort")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateSort(@PathVariable Long id, @RequestParam Integer sortOrder) {
        menuService.updateSort(id, sortOrder);
        return Result.success();
    }
}
