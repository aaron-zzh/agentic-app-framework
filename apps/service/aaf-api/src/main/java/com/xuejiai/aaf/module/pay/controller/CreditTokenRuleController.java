package com.xuejiai.aaf.module.pay.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.pay.service.CreditTokenRuleService;
import com.xuejiai.aaf.module.pay.vo.CreditTokenRuleDTO;
import com.xuejiai.aaf.module.pay.vo.CreditTokenRuleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 积分转 Token 规则接口 */
@Tag(name = "积分转Token规则")
@RestController
@RequestMapping("/api/credit-token-rules")
@RequiredArgsConstructor
public class CreditTokenRuleController {

    private final CreditTokenRuleService ruleService;

    @Operation(summary = "创建规则")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<CreditTokenRuleVO> create(@Valid @RequestBody CreditTokenRuleDTO dto) {
        return Result.success(ruleService.create(dto));
    }

    @Operation(summary = "更新规则")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<CreditTokenRuleVO> update(
            @PathVariable Long id, @Valid @RequestBody CreditTokenRuleDTO dto) {
        return Result.success(ruleService.update(id, dto));
    }

    @Operation(summary = "删除规则")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "查询所有规则")
    @GetMapping
    public Result<List<CreditTokenRuleVO>> list() {
        return Result.success(ruleService.list());
    }

    @Operation(summary = "计算积分可兑换Token数")
    @GetMapping("/calculate")
    public Result<Long> calculate(@RequestParam long creditAmount) {
        return Result.success(ruleService.calculateTokens(creditAmount));
    }
}
