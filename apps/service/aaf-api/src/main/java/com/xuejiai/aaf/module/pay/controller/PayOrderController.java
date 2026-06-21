package com.xuejiai.aaf.module.pay.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.settlement.channel.AlipayChannelAdapter;
import com.xuejiai.aaf.framework.engine.settlement.channel.WxPayChannelAdapter;
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
    private final Map<String, PaySuccessHandler> handlers;
    private final WxPayChannelAdapter wxPayAdapter;
    private final AlipayChannelAdapter alipayAdapter;

    public PayOrderController(
            PayOrderService payOrderService,
            RechargeService rechargeService,
            BizOrderService bizOrderService,
            PayNotifyService payNotifyService,
            OperatorContext operatorContext,
            List<PaySuccessHandler> handlerList,
            java.util.Optional<WxPayChannelAdapter> wxPayAdapter,
            java.util.Optional<AlipayChannelAdapter> alipayAdapter) {
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
        this.wxPayAdapter = wxPayAdapter.orElse(null);
        this.alipayAdapter = alipayAdapter.orElse(null);
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

    /**
     * 微信支付异步回调（带验签）。
     *
     * <p>微信会推送到此端点，须校验签名后才能标记支付成功。 配置：在微信商户平台填写 notifyUrl =
     * https://your-domain/api/pay/orders/notify/wx
     */
    @Operation(summary = "微信支付回调")
    @PostMapping("/notify/wx")
    public org.springframework.http.ResponseEntity<Map<String, String>> notifyWx(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        if (wxPayAdapter == null) {
            return org.springframework.http.ResponseEntity.status(500)
                    .body(Map.of("code", "FAIL", "message", "微信支付未配置"));
        }
        try {
            var header = new com.github.binarywang.wxpay.bean.notify.SignatureHeader();
            header.setTimeStamp(timestamp);
            header.setNonce(nonce);
            header.setSignature(signature);
            header.setSerial(serial);
            var notifyResult = wxPayAdapter.parseOrderNotify(body, header);
            var decryptResult = notifyResult.getResult();
            if ("SUCCESS".equals(decryptResult.getTradeState())) {
                var payOrderId =
                        payOrderService.handleWxNotify(
                                decryptResult.getOutTradeNo(), decryptResult.getTransactionId());
                if (payOrderId != null) {
                    payNotifyService.onPaySuccess(payOrderId);
                }
            }
            return org.springframework.http.ResponseEntity.ok(
                    Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.error("微信回调处理失败", e);
            return org.springframework.http.ResponseEntity.status(500)
                    .body(Map.of("code", "FAIL", "message", e.getMessage()));
        }
    }

    @Operation(summary = "支付宝支付回调")
    @PostMapping("/notify/alipay")
    public String notifyAlipay(@RequestParam Map<String, String> params) {
        if (alipayAdapter == null) return "fail";
        try {
            if (!alipayAdapter.verifyNotify(params)) {
                log.warn("支付宝回调验签失败");
                return "fail";
            }
            if ("TRADE_SUCCESS".equals(params.get("trade_status"))
                    || "TRADE_FINISHED".equals(params.get("trade_status"))) {
                var payOrderId =
                        payOrderService.handleAlipayNotify(
                                params.get("out_trade_no"), params.get("trade_no"));
                if (payOrderId != null) {
                    payNotifyService.onPaySuccess(payOrderId);
                }
            }
            return "success";
        } catch (Exception e) {
            log.error("支付宝回调处理失败", e);
            return "fail";
        }
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
