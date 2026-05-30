package com.xuejiai.aaf.framework.engine.settlement;

import java.time.LocalDate;
import java.util.List;

/** 支付渠道适配器接口 */
public interface PayChannelAdapter {

    /**
     * 该适配器支持的所有渠道编码。
     * 默认实现返回 {@link #channelCode()} 单元素列表，兼容已有单渠道适配器。
     */
    default List<String> supportedChannelCodes() {
        return List.of(channelCode());
    }

    /** 该适配器的主渠道编码（单渠道适配器使用） */
    String channelCode();

    /** 发起支付 */
    PayResult charge(ChargeRequest request);

    /** 发起提现打款 */
    PayResult withdraw(WithdrawRequest request);

    /** 发起退款 */
    RefundResult refund(RefundRequest request);

    /** 查询支付状态 */
    PayStatus queryStatus(String outTradeNo);

    /** 验证渠道异步通知签名（M28）。默认 fail-closed 拒绝；具体渠道适配器须覆盖实现真实验签。 */
    default boolean verifyNotify(java.util.Map<String, String> params) {
        return false;
    }

    /**
     * 下载渠道账单（对账用）。
     * 返回账单条目列表，每条包含：商户订单号、金额（分）、状态。
     * 默认返回空列表（Mock 渠道可覆盖生成模拟数据）。
     */
    default List<BillItem> downloadBill(LocalDate date) {
        return List.of();
    }

    /** 账单条目 */
    record BillItem(String outTradeNo, long amount, PayStatus status) {}
}

