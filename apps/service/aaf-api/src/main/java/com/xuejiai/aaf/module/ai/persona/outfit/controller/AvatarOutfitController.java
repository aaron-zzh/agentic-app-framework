package com.xuejiai.aaf.module.ai.persona.outfit.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.persona.outfit.domain.AvatarOutfit;
import com.xuejiai.aaf.module.ai.persona.outfit.service.AvatarOutfitService;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.AvatarOutfitPageDTO;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.AvatarOutfitVO;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.UserAvatarInventoryVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 装扮商城接口。 */
@Tag(name = "装扮商城")
@RestController
@RequestMapping("/api/avatar-outfits")
@RequiredArgsConstructor
public class AvatarOutfitController
        extends BaseCrudController<AvatarOutfit, AvatarOutfitVO, Void, Void, AvatarOutfitPageDTO> {

    private final AvatarOutfitService outfitService;

    @Override
    protected AvatarOutfitService getService() {
        return outfitService;
    }

    @Operation(summary = "购买装扮")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/purchase")
    public Result<UserAvatarInventoryVO> purchase(@PathVariable Long id) {
        return Result.success(outfitService.purchase(id));
    }
}
