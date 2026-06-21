package com.xuejiai.aaf.framework.engine.settlement.channel;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.settlement.ChargeRequest;
import com.xuejiai.aaf.framework.engine.settlement.PayChannelAdapter;
import com.xuejiai.aaf.framework.engine.settlement.PayResult;
import com.xuejiai.aaf.framework.engine.settlement.PayStatus;
import com.xuejiai.aaf.framework.engine.settlement.RefundRequest;
import com.xuejiai.aaf.framework.engine.settlement.RefundResult;
import com.xuejiai.aaf.framework.engine.settlement.WithdrawRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * 分销余额支付渠道适配器。
 *
 * <p>余额扣减不在此处发生——实际扣减由 {@link PayOrderService} 在创建支付单时完成（需知道 userId）。 此适配器仅作占位，向 SettlementEngine
 * 注册渠道编码，charge() 直接返回成功（余额已在上层扣减）。
 */
@Slf4j
@Component
public class BrokerageBalanceChannelAdapter implements PayChannelAdapter {

    public static final String CHANNEL_CODE = "brokerage_balance";

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public PayResult charge(ChargeRequest request) {
        // 余额已在 PayOrderService 层扣减，此处直接返回成功
        log.info("余额支付完成: outTradeNo={}, amount={}", request.outTradeNo(), request.amount());
        return new PayResult(true, request.outTradeNo(), "BAL_" + request.outTradeNo(), "余额支付成功");
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        return new PayResult(false, request.outTradeNo(), null, "余额渠道不支持提现");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        // 退款：返还余额，由上层处理
        log.info("余额退款: refundNo={}, amount={}", request.refundNo(), request.amount());
        return new RefundResult(true, request.refundNo(), "余额退款成功");
    }

    @Override
    public PayStatus queryStatus(String outTradeNo) {
        // 余额支付同步完成，无需查询
        return null;
    }

    @Override
    public boolean verifyNotify(java.util.Map<String, String> params) {
        // 余额支付无异步通知，不需要验签
        return false;
    }
}
