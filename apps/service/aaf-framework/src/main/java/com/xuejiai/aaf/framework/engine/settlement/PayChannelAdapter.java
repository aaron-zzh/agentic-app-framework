package com.xuejiai.aaf.framework.engine.settlement;

import java.time.LocalDate;
import java.util.List;

/** 支付渠道适配器接口 */
public interface PayChannelAdapter {

    /** 该适配器支持的所有渠道编码。 默认实现返回 {@link #channelCode()} 单元素列表，兼容已有单渠道适配器。 */
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
    QueryResult queryStatus(String outTradeNo);

    /**
     * 关闭未支付交易——订单超时未支付时调用，通知渠道侧同步关闭该交易，避免渠道侧交易仍可被扫码/继续支付
     * 而本地订单已判定关闭，导致资金与状态不一致。默认空实现（Mock/余额支付等无需通知外部渠道）。
     */
    default void close(String outTradeNo) {}

    /**
     * 验签并解析渠道异步通知（M28）。
     *
     * <p>统一回调契约：调用方只组装 {@link NotifyEnvelope}（原始报文 + 请求头 + 表单参数），由适配器完成 验签与状态归一，不再需要按渠道分支调用具体
     * SDK。默认 fail-closed 拒绝，具体渠道必须覆盖。
     */
    default NotifyResult verifyAndParseNotify(NotifyEnvelope envelope) {
        return NotifyResult.rejected("该渠道未实现回调验签");
    }

    /** 下载渠道账单（对账用）。 返回账单条目列表，每条包含：商户订单号、金额（分）、状态。 默认返回空列表（Mock 渠道可覆盖生成模拟数据）。 */
    default List<BillItem> downloadBill(LocalDate date) {
        return List.of();
    }

    /** 账单条目 */
    record BillItem(String outTradeNo, long amount, PayStatus status) {}
}
