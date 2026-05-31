package com.xuejiai.aaf.module.developer.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.PremiumRequired;
import com.xuejiai.aaf.module.developer.domain.DeveloperSubscriptionPlan;
import com.xuejiai.aaf.module.developer.service.DeveloperAccountService;
import com.xuejiai.aaf.module.developer.service.DeveloperSubscriptionService;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscribeDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 开发者订阅接口。 */
@Tag(name = "开发者订阅")
@RestController
@RequestMapping("/api/developer/subscription")
@RequiredArgsConstructor
public class DeveloperSubscriptionController {

    private final DeveloperAccountService accountService;
    private final DeveloperSubscriptionService subscriptionService;

    @Operation(summary = "查询开发者套餐")
    @GetMapping("/plans")
    public Result<List<DeveloperSubscriptionPlan>> plans() {
        return Result.success(subscriptionService.listPlans());
    }

    @Operation(summary = "开通开发者订阅")
    @PostMapping("/subscribe")
    public Result<Long> subscribe(@Valid @RequestBody DeveloperSubscribeDTO dto) {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(subscriptionService.subscribe(developer.getId(), dto.planCode()));
    }

    @Operation(summary = "查询当前开发者订阅")
    @GetMapping("/current")
    @PremiumRequired("开发者订阅管理")
    @FeatureRequired("developer")
    public Result<DeveloperSubscriptionVO> current() {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(subscriptionService.current(developer.getId()));
    }
}
