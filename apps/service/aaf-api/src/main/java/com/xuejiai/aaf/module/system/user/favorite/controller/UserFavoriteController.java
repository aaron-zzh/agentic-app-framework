package com.xuejiai.aaf.module.system.user.favorite.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.user.favorite.domain.UserFavorite;
import com.xuejiai.aaf.module.system.user.favorite.service.UserFavoriteService;
import com.xuejiai.aaf.module.system.user.favorite.vo.UserFavoriteCreateDTO;
import com.xuejiai.aaf.module.system.user.favorite.vo.UserFavoritePageDTO;
import com.xuejiai.aaf.module.system.user.favorite.vo.UserFavoriteVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 收藏夹接口。 */
@Tag(name = "收藏夹")
@RestController
@RequestMapping("/api/user-favorites")
@RequiredArgsConstructor
public class UserFavoriteController
        extends BaseCrudController<
                UserFavorite, UserFavoriteVO, UserFavoriteCreateDTO, Void, UserFavoritePageDTO> {

    private final UserFavoriteService favoriteService;

    @Override
    protected UserFavoriteService getService() {
        return favoriteService;
    }

    /** 删除单条收藏（override 以加 ownership 校验）。 */
    @Operation(summary = "删除收藏")
    @PreAuthorize("isAuthenticated()")
    @Override
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        favoriteService.deleteOwn(id);
        return Result.success();
    }

    @Operation(summary = "按目标删除（toggle 用）")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/by-target")
    public Result<Void> deleteByTarget(
            @RequestParam String targetType, @RequestParam Long targetId) {
        favoriteService.deleteByTarget(targetType, targetId);
        return Result.success();
    }
}
