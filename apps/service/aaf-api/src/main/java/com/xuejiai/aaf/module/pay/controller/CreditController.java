package com.xuejiai.aaf.module.pay.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.pay.vo.CreditBalanceVO;
import com.xuejiai.aaf.module.pay.vo.CreditGroupVO;
import com.xuejiai.aaf.module.pay.vo.CreditTransactionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 积分查询接口 */
@Tag(name = "积分管理")
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;
    private final OperatorContext operatorContext;

    @Operation(summary = "查询积分余额")
    @GetMapping("/balance")
    public Result<CreditBalanceVO> getBalance(@RequestParam(required = false) Long userId) {
        var ownerId = ownerId(userId);
        var account = creditService.getAccount(ownerId);
        if (account == null) {
            return Result.success(new CreditBalanceVO(ownerId, 0, 0, 0, 0));
        }
        return Result.success(
                new CreditBalanceVO(
                        ownerId,
                        account.getBalance(),
                        account.getFrozen(),
                        account.getTotalEarned(),
                        account.getTotalSpent()));
    }

    @Operation(summary = "查询积分分组明细（按 batch_type 汇总）")
    @GetMapping("/groups")
    public Result<java.util.List<CreditGroupVO>> getGroups(@RequestParam(required = false) Long userId) {
        var grouped = creditService.getGroupedBalance(ownerId(userId));
        // batch_type → 显示名映射
        var labelMap = java.util.Map.of(
                "SUBSCRIPTION", "套餐积分",
                "TOPUP",        "购买积分",
                "WEEKLY",       "每周积分",
                "REWARD",       "奖励积分",
                "MANUAL",       "额外赠送");
        var groups = grouped.entrySet().stream()
                .map(e -> new CreditGroupVO(e.getKey(), labelMap.getOrDefault(e.getKey(), e.getKey()), e.getValue(), null))
                .toList();
        return Result.success(groups);
    }

    @Operation(summary = "查询积分流水")
    @GetMapping("/transactions")
    public Result<PageResult<CreditTransactionVO>> getTransactions(
            @RequestParam(required = false) Long userId, @PageableDefault Pageable pageable) {
        var page = creditService.getTransactions(ownerId(userId), pageable);
        var list =
                page.getContent().stream()
                        .map(
                                t ->
                                        new CreditTransactionVO(
                                                t.getId(),
                                                t.getType().name(),
                                                t.getAmount(),
                                                t.getBalanceAfter(),
                                                t.getSource(),
                                                t.getBizId(),
                                                t.getCreateTime()))
                        .toList();
        return Result.success(new PageResult<>(list, page.getTotalElements()));
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
