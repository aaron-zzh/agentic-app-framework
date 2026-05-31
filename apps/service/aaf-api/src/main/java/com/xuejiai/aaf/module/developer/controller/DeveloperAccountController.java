package com.xuejiai.aaf.module.developer.controller;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.PremiumRequired;
import com.xuejiai.aaf.module.developer.service.DeveloperAccountService;
import com.xuejiai.aaf.module.developer.vo.DeveloperAccountVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 开发者账户接口。 */
@Tag(name = "开发者账户")
@RestController
@RequestMapping("/api/developer/account")
@RequiredArgsConstructor
@PremiumRequired("开发者商业化模块")
@FeatureRequired("developer")
public class DeveloperAccountController {

    private final DeveloperAccountService accountService;

    @Operation(summary = "获取或创建当前开发者账户")
    @GetMapping("/current")
    public Result<DeveloperAccountVO> current() {
        return Result.success(accountService.toVO(accountService.getOrCreateCurrent()));
    }
}
