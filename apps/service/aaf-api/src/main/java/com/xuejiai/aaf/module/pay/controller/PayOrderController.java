package com.xuejiai.aaf.module.pay.controller;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.service.RechargeService;
import com.xuejiai.aaf.module.pay.vo.PayNotifyDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 支付订单接口 */
@Tag(name = "支付订单")
@RestController
@RequestMapping("/api/pay/orders")
@RequiredArgsConstructor
public class PayOrderController {

    private final PayOrderService payOrderService;
    private final RechargeService rechargeService;

    @Operation(summary = "发起充值")
    @PostMapping("/recharge")
    public Result<PayOrderVO> recharge(
            @RequestParam Long userId,
            @RequestParam long amount,
            @RequestParam(defaultValue = "MOCK") String channelCode) {
        return Result.success(rechargeService.initiateRecharge(userId, amount, channelCode));
    }

    @Operation(summary = "创建支付单")
    @PostMapping
    public Result<PayOrderVO> create(@Valid @RequestBody PayOrderCreateDTO dto) {
        return Result.success(payOrderService.create(dto));
    }

    @Operation(summary = "支付回调通知")
    @PostMapping("/notify")
    public Result<Void> notify(@Valid @RequestBody PayNotifyDTO dto) {
        var payOrderId = payOrderService.handleNotify(dto);
        if (payOrderId != null) {
            rechargeService.onPaySuccess(payOrderId);
        }
        return Result.success();
    }

    @Operation(summary = "查询支付单")
    @GetMapping("/{id}")
    public Result<PayOrderVO> getById(@PathVariable Long id) {
        return Result.success(payOrderService.getById(id));
    }
}
