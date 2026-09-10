package com.xuejiai.aaf.module.pay.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.settlement.NotifyEnvelope;
import com.xuejiai.aaf.framework.engine.settlement.SettlementEngine;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayNotifyService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
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

    /**
     * M28：回调端点用于路由验签适配器的渠道编码。
     *
     * <p>回调报文本身不含 AAF 渠道编码，这里用各渠道适配器注册的任一编码定位适配器——同一适配器 覆盖该渠道全部下单方式（wx_pub/wx_lite/… 或
     * alipay_pc/alipay_wap/…），验签逻辑一致。
     */
    private static final String WX_NOTIFY_CHANNEL_CODE = "wx_native";

    private static final String ALIPAY_NOTIFY_CHANNEL_CODE = "alipay_pc";

    private final PayOrderService payOrderService;
    private final BizOrderService bizOrderService;
    private final PayNotifyService payNotifyService;
    private final SettlementEngine settlementEngine;
    private final Map<String, PaySuccessHandler> handlers;

    public PayOrderController(
            PayOrderService payOrderService,
            BizOrderService bizOrderService,
            PayNotifyService payNotifyService,
            SettlementEngine settlementEngine,
            List<PaySuccessHandler> handlerList) {
        this.payOrderService = payOrderService;
        this.bizOrderService = bizOrderService;
        this.payNotifyService = payNotifyService;
        this.settlementEngine = settlementEngine;
        this.handlers =
                handlerList.stream()
                        .collect(
                                Collectors.toMap(
                                        PaySuccessHandler::bizOrderType, Function.identity()));
        log.info("PaySuccessHandler 注册完成: {}", this.handlers.keySet());
    }

    // B2：原 POST /recharge 由客户端提交 amount 直接下单，Mock 渠道下可同步入账形成"客户端定价铸币"。
    // 该入口已删除：充值统一走 POST /api/billing/credit-packages/purchase，
    // 金额取 credit_package.price（服务端货架定价），客户端只提交 packageId。

    @Operation(summary = "创建支付单")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<PayOrderVO> create(@Valid @RequestBody PayOrderCreateDTO dto) {
        return Result.success(payOrderService.create(dto));
    }

    @Operation(summary = "查询支付单")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<PayOrderVO> getById(@PathVariable Long id) {
        return Result.success(payOrderService.getById(id));
    }

    /**
     * 按商户订单号查询支付单——供支付结果落地页调用。
     *
     * <p>支付宝 returnUrl 跳转回来时 query 参数携带的是 out_trade_no（即 merchantOrderNo）， 而非数据库自增
     * ID，此端点供未登录场景下的落地页查询最终支付状态。
     */
    @Operation(summary = "按商户订单号查询支付单")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/by-merchant-order-no/{merchantOrderNo}")
    public Result<PayOrderVO> getByMerchantOrderNo(@PathVariable String merchantOrderNo) {
        return Result.success(payOrderService.getByMerchantOrderNo(merchantOrderNo));
    }

    /**
     * 支付宝页面跳转页（电脑网站支付/手机网站支付）——前端整页跳转到此地址，浏览器自动提交表单跳转到支付宝收银台。
     *
     * <p>仍需登录：浏览器无需手工设置 Authorization Header，会自动携带登录时写入的 {@code aaf-token}
     * Cookie，由安全过滤器恢复 Bearer 身份；服务层继续校验订单归属、渠道和待支付状态，仅本人未支付订单可重新生成跳转表单。
     */
    @Operation(summary = "支付宝页面跳转页")
    @PreAuthorize("isAuthenticated()")
    @GetMapping(
            value = "/{id}/redirect",
            produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
    public String redirect(@PathVariable Long id) {
        return payOrderService.buildAlipayRedirectHtml(id);
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
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Serial") String serial) {
        try {
            // M28：统一走结算引擎的回调信封契约，Controller 不再直连微信 SDK 与具体适配器
            var envelope =
                    NotifyEnvelope.ofBody(
                            WX_NOTIFY_CHANNEL_CODE,
                            body,
                            Map.of(
                                    "Wechatpay-Timestamp", timestamp,
                                    "Wechatpay-Nonce", nonce,
                                    "Wechatpay-Signature", signature,
                                    "Wechatpay-Serial", serial));
            var result = settlementEngine.verifyAndParseNotify(envelope);
            if (!result.verified()) {
                return org.springframework.http.ResponseEntity.status(401)
                        .body(Map.of("code", "FAIL", "message", "验签失败"));
            }
            if (result.isPaySuccess()) {
                var payOrderId =
                        payOrderService.handleWxNotify(
                                result.outTradeNo(), result.channelOrderNo());
                if (payOrderId != null) {
                    payNotifyService.onPaySuccess(payOrderId);
                }
            }
            return org.springframework.http.ResponseEntity.ok(
                    Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.error("微信回调处理失败", e);
            return org.springframework.http.ResponseEntity.status(500)
                    .body(Map.of("code", "FAIL", "message", "回调处理失败"));
        }
    }

    @Operation(summary = "支付宝支付回调")
    @PostMapping("/notify/alipay")
    public String notifyAlipay(@RequestParam Map<String, String> params) {
        try {
            // M28：验签与状态归一由适配器完成，这里只按统一结果决定是否入账
            var result =
                    settlementEngine.verifyAndParseNotify(
                            NotifyEnvelope.ofForm(ALIPAY_NOTIFY_CHANNEL_CODE, params));
            if (!result.verified()) return "fail";
            if (result.isPaySuccess()) {
                var payOrderId =
                        payOrderService.handleAlipayNotify(
                                result.outTradeNo(), result.channelOrderNo());
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
}
