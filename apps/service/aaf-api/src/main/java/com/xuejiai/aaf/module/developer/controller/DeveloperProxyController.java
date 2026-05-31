package com.xuejiai.aaf.module.developer.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.PremiumRequired;
import com.xuejiai.aaf.module.developer.domain.DeveloperProxy;
import com.xuejiai.aaf.module.developer.service.DeveloperAccountService;
import com.xuejiai.aaf.module.developer.service.DeveloperProxyService;
import com.xuejiai.aaf.module.developer.vo.DeveloperProxyCreateDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 开发者子代理管理接口。 */
@Tag(name = "开发者子代理")
@RestController
@RequestMapping("/api/developer/proxies")
@RequiredArgsConstructor
@PremiumRequired("开发者子代理管理")
@FeatureRequired("developer")
public class DeveloperProxyController {

    private final DeveloperAccountService accountService;
    private final DeveloperProxyService proxyService;

    @Operation(summary = "创建子代理关系")
    @PostMapping
    public Result<DeveloperProxy> create(@Valid @RequestBody DeveloperProxyCreateDTO dto) {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(proxyService.create(developer.getId(), dto));
    }

    @Operation(summary = "查询子代理关系")
    @GetMapping
    public Result<List<DeveloperProxy>> list() {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(proxyService.list(developer.getId()));
    }
}
