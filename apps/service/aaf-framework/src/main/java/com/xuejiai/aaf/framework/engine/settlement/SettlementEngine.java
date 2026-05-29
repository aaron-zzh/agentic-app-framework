package com.xuejiai.aaf.framework.engine.settlement;

/**
 * 结算引擎——支付发起、提现打款、退款、状态查询。
 *
 * <p>通过 PayChannelAdapter 适配不同支付渠道。
 */
public interface SettlementEngine {

    /** 发起支付 */
    PayResult charge(ChargeRequest request);

    /** 发起提现打款 */
    PayResult withdraw(WithdrawRequest request);

    /** 发起退款 */
    RefundResult refund(RefundRequest request);

    /** 查询支付状态 */
    PayStatus queryStatus(String outTradeNo);
}
