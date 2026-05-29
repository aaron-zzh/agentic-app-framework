package com.xuejiai.aaf.framework.engine.settlement;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 模拟支付渠道适配器——直接返回成功，用于开发和测试。 */
@Slf4j
@Component
public class MockPayChannelAdapter implements PayChannelAdapter {

    public static final String CHANNEL_CODE = "MOCK";

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public PayResult charge(ChargeRequest request) {
        log.info("模拟支付: outTradeNo={}, amount={}", request.outTradeNo(), request.amount());
        var channelOrderNo = "MOCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new PayResult(true, request.outTradeNo(), channelOrderNo, "模拟支付成功");
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        log.info("模拟提现: outTradeNo={}, amount={}", request.outTradeNo(), request.amount());
        var channelOrderNo = "MOCK_W_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return new PayResult(true, request.outTradeNo(), channelOrderNo, "模拟提现成功");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.info("模拟退款: refundNo={}, amount={}", request.refundNo(), request.amount());
        return new RefundResult(true, request.refundNo(), "模拟退款成功");
    }

    @Override
    public PayStatus queryStatus(String outTradeNo) {
        return PayStatus.PAID;
    }
}
