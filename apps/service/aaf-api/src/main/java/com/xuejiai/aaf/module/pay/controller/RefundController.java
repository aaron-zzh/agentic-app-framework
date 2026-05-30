package com.xuejiai.aaf.module.pay.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.pay.service.PayRefundService;
import com.xuejiai.aaf.module.pay.vo.RefundApplyDTO;
import com.xuejiai.aaf.module.pay.vo.RefundOrderVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 退款接口 */
@Tag(name = "退款管理")
@RestController
@RequestMapping("/api/pay/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final PayRefundService payRefundService;

    @Operation(summary = "申请退款")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<RefundOrderVO> apply(@Valid @RequestBody RefundApplyDTO dto) {
        return Result.success(payRefundService.applyRefund(dto));
    }

    @Operation(summary = "查询退款单")
    @GetMapping("/{refundNo}")
    public Result<RefundOrderVO> getByRefundNo(@PathVariable String refundNo) {
        return Result.success(payRefundService.getByRefundNo(refundNo));
    }

    @Operation(summary = "退款回调通知")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/notify")
    public Result<Void> notify(@RequestParam String refundNo, @RequestParam boolean success) {
        payRefundService.handleRefundNotify(refundNo, success);
        return Result.success();
    }
}
