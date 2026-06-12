package com.xuejiai.aaf.module.pay.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayNotifyService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.service.RechargeService;
import com.xuejiai.aaf.module.pay.vo.PayNotifyDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/** 支付订单接口 */
@Slf4j
@Tag(name = "支付订单")
@RestController
@RequestMapping("/api/pay/orders")
public class PayOrderController {

    private final PayOrderService payOrderService;
    private final RechargeService rechargeService;
    private final BizOrderService bizOrderService;
    private final PayNotifyService payNotifyService;
    private final OperatorContext operatorContext;

    /** bizOrderType → handler，启动时自动注册所有 PaySuccessHandler Bean */
    private final Map<String, PaySuccessHandler> handlers;

    public PayOrderController(
            PayOrderService payOrderService,
            RechargeService rechargeService,
            BizOrderService bizOrderService,
            PayNotifyService payNotifyService,
            OperatorContext operatorContext,
            List<PaySuccessHandler> handlerList) {
        this.payOrderService = payOrderService;
        this.rechargeService = rechargeService;
        this.bizOrderService = bizOrderService;
        this.payNotifyService = payNotifyService;
        this.operatorContext = operatorContext;
        this.handlers =
                handlerList.stream()
                        .collect(
                                Collectors.toMap(
                                        PaySuccessHandler::bizOrderType, Function.identity()));
        log.info("PaySuccessHandler 注册完成: {}", this.handlers.keySet());
    }

    @Operation(summary = "发起充值")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/recharge")
    public Result<PayOrderVO> recharge(
            @RequestParam(required = false) Long userId,
            @RequestParam long amount,
            @RequestParam(defaultValue = "MOCK") String channelCode) {
        return Result.success(
                rechargeService.initiateRecharge(ownerId(userId), amount, channelCode));
    }

    @Operation(summary = "创建支付单")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<PayOrderVO> create(@Valid @RequestBody PayOrderCreateDTO dto) {
        return Result.success(payOrderService.create(dto));
    }

    @Operation(summary = "支付回调通知")
    @PostMapping("/notify")
    public Result<Void> notify(@Valid @RequestBody PayNotifyDTO dto) {
        var payOrderId = payOrderService.handleNotify(dto);
        if (payOrderId != null) {
            payNotifyService.onPaySuccess(payOrderId);
        }
        return Result.success();
    }

    @Operation(summary = "查询支付单")
    @GetMapping("/{id}")
    public Result<PayOrderVO> getById(@PathVariable Long id) {
        return Result.success(payOrderService.getById(id));
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
