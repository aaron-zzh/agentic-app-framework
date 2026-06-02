package com.xuejiai.aaf.module.developer.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.PremiumRequired;
import com.xuejiai.aaf.module.developer.service.DeveloperAccountService;
import com.xuejiai.aaf.module.developer.service.DeveloperApiKeyService;
import com.xuejiai.aaf.module.developer.vo.DeveloperApiKeyCreateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperApiKeyCreateVO;
import com.xuejiai.aaf.module.developer.vo.DeveloperApiKeyVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 开发者 Gateway Key 管理接口。 */
@Tag(name = "开发者API Key")
@RestController
@RequestMapping("/api/developer/api-keys")
@RequiredArgsConstructor
@PremiumRequired("开发者 Gateway Key 管理")
@FeatureRequired("developer")
public class DeveloperApiKeyController {

    private final DeveloperAccountService accountService;
    private final DeveloperApiKeyService apiKeyService;

    @Operation(summary = "创建开发者 Gateway Key")
    @PostMapping
    public Result<DeveloperApiKeyCreateVO> create(
            @Valid @RequestBody DeveloperApiKeyCreateDTO dto) {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(apiKeyService.create(developer.getId(), dto));
    }

    @Operation(summary = "查询开发者 Gateway Key")
    @GetMapping
    public Result<List<DeveloperApiKeyVO>> list() {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(apiKeyService.list(developer.getId()));
    }
}
