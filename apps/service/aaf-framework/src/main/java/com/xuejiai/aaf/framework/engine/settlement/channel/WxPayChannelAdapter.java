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

    public WxPayChannelAdapter(WxPayProperties properties) {
        var config = new WxPayConfig();
        config.setAppId(properties.getAppId());
        config.setMchId(properties.getMchId());
        config.setApiV3Key(properties.getApiV3Key());
        config.setPrivateKeyPath(properties.getPrivateKeyPath());
        config.setPrivateCertPath(properties.getPrivateCertPath());
        this.wxPayService = new WxPayServiceImpl();
        this.wxPayService.setConfig(config);
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
            wxRequest.setNotifyUrl(request.notifyUrl());
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
            amount.setRefund((int) request.amount());
            amount.setTotal((int) request.amount()); // 简化：退款金额=原单金额时
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

    @Override
    public List<PayChannelAdapter.BillItem> downloadBill(java.time.LocalDate date) {
        try {
            var request = new com.github.binarywang.wxpay.bean.request.WxPayDownloadBillRequest();
            request.setBillDate(date.toString());
            request.setBillType("ALL");
            var result = wxPayService.downloadBill(request);
            // 简化：解析账单内容为 BillItem 列表
            // 实际生产中需解析 CSV 格式
            log.info("微信账单下载成功: date={}", date);
            return List.of();
        } catch (WxPayException e) {
            log.error("微信账单下载失败: date={}", date, e);
            return List.of();
        }
    }
}
