package com.xuejiai.aaf.module.channel.service;

import java.util.Map;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;

/**
 * 渠道适配器接口——双向 IM 类渠道的核心抽象。
 *
 * <p>与 framework/messaging 的 ChannelSender（出站单向通知）不同， ChannelAdapter 负责入站消息接收解析 + 出站回复 + 模板消息推送。
 *
 * <p>分工：
 *
 * <ul>
 *   <li>ChannelAdapter：双向 IM 渠道（收消息 + 回消息 + 推模板）
 *   <li>ChannelSender：单向通知渠道（短信/邮件/站内信，只发不收）
 * </ul>
 */
public interface ChannelAdapter {

    /** 支持的渠道类型 */
    ChannelTypeEnum channelType();

    /**
     * 接收并解析入站消息。
     *
     * @param rawPayload 渠道原始报文（XML/JSON）
     * @return 统一消息模型
     */
    UnifiedMessage receive(String rawPayload);

    /**
     * 回复消息到渠道。
     *
     * @param message 统一消息模型（出站方向）
     */
    void reply(UnifiedMessage message);

    /**
     * 推送模板消息。
     *
     * @param externalUserId 渠道侧用户标识
     * @param templateId 模板 ID
     * @param variables 模板变量
     */
    void pushTemplate(String externalUserId, String templateId, Map<String, String> variables);

    /** 渠道是否可用 */
    default boolean isAvailable() {
        return true;
    }
}
