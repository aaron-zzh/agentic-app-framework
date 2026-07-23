package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;
import com.xuejiai.aaf.module.brokerage.service.BrokerageWithdrawCrudService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/** 佣金提现管理接口。 */
@Tag(name = "佣金提现管理")
@RestController
@RequestMapping("/api/brokerage/withdraws")
@RequiredArgsConstructor
public class BrokerageWithdrawController
        extends BaseCrudController<
                BrokerageWithdraw, BrokerageWithdrawVO, Void, Void, BrokerageWithdrawPageParam> {

    private final BrokerageWithdrawCrudService brokerageWithdrawCrudService;

    @Override
    protected BrokerageWithdrawCrudService getService() {
        return brokerageWithdrawCrudService;
    }

    @Operation(summary = "审核通过提现申请")
    @PreAuthorize("hasPermission(null, 'brokerage:brokerage-withdraw:approve')")
    @PostMapping("/{id}/approve")
    public Result<BrokerageWithdrawVO> approve(@PathVariable Long id) {
        return Result.success(brokerageWithdrawCrudService.approve(id));
    }

    @Operation(summary = "驳回提现申请")
    @PreAuthorize("hasPermission(null, 'brokerage:brokerage-withdraw:reject')")
    @PostMapping("/{id}/reject")
    public Result<BrokerageWithdrawVO> reject(
            @PathVariable Long id, @Valid @RequestBody RejectWithdrawRequest request) {
        return Result.success(brokerageWithdrawCrudService.reject(id, request.auditReason()));
    }

    @Operation(summary = "确认提现已转账")
    @PreAuthorize("hasPermission(null, 'brokerage:brokerage-withdraw:transfer-confirm')")
    @PostMapping("/{id}/transfer-confirm")
    public Result<BrokerageWithdrawVO> confirmTransfer(
            @PathVariable Long id, @Valid @RequestBody TransferConfirmRequest request) {
        return Result.success(
                brokerageWithdrawCrudService.confirmTransfer(id, request.payTransferId()));
    }

    public record RejectWithdrawRequest(@NotBlank String auditReason) {}

    public record TransferConfirmRequest(@NotNull @Positive Long payTransferId) {}
}
