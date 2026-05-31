package com.xuejiai.aaf.module.developer.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseOwnerRequired;
import com.xuejiai.aaf.framework.security.license.PremiumRequired;
import com.xuejiai.aaf.module.developer.service.DeveloperAccountService;
import com.xuejiai.aaf.module.developer.service.DeveloperRedeemCodeService;
import com.xuejiai.aaf.module.developer.service.DeveloperSubscriptionService;
import com.xuejiai.aaf.module.developer.vo.DeveloperRedeemCodeCreateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperRedeemCodeCreateVO;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscribeDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 开发者运营管理接口。 */
@Tag(name = "开发者运营管理")
@RestController
@RequestMapping("/api/developer/admin")
@RequiredArgsConstructor
@PremiumRequired("开发者运营管理")
@FeatureRequired("developer")
@LicenseOwnerRequired("官方开发者运营管理")
public class DeveloperAdminController {

    private final DeveloperAccountService accountService;
    private final DeveloperRedeemCodeService redeemCodeService;
    private final DeveloperSubscriptionService subscriptionService;

    @Operation(summary = "生成开发者 Token 兑换码")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/redeem-codes")
    public Result<DeveloperRedeemCodeCreateVO> createRedeemCode(
            @Valid @RequestBody DeveloperRedeemCodeCreateDTO dto) {
        return Result.success(redeemCodeService.create(dto));
    }

    @Operation(summary = "为指定用户开通或调整开发者订阅")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/accounts/{userId}/subscribe")
    public Result<Long> subscribeUser(
            @PathVariable Long userId, @Valid @RequestBody DeveloperSubscribeDTO dto) {
        var developer = accountService.getOrCreateByUserId(userId);
        return Result.success(subscriptionService.subscribe(developer.getId(), dto.planCode()));
    }
}
