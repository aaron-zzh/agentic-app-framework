package com.xuejiai.aaf.framework.engine.settlement;

import java.util.Map;

/**
 * 渠道异步通知信封（M28）。
 *
 * <p>统一承载各渠道验签所需的全部上下文，使 {@link PayChannelAdapter#verifyAndParseNotify} 成为通用契约，
 * 调用方（Controller）不再感知具体渠道 SDK：
 *
 * <ul>
 *   <li>微信 V3：验签需要原始报文 {@code rawBody} + {@code Wechatpay-*} 请求头
 *   <li>支付宝：验签需要表单参数 {@code formParams}
 * </ul>
 *
 * @param channelCode 渠道编码，用于路由到对应适配器
 * @param rawBody 原始请求体（微信必需；支付宝可为 null）
 * @param headers 请求头（大小写按原样传入，适配器自行取用）
 * @param formParams 表单/查询参数（支付宝必需；微信可为空 Map）
 */
public record NotifyEnvelope(
        String channelCode,
        String rawBody,
        Map<String, String> headers,
        Map<String, String> formParams) {

    /** 微信类回调：原始报文 + 请求头 */
    public static NotifyEnvelope ofBody(
            String channelCode, String rawBody, Map<String, String> headers) {
        return new NotifyEnvelope(channelCode, rawBody, headers, Map.of());
    }

    /** 支付宝类回调：表单参数 */
    public static NotifyEnvelope ofForm(String channelCode, Map<String, String> formParams) {
        return new NotifyEnvelope(channelCode, null, Map.of(), formParams);
    }
}
