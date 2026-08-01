package com.xuejiai.aaf.framework.engine.settlement;

/**
 * 渠道通知验签解析结果（M28）——统一 Wx/Alipay 的验签结果模型。
 *
 * @param verified 验签是否通过；false 时其余字段无意义，调用方必须直接拒绝
 * @param status 渠道侧交易状态（已归一为 {@link PayStatus}）
 * @param outTradeNo 商户订单号
 * @param channelOrderNo 渠道交易号
 * @param rawStatus 渠道原始状态文本，仅用于日志排查
 */
public record NotifyResult(
        boolean verified,
        PayStatus status,
        String outTradeNo,
        String channelOrderNo,
        String rawStatus) {

    /** 验签失败/未实现——fail-closed 默认值 */
    public static NotifyResult rejected(String rawStatus) {
        return new NotifyResult(false, PayStatus.UNPAID, null, null, rawStatus);
    }

    /** 验签通过且渠道判定支付成功 */
    public static NotifyResult paid(String outTradeNo, String channelOrderNo, String rawStatus) {
        return new NotifyResult(true, PayStatus.PAID, outTradeNo, channelOrderNo, rawStatus);
    }

    /** 验签通过但尚未支付成功（如已关闭、退款中等），调用方不应触发入账 */
    public static NotifyResult verifiedButNotPaid(
            PayStatus status, String outTradeNo, String rawStatus) {
        return new NotifyResult(true, status, outTradeNo, null, rawStatus);
    }

    /** 是否应触发支付成功后续处理 */
    public boolean isPaySuccess() {
        return verified && status == PayStatus.PAID;
    }
}
