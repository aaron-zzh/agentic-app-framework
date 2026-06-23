package com.xuejiai.aaf.module.ai.persona.outfit.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.persona.outfit.service.AvatarOutfitService;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.EquipDTO;
import com.xuejiai.aaf.module.ai.persona.outfit.vo.UserAvatarInventoryVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 用户装扮库存接口。 */
@Tag(name = "用户装扮库存")
@RestController
@RequestMapping("/api/user-avatar-inventory")
@RequiredArgsConstructor
public class UserAvatarInventoryController {

    private final AvatarOutfitService outfitService;

    @Operation(summary = "我的库存")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<List<UserAvatarInventoryVO>> myInventory() {
        return Result.success(outfitService.myInventory());
    }

    @Operation(summary = "装备")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/equip")
    public Result<UserAvatarInventoryVO> equip(@RequestBody EquipDTO dto) {
        return Result.success(outfitService.equip(dto));
    }

    @Operation(summary = "卸下装备")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/unequip")
    public Result<Void> unequip(@RequestBody EquipDTO dto) {
        outfitService.unequip(dto);
        return Result.success();
    }
}
