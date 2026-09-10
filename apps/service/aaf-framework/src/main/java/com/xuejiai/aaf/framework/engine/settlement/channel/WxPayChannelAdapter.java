package com.xuejiai.aaf.framework.engine.settlement.channel;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyV3Result;
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayRefundV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.settlement.*;

import lombok.extern.slf4j.Slf4j;

/** 微信支付渠道适配器——支持 wx_pub/wx_lite/wx_app/wx_native 四种下单方式。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "aaf.pay.wx", name = "enabled", havingValue = "true")
public class WxPayChannelAdapter implements PayChannelAdapter {

    private static final List<String> SUPPORTED_CODES =
            List.of("wx_pub", "wx_lite", "wx_app", "wx_native");

    /** channelCode → 微信交易类型映射 */
    private static final Map<String, TradeTypeEnum> TRADE_TYPE_MAP =
            Map.of(
                    "wx_pub", TradeTypeEnum.JSAPI,
                    "wx_lite", TradeTypeEnum.JSAPI,
                    "wx_app", TradeTypeEnum.APP,
                    "wx_native", TradeTypeEnum.NATIVE);

    private final WxPayService wxPayService;
    private final String notifyUrl;

    public WxPayChannelAdapter(WxPayProperties properties) {
        var config = new WxPayConfig();
        config.setAppId(properties.getAppId());
        config.setMchId(properties.getMchId());
        config.setApiV3Key(properties.getApiV3Key());
        config.setPrivateKeyPath(properties.getPrivateKeyPath());
        config.setPrivateCertPath(properties.getPrivateCertPath());
        this.wxPayService = new WxPayServiceImpl();
        this.wxPayService.setConfig(config);
        this.notifyUrl = properties.getNotifyUrl();
        log.info("微信支付适配器初始化完成, mchId={}", properties.getMchId());
    }

    @Override
    public List<String> supportedChannelCodes() {
        return SUPPORTED_CODES;
    }

    @Override
    public String channelCode() {
        return "wx_native";
    }

    @Override
    public PayResult charge(ChargeRequest request) {
        var tradeType = TRADE_TYPE_MAP.get(request.channelCode());
        if (tradeType == null) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "不支持的微信渠道: " + request.channelCode());
        }
        try {
            var wxRequest = new WxPayUnifiedOrderV3Request();
            wxRequest.setOutTradeNo(request.outTradeNo());
            wxRequest.setDescription(request.subject());
            wxRequest.setNotifyUrl(notifyUrl);
            var amountInfo = new WxPayUnifiedOrderV3Request.Amount();
            amountInfo.setTotal((int) request.amount());
            amountInfo.setCurrency("CNY");
            wxRequest.setAmount(amountInfo);

            var response = wxPayService.unifiedOrderV3(tradeType, wxRequest);
            // wx_native 返回 code_url（扫码 URL），其他返回 prepay_id
            String codeUrl = null;
            String channelOrderNo = null;
            if (TradeTypeEnum.NATIVE.equals(tradeType)) {
                codeUrl = response.getCodeUrl();
            } else {
                channelOrderNo = response.getPrepayId();
            }
            return new PayResult(
                    true,
                    // wx_native 等渠道下单成功仅代表二维码/跳转链接生成成功，用户尚未支付
                    PayStatus.UNPAID,
                    request.outTradeNo(),
                    channelOrderNo,
                    response.toString(),
                    codeUrl);
        } catch (WxPayException e) {
            log.error("微信支付下单失败: outTradeNo={}, error={}", request.outTradeNo(), e.getMessage());
            return new PayResult(
                    false, PayStatus.UNPAID, request.outTradeNo(), null, e.getMessage());
        }
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        // 微信提现（企业付款）暂不实现，返回失败
        return new PayResult(false, PayStatus.UNPAID, request.outTradeNo(), null, "微信提现暂未实现");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        try {
            var wxRequest = new WxPayRefundV3Request();
            wxRequest.setOutTradeNo(request.outTradeNo());
            wxRequest.setOutRefundNo(request.refundNo());
            var amount = new WxPayRefundV3Request.Amount();
            // M25：微信 V3 语义——refund=本次退款额，total=原支付单总额（不是退款额）。
            // 原实现两者都传退款额，部分退款会被渠道拒绝或按错误比例计算。
            amount.setRefund((int) request.amount());
            amount.setTotal((int) request.originalAmount());
            amount.setCurrency("CNY");
            wxRequest.setAmount(amount);
            wxRequest.setReason(request.reason());

            WxPayRefundV3Result result = wxPayService.refundV3(wxRequest);
            boolean success = "SUCCESS".equals(result.getStatus());
            return new RefundResult(success, request.refundNo(), result.getStatus());
        } catch (WxPayException e) {
            log.error("微信退款失败: refundNo={}, error={}", request.refundNo(), e.getMessage());
            return new RefundResult(false, request.refundNo(), e.getMessage());
        }
    }

    @Override
    public QueryResult queryStatus(String outTradeNo) {
        try {
            WxPayOrderQueryV3Result result = wxPayService.queryOrderV3(null, outTradeNo);
            return switch (result.getTradeState()) {
                case "SUCCESS" -> new QueryResult(PayStatus.PAID, result.getTransactionId());
                case "CLOSED", "PAYERROR" -> new QueryResult(PayStatus.CLOSED);
                case "REFUND" -> new QueryResult(PayStatus.REFUNDED);
                default -> new QueryResult(PayStatus.UNPAID);
            };
        } catch (WxPayException e) {
            // 订单不存在是明确的业务判定（非网关调用异常），区分对待以便提前关闭死单
            if ("ORDER_NOT_EXIST".equals(e.getErrCode())) {
                return new QueryResult(PayStatus.NOT_FOUND);
            }
            log.warn("微信查询订单状态失败: outTradeNo={}", outTradeNo);
            return null;
        }
    }

    /**
     * M28：统一回调验签入口——微信 V3 需要原始报文 + Wechatpay-* 请求头。
     *
     * <p>验签失败或解析异常统一返回 {@link NotifyResult#rejected}（fail-closed），不向调用方泄露 SDK 细节。
     */
    @Override
    public NotifyResult verifyAndParseNotify(NotifyEnvelope envelope) {
        var headers = envelope.headers();
        var header = new SignatureHeader();
        header.setTimeStamp(headers.get("Wechatpay-Timestamp"));
        header.setNonce(headers.get("Wechatpay-Nonce"));
        header.setSignature(headers.get("Wechatpay-Signature"));
        header.setSerial(headers.get("Wechatpay-Serial"));
        try {
            var decrypted = parseOrderNotify(envelope.rawBody(), header).getResult();
            String tradeState = decrypted.getTradeState();
            if ("SUCCESS".equals(tradeState)) {
                return NotifyResult.paid(
                        decrypted.getOutTradeNo(), decrypted.getTransactionId(), tradeState);
            }
            var status =
                    switch (tradeState) {
                        case "CLOSED", "PAYERROR", "REVOKED" -> PayStatus.CLOSED;
                        case "REFUND" -> PayStatus.REFUNDED;
                        default -> PayStatus.UNPAID;
                    };
            return NotifyResult.verifiedButNotPaid(status, decrypted.getOutTradeNo(), tradeState);
        } catch (WxPayException e) {
            log.warn("微信回调验签或解析失败: {}", e.getMessage());
            return NotifyResult.rejected("验签失败");
        }
    }

    /** 验证微信回调签名并解析通知 */
    public WxPayNotifyV3Result parseOrderNotify(String body, SignatureHeader header)
            throws WxPayException {
        return wxPayService.parseOrderNotifyV3Result(body, header);
    }

    /** 验证微信退款回调签名并解析通知 */
    public WxPayRefundNotifyV3Result parseRefundNotify(String body, SignatureHeader header)
            throws WxPayException {
        return wxPayService.parseRefundNotifyV3Result(body, header);
    }

    /**
     * 下载微信账单——CSV 解析尚未实现。
     *
     * <p>M27：原实现调用下载接口后 log 成功但 {@code return List.of()}，对账拿到零条渠道记录会
     * 静默失真（把全部本地记录判为差异，或在无本地记录时误报"无差异"）。未实现即显式失败。
     */
    @Override
    public List<PayChannelAdapter.BillItem> downloadBill(java.time.LocalDate date) {
        throw new UnsupportedOperationException("微信账单 CSV 解析尚未实现，不能以空账单参与对账");
    }
}
