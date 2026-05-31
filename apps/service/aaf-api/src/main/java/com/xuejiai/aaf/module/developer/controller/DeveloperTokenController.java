package com.xuejiai.aaf.module.developer.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.PremiumRequired;
import com.xuejiai.aaf.module.developer.service.DeveloperAccountService;
import com.xuejiai.aaf.module.developer.service.DeveloperRedeemCodeService;
import com.xuejiai.aaf.module.developer.service.DeveloperTokenService;
import com.xuejiai.aaf.module.developer.vo.DeveloperRedeemDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperTokenAccountVO;
import com.xuejiai.aaf.module.developer.vo.DeveloperTokenTransactionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 开发者 Token 池接口。 */
@Tag(name = "开发者Token池")
@RestController
@RequestMapping("/api/developer/tokens")
@RequiredArgsConstructor
@PremiumRequired("开发者 Token 池")
@FeatureRequired("developer")
public class DeveloperTokenController {

    private final DeveloperAccountService accountService;
    private final DeveloperTokenService tokenService;
    private final DeveloperRedeemCodeService redeemCodeService;

    @Operation(summary = "查询开发者 Token 池")
    @GetMapping("/account")
    public Result<DeveloperTokenAccountVO> account() {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(tokenService.getAccountVO(developer.getId()));
    }

    @Operation(summary = "查询开发者 Token 流水")
    @GetMapping("/transactions")
    public Result<PageResult<DeveloperTokenTransactionVO>> transactions(
            @PageableDefault Pageable pageable) {
        var developer = accountService.getOrCreateCurrent();
        var page = tokenService.listTransactions(developer.getId(), pageable);
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }

    @Operation(summary = "兑换开发者 Token")
    @PostMapping("/redeem")
    public Result<Long> redeem(@Valid @RequestBody DeveloperRedeemDTO dto) {
        var developer = accountService.getOrCreateCurrent();
        return Result.success(redeemCodeService.redeem(developer.getId(), dto.code()));
    }
}
