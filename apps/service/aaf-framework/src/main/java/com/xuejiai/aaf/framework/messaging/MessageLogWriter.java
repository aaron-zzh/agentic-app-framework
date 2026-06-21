package com.xuejiai.aaf.framework.messaging;

import java.util.List;

/** 消息日志写入接口，由业务层实现（依赖倒置，framework 不依赖 api）。 */
public interface MessageLogWriter {

    /** 创建待发送日志，返回日志 ID */
    Long createPending(
            String channel,
            String templateCode,
            List<String> recipients,
            String subject,
            String content);

    /**
     * 写入发送结果。
     *
     * @param logId 日志 ID（createPending 返回）
     * @param success 是否成功
     * @param errorMsg 错误描述（success=false 时填）
     * @param response 厂商响应（短信/邮件等外部渠道；站内信传 {@link ProviderResponse#empty()}）
     */
    void markResult(Long logId, boolean success, String errorMsg, ProviderResponse response);
}
