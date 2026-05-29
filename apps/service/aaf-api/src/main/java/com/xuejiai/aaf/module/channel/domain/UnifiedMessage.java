package com.xuejiai.aaf.module.channel.domain;

import java.time.LocalDateTime;
import java.util.Map;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;

/**
 * 统一消息模型。
 *
 * <p>各渠道入站消息解析为此模型，业务处理后回复也通过此模型转回渠道格式。
 *
 * @param channelType 渠道类型
 * @param direction 消息方向
 * @param messageType 消息类型
 * @param externalUserId 渠道侧用户标识（openid / unionid / staffId）
 * @param content 文本内容（文本消息时有值）
 * @param mediaUrl 媒体 URL（图片/语音/视频时有值）
 * @param eventType 事件类型（subscribe/unsubscribe/click 等，事件消息时有值）
 * @param eventKey 事件 key（菜单点击 key 等）
 * @param extra 扩展字段（渠道特有数据）
 * @param rawPayload 原始报文（用于审计和排查）
 * @param timestamp 消息时间
 */
public record UnifiedMessage(
        ChannelTypeEnum channelType,
        MessageDirectionEnum direction,
        MessageTypeEnum messageType,
        String externalUserId,
        String content,
        String mediaUrl,
        String eventType,
        String eventKey,
        Map<String, Object> extra,
        String rawPayload,
        LocalDateTime timestamp) {

    /** 快捷构建入站文本消息 */
    public static UnifiedMessage inboundText(
            ChannelTypeEnum channel, String externalUserId, String content, String rawPayload) {
        return new UnifiedMessage(
                channel,
                MessageDirectionEnum.INBOUND,
                MessageTypeEnum.TEXT,
                externalUserId,
                content,
                null,
                null,
                null,
                Map.of(),
                rawPayload,
                LocalDateTime.now());
    }

    /** 快捷构建出站文本回复 */
    public static UnifiedMessage outboundText(
            ChannelTypeEnum channel, String externalUserId, String content) {
        return new UnifiedMessage(
                channel,
                MessageDirectionEnum.OUTBOUND,
                MessageTypeEnum.TEXT,
                externalUserId,
                content,
                null,
                null,
                null,
                Map.of(),
                null,
                LocalDateTime.now());
    }

    /** 快捷构建入站事件消息 */
    public static UnifiedMessage inboundEvent(
            ChannelTypeEnum channel,
            String externalUserId,
            String eventType,
            String eventKey,
            String rawPayload) {
        return new UnifiedMessage(
                channel,
                MessageDirectionEnum.INBOUND,
                MessageTypeEnum.EVENT,
                externalUserId,
                null,
                null,
                eventType,
                eventKey,
                Map.of(),
                rawPayload,
                LocalDateTime.now());
    }
}
