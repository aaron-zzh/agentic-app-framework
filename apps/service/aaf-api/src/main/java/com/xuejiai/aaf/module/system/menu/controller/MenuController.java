package com.xuejiai.aaf.module.system.menu.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.menu.domain.SysMenu;
import com.xuejiai.aaf.module.system.menu.service.MenuService;
import com.xuejiai.aaf.module.system.menu.vo.MenuCreateDTO;
import com.xuejiai.aaf.module.system.menu.vo.MenuPageParam;
import com.xuejiai.aaf.module.system.menu.vo.MenuVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 菜单管理接口 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController
        extends BaseCrudController<SysMenu, MenuVO, MenuCreateDTO, MenuCreateDTO, MenuPageParam> {

    private final MenuService menuService;
    private final OperatorContext operatorContext;

    @Override
    protected MenuService getService() {
        return menuService;
    }

    @Operation(summary = "获取当前用户菜单树")
    @GetMapping("/my-tree")
    @PreAuthorize("isAuthenticated()")
    public Result<List<MenuVO>> getUserMenuTree() {
        var userId = operatorContext.currentOwnerId().orElse(null);
        return Result.success(menuService.getUserMenuTree(userId));
    }

    @Operation(summary = "获取全部菜单树（管理用）")
    @GetMapping("/tree")
    @PreAuthorize("hasPermission(null, 'system:menu:manage')")
    public Result<List<MenuVO>> getFullTree() {
        return Result.success(menuService.getFullTree());
    }

    @Operation(summary = "更新菜单排序")
    @PutMapping("/{id}/sort")
    @PreAuthorize("hasPermission(null, 'system:menu:manage')")
    public Result<Void> updateSort(@PathVariable Long id, @RequestParam Integer sortOrder) {
        menuService.updateSort(id, sortOrder);
        return Result.success();
    }
}
