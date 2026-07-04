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
    QueryResult queryStatus(String channelCode, String outTradeNo);

    /** 关闭未支付交易——通知渠道侧同步关闭，避免渠道侧交易仍可支付而本地已判定关闭 */
    void close(String channelCode, String outTradeNo);

    /** 判断渠道是否受支持，用于下单前校验，避免创建业务订单后才发现渠道不支持 */
    boolean isChannelSupported(String channelCode);
}
