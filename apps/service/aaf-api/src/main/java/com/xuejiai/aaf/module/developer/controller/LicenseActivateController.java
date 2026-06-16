package com.xuejiai.aaf.module.developer.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.developer.service.DeveloperRedeemCodeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * License 公开激活接口（无需登录）。
 *
 * <p>用户部署的实例传入 LICENSE 类型兑换码，服务端验证并返回预签发的 license.jwt。 兑换码一次性，用完即标记 REDEEMED。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "License 激活")
@RestController
@RequestMapping("/api/public/license")
@RequiredArgsConstructor
public class LicenseActivateController {

    private final DeveloperRedeemCodeService redeemCodeService;

    public record ActivateRequest(@NotBlank String redeemCode) {}

    public record ActivateResponse(String licenseJwt) {}

    @Operation(
            summary = "用 LICENSE 兑换码激活并获取 license.jwt",
            description = "无需登录。传入 LICENSE 类型兑换码，返回 license.jwt 内容，一次性有效。")
    @PostMapping("/activate")
    public Result<ActivateResponse> activate(@Valid @RequestBody ActivateRequest req) {
        String licenseJwt = redeemCodeService.activateLicense(req.redeemCode());
        return Result.success(new ActivateResponse(licenseJwt));
    }
}
