package com.xuejiai.aaf.module.channel.service.adapter;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateData;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;

/**
 * 微信公众号渠道适配器。
 *
 * <p>消息接收（文本/图片/语音/事件）与被动回复、模板消息推送、客服消息。 需配置 aaf.channel.wx.mp.enabled=true 激活。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.channel.wx.mp.enabled", havingValue = "true")
public class WechatMpChannelAdapter implements ChannelAdapter {

    private final WxMpService wxMpService;

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.WECHAT_MP;
    }

    @Override
    public UnifiedMessage receive(String rawPayload) {
        var xmlMsg = WxMpXmlMessage.fromXml(rawPayload);
        var msgType = xmlMsg.getMsgType();

        return switch (msgType) {
            case "text" ->
                    UnifiedMessage.inboundText(
                            ChannelTypeEnum.WECHAT_MP,
                            xmlMsg.getFromUser(),
                            xmlMsg.getContent(),
                            rawPayload);
            case "image" ->
                    new UnifiedMessage(
                            ChannelTypeEnum.WECHAT_MP,
                            com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum.INBOUND,
                            MessageTypeEnum.IMAGE,
                            xmlMsg.getFromUser(),
                            null,
                            xmlMsg.getPicUrl(),
                            null,
                            null,
                            Map.of("mediaId", xmlMsg.getMediaId()),
                            rawPayload,
                            java.time.LocalDateTime.now());
            case "voice" ->
                    new UnifiedMessage(
                            ChannelTypeEnum.WECHAT_MP,
                            com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum.INBOUND,
                            MessageTypeEnum.VOICE,
                            xmlMsg.getFromUser(),
                            xmlMsg.getRecognition(),
                            null,
                            null,
                            null,
                            Map.of("mediaId", xmlMsg.getMediaId()),
                            rawPayload,
                            java.time.LocalDateTime.now());
            case "event" ->
                    UnifiedMessage.inboundEvent(
                            ChannelTypeEnum.WECHAT_MP,
                            xmlMsg.getFromUser(),
                            xmlMsg.getEvent(),
                            xmlMsg.getEventKey(),
                            rawPayload);
            default ->
                    UnifiedMessage.inboundText(
                            ChannelTypeEnum.WECHAT_MP,
                            xmlMsg.getFromUser(),
                            "[不支持的消息类型: " + msgType + "]",
                            rawPayload);
        };
    }

    @Override
    public void reply(UnifiedMessage message) {
        try {
            var kefuMsg =
                    switch (message.messageType()) {
                        case TEXT ->
                                WxMpKefuMessage.TEXT()
                                        .toUser(message.externalUserId())
                                        .content(message.content())
                                        .build();
                        case IMAGE ->
                                WxMpKefuMessage.IMAGE()
                                        .toUser(message.externalUserId())
                                        .mediaId((String) message.extra().get("mediaId"))
                                        .build();
                        default ->
                                WxMpKefuMessage.TEXT()
                                        .toUser(message.externalUserId())
                                        .content(message.content() != null ? message.content() : "")
                                        .build();
                    };
            wxMpService.getKefuService().sendKefuMessage(kefuMsg);
        } catch (Exception e) {
            log.error("微信公众号回复失败: user={}, error={}", message.externalUserId(), e.getMessage());
        }
    }

    @Override
    public void pushTemplate(
            String externalUserId, String templateId, Map<String, String> variables) {
        try {
            var templateMsg =
                    WxMpTemplateMessage.builder()
                            .toUser(externalUserId)
                            .templateId(templateId)
                            .build();
            variables.forEach((k, v) -> templateMsg.addData(new WxMpTemplateData(k, v)));
            wxMpService.getTemplateMsgService().sendTemplateMsg(templateMsg);
        } catch (Exception e) {
            log.error(
                    "微信模板消息推送失败: user={}, template={}, error={}",
                    externalUserId,
                    templateId,
                    e.getMessage());
        }
    }
}
