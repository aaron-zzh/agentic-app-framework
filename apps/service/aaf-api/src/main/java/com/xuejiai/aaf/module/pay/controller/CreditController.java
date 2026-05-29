package com.xuejiai.aaf.module.pay.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionRepository;
import com.xuejiai.aaf.module.pay.vo.CreditBalanceVO;
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

    private final CreditAccountRepository accountRepository;
    private final CreditTransactionRepository transactionRepository;

    @Operation(summary = "查询积分余额")
    @GetMapping("/balance")
    public Result<CreditBalanceVO> getBalance(@RequestParam Long userId) {
        var account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            return Result.success(new CreditBalanceVO(userId, 0, 0, 0, 0));
        }
        return Result.success(
                new CreditBalanceVO(
                        userId,
                        account.getBalance(),
                        account.getFrozen(),
                        account.getTotalEarned(),
                        account.getTotalSpent()));
    }

    @Operation(summary = "查询积分流水")
    @GetMapping("/transactions")
    public Result<PageResult<CreditTransactionVO>> getTransactions(
            @RequestParam Long userId, @PageableDefault Pageable pageable) {
        var account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            return Result.success(PageResult.empty());
        }
        var page =
                transactionRepository
                        .findByAccountId(account.getId(), pageable)
                        .map(
                                t ->
                                        new CreditTransactionVO(
                                                t.getId(),
                                                t.getType().name(),
                                                t.getAmount(),
                                                t.getBalanceAfter(),
                                                t.getSource(),
                                                t.getBizId(),
                                                t.getCreateTime()));
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }
}
