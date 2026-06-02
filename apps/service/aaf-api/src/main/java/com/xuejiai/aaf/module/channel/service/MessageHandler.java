package com.xuejiai.aaf.module.channel.service;

import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;

/**
 * 消息处理器接口——业务层处理入站消息的扩展点。
 *
 * <p>AAF-076 客服模块通过实现此接口接入渠道消息。 多个 handler 按 {@link #order()} 排序，第一个返回非 null 结果的 handler 生效。
 */
public interface MessageHandler {

    /**
     * 处理入站消息。
     *
     * @param message 统一消息模型
     * @return 回复消息，null 表示不处理（交给下一个 handler）
     */
    UnifiedMessage handle(UnifiedMessage message);

    /** 是否支持处理该消息 */
    default boolean supports(UnifiedMessage message) {
        return true;
    }

    /** 排序（越小越优先） */
    default int order() {
        return 0;
    }
}
