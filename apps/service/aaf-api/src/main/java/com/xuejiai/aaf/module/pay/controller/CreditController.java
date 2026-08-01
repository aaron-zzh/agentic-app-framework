package com.xuejiai.aaf.module.pay.controller;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
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
@PreAuthorize("isAuthenticated()")
public class CreditController {

    private final CreditService creditService;
    private final OperatorContext operatorContext;

    @Operation(summary = "查询积分余额")
    @GetMapping("/balance")
    public Result<CreditBalanceVO> getBalance() {
        var ownerId = currentOwnerId();
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
    public Result<java.util.List<CreditGroupVO>> getGroups() {
        var grouped = creditService.getGroupedBalance(currentOwnerId());
        // batch_type → 显示名映射
        var labelMap =
                java.util.Map.of(
                        "SUBSCRIPTION", "套餐积分",
                        "TOPUP", "购买积分",
                        "WEEKLY", "每周积分",
                        "REWARD", "奖励积分",
                        "MANUAL", "额外赠送");
        // 非标准 batchType 合并到 REWARD
        var normalizedMap = new java.util.LinkedHashMap<String, Long>();
        grouped.forEach(
                (type, amount) -> {
                    String key = labelMap.containsKey(type) ? type : "REWARD";
                    normalizedMap.merge(key, amount, Long::sum);
                });
        var groups =
                normalizedMap.entrySet().stream()
                        .map(
                                e ->
                                        new CreditGroupVO(
                                                e.getKey(),
                                                labelMap.getOrDefault(e.getKey(), e.getKey()),
                                                e.getValue(),
                                                null))
                        .toList();
        return Result.success(groups);
    }

    @Operation(summary = "查询积分流水")
    @GetMapping("/transactions")
    public Result<PageResult<CreditTransactionVO>> getTransactions(
            @PageableDefault(sort = "createTime", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        var page = creditService.getTransactions(currentOwnerId(), pageable);
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
                                                t.getCategory(),
                                                t.getRemark(),
                                                t.getBizId(),
                                                t.getCreateTime()))
                        .toList();
        return Result.success(new PageResult<>(list, page.getTotalElements()));
    }

    /**
     * M1：当前身份来源唯一——只从 OperatorContext 取，取不到即 401。
     *
     * <p>原实现是 currentOwnerId().orElse(客户端传入的 userId)：一旦认证上下文解析不出归属者 （如 API Key 认证未绑定用户），就会采信请求参数里的
     * userId，形成任意用户数据读取。 管理员代查须走带显式鉴权的管理端接口，不复用本接口。
     */
    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
    }
}
