package com.xuejiai.aaf.framework.messaging;

/**
 * 渠道发送的厂商响应信息。
 *
 * <p>对外部渠道（短信/邮件/钉钉等）调用厂商 API 后回传给上层的元信息， 由 MessageSendListener 写入 sys_message_log。
 *
 * <p>无外部厂商概念的渠道（如站内信）直接返回 {@link #empty()}。
 *
 * @param provider 厂商标识（aliyun/tencent 等）
 * @param apiRequestId 厂商请求 ID（如阿里云 BizId）
 * @param apiCode 厂商响应码（OK / IS_OUT_OF_SERVICE 等）
 * @param apiMsg 厂商响应描述
 */
public record ProviderResponse(
        String provider, String apiRequestId, String apiCode, String apiMsg) {

    private static final ProviderResponse EMPTY = new ProviderResponse(null, null, null, null);

    public static ProviderResponse empty() {
        return EMPTY;
    }

    public static ProviderResponse of(String provider) {
        return new ProviderResponse(provider, null, null, null);
    }
}
