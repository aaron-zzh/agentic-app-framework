package com.xuejiai.aaf.framework.engine.settlement;

/** 支付渠道适配器接口 */
public interface PayChannelAdapter {

    /** 该适配器支持的渠道编码 */
    String channelCode();

    /** 发起支付 */
    PayResult charge(ChargeRequest request);

    /** 发起退款 */
    RefundResult refund(RefundRequest request);

    /** 查询支付状态 */
    PayStatus queryStatus(String outTradeNo);
}
