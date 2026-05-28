package com.xuejiai.aaf.module.system.role.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.entity.vo.EntityAccessVO;
import com.xuejiai.aaf.module.system.role.service.PermissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 权限查询接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "权限管理")
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final ActorContext actorContext;

    @Operation(summary = "查询当前用户对指定实体的权限")
    @GetMapping("/entity/{slug}")
    public Result<EntityAccessVO> getEntityAccess(@PathVariable String slug) {
        Long userId =
                actorContext
                        .currentUserId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        return Result.success(permissionService.getEntityAccess(userId, slug));
    }
}
