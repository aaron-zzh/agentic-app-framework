package com.xuejiai.aaf.framework.engine.settlement.channel;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.settlement.*;
import com.xuejiai.aaf.framework.engine.settlement.QueryResult;

import lombok.extern.slf4j.Slf4j;

/** 支付宝渠道适配器——支持 alipay_pc/alipay_wap/alipay_app/alipay_qr 四种下单方式。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "aaf.pay.alipay", name = "enabled", havingValue = "true")
public class AlipayChannelAdapter implements PayChannelAdapter {

    /** 手机网站支付渠道码——跳转表单 HTML 不落库，PayOrderService 据此渠道码识别并写入跳转接口地址 */
    public static final String CHANNEL_CODE_WAP = "alipay_wap";

    private static final List<String> SUPPORTED_CODES =
            List.of("alipay_pc", CHANNEL_CODE_WAP, "alipay_app", "alipay_qr");

    private final AlipayClient alipayClient;
    private final AlipayProperties properties;

    public AlipayChannelAdapter(AlipayProperties properties) {
        this.properties = properties;
        this.alipayClient =
                new DefaultAlipayClient(
                        properties.getServerUrl(),
                        properties.getAppId(),
                        properties.getPrivateKey(),
                        "json",
                        "UTF-8",
                        properties.getAlipayPublicKey(),
                        "RSA2");
        log.info("支付宝适配器初始化完成, appId={}", properties.getAppId());
    }

    @Override
    public List<String> supportedChannelCodes() {
        return SUPPORTED_CODES;
    }

    @Override
    public String channelCode() {
        return "alipay_pc";
    }

    @Override
    public PayResult charge(ChargeRequest request) {
        try {
            String body =
                    switch (request.channelCode()) {
                        case "alipay_pc" -> chargePagePay(request);
                        case "alipay_wap" -> chargeWapPay(request);
                        case "alipay_app" -> chargeAppPay(request);
                        case "alipay_qr" -> chargePrecreate(request);
                        default ->
                                throw new BusinessException(
                                        GlobalErrorCode.BAD_REQUEST,
                                        "不支持的支付宝渠道: " + request.channelCode());
                    };
            // alipay_qr 的 body 就是 qr_code URL，放进 codeUrl 透传给前端
            String codeUrl = "alipay_qr".equals(request.channelCode()) ? body : null;
            log.info(
                    "支付宝下单成功: outTradeNo={}, channelCode={}",
                    request.outTradeNo(),
                    request.channelCode());
            // 四种支付宝渠道均为跳转/扫码类，下单成功≠已支付，须等待异步回调或轮询确认
            return new PayResult(true, PayStatus.UNPAID, request.outTradeNo(), null, body, codeUrl);
        } catch (AlipayApiException e) {
            log.error("支付宝下单失败: outTradeNo={}, error={}", request.outTradeNo(), e.getMessage());
            return new PayResult(
                    false, PayStatus.UNPAID, request.outTradeNo(), null, e.getMessage());
        }
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        return new PayResult(false, PayStatus.UNPAID, request.outTradeNo(), null, "支付宝提现暂未实现");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        try {
            var model = new AlipayTradeRefundModel();
            model.setOutTradeNo(request.outTradeNo());
            model.setOutRequestNo(request.refundNo());
            model.setRefundAmount(formatAmount(request.amount()));
            model.setRefundReason(request.reason());

            var req = new AlipayTradeRefundRequest();
            req.setBizModel(model);
            AlipayTradeRefundResponse response = alipayClient.execute(req);
            if (response.isSuccess()) {
                return new RefundResult(true, request.refundNo(), "退款成功");
            }
            return new RefundResult(false, request.refundNo(), response.getSubMsg());
        } catch (AlipayApiException e) {
            log.error("支付宝退款失败: refundNo={}, error={}", request.refundNo(), e.getMessage());
            return new RefundResult(false, request.refundNo(), e.getMessage());
        }
    }

    @Override
    public QueryResult queryStatus(String outTradeNo) {
        try {
            var model = new AlipayTradeQueryModel();
            model.setOutTradeNo(outTradeNo);
            var req = new AlipayTradeQueryRequest();
            req.setBizModel(model);
            AlipayTradeQueryResponse response = alipayClient.execute(req);
            if (!response.isSuccess()) {
                // 交易不存在是明确的业务判定（非网关调用异常），区分对待以便提前关闭死单
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                    return new QueryResult(PayStatus.NOT_FOUND);
                }
                return null;
            }
            return switch (response.getTradeStatus()) {
                case "TRADE_SUCCESS", "TRADE_FINISHED" ->
                        new QueryResult(PayStatus.PAID, response.getTradeNo());
                case "TRADE_CLOSED" -> new QueryResult(PayStatus.CLOSED);
                default -> new QueryResult(PayStatus.UNPAID);
            };
        } catch (AlipayApiException e) {
            log.warn("支付宝查询订单状态失败: outTradeNo={}", outTradeNo);
            return null;
        }
    }

    /** 验证支付宝异步通知签名 */
    public boolean verifyNotify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(
                    params, properties.getAlipayPublicKey(), "UTF-8", "RSA2");
        } catch (AlipayApiException e) {
            log.error("支付宝签名验证失败", e);
            return false;
        }
    }

    /**
     * 手机网站支付跳转表单——按需实时生成，不落库存储。
     *
     * <p>跳转表单 HTML 只在用户点击跳转时才需要，且支付宝允许对同一笔未支付订单重复下单， 因此不在创建支付单时持久化这段 HTML，避免数据库存放大段冗余内容及过期风险。
     */
    public String buildWapPayForm(ChargeRequest request) throws AlipayApiException {
        return chargeWapPay(request);
    }

    /** 电脑网站支付跳转表单——按需实时生成，不落库存储，原因同手机网站支付。 */
    public String buildPagePayForm(ChargeRequest request) throws AlipayApiException {
        return chargePagePay(request);
    }

    @Override
    public List<PayChannelAdapter.BillItem> downloadBill(java.time.LocalDate date) {
        try {
            var model = new com.alipay.api.domain.AlipayDataDataserviceBillDownloadurlQueryModel();
            model.setBillType("trade");
            model.setBillDate(date.toString());
            var req = new com.alipay.api.request.AlipayDataDataserviceBillDownloadurlQueryRequest();
            req.setBizModel(model);
            var response = alipayClient.execute(req);
            if (response.isSuccess()) {
                // 简化：实际需下载 CSV 并解析
                log.info("支付宝账单下载地址: {}", response.getBillDownloadUrl());
            }
            return List.of();
        } catch (AlipayApiException e) {
            log.error("支付宝账单下载失败: date={}", date, e);
            return List.of();
        }
    }

    private String chargePagePay(ChargeRequest request) throws AlipayApiException {
        var model = new AlipayTradePagePayModel();
        model.setOutTradeNo(request.outTradeNo());
        model.setSubject(request.subject());
        model.setTotalAmount(formatAmount(request.amount()));
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        var req = new AlipayTradePagePayRequest();
        req.setBizModel(model);
        req.setNotifyUrl(properties.getNotifyUrl());
        req.setReturnUrl(properties.getReturnUrl());
        var response = alipayClient.pageExecute(req);
        // 页面跳转类接口本身不返回业务成功/失败（表单会直接跳转到支付宝页面），无需校验 isSuccess
        return response.getBody();
    }

    private String chargeWapPay(ChargeRequest request) throws AlipayApiException {
        var model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(request.outTradeNo());
        model.setSubject(request.subject());
        model.setTotalAmount(formatAmount(request.amount()));
        model.setProductCode("QUICK_WAP_WAY");
        var req = new AlipayTradeWapPayRequest();
        req.setBizModel(model);
        req.setNotifyUrl(properties.getNotifyUrl());
        req.setReturnUrl(properties.getReturnUrl());
        var response = alipayClient.pageExecute(req);
        return response.getBody();
    }

    private String chargeAppPay(ChargeRequest request) throws AlipayApiException {
        var model = new AlipayTradeAppPayModel();
        model.setOutTradeNo(request.outTradeNo());
        model.setSubject(request.subject());
        model.setTotalAmount(formatAmount(request.amount()));
        model.setProductCode("QUICK_MSECURITY_PAY");
        var req = new AlipayTradeAppPayRequest();
        req.setBizModel(model);
        req.setNotifyUrl(properties.getNotifyUrl());
        AlipayTradeAppPayResponse response = alipayClient.sdkExecute(req);
        return response.getBody();
    }

    private String chargePrecreate(ChargeRequest request) throws AlipayApiException {
        var model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(request.outTradeNo());
        model.setSubject(request.subject());
        model.setTotalAmount(formatAmount(request.amount()));
        var req = new AlipayTradePrecreateRequest();
        req.setBizModel(model);
        req.setNotifyUrl(properties.getNotifyUrl());
        AlipayTradePrecreateResponse response = alipayClient.execute(req);
        if (!response.isSuccess() || response.getQrCode() == null) {
            log.error(
                    "支付宝预下单业务失败: outTradeNo={}, code={}, subCode={}, subMsg={}",
                    request.outTradeNo(),
                    response.getCode(),
                    response.getSubCode(),
                    response.getSubMsg());
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "支付宝预下单失败: " + response.getSubMsg());
        }
        return response.getQrCode();
    }

    /** 分转元（保留两位小数） */
    private String formatAmount(long amountInFen) {
        return "%.2f".formatted(amountInFen / 100.0);
    }
}
