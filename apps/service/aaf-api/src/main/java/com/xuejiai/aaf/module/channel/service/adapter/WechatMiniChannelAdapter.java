package com.xuejiai.aaf.module.channel.service.adapter;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;

/**
 * 微信小程序渠道适配器（客服消息收发）。
 *
 * <p>小程序客服消息与公众号共用 weixin-java-mp SDK，
 * 区别在于 channelType 和配置项。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.channel.wx.mini.enabled", havingValue = "true")
public class WechatMiniChannelAdapter implements ChannelAdapter {

    private final WxMpService wxMiniMpService;

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.WECHAT_MINI;
    }

    @Override
    public UnifiedMessage receive(String rawPayload) {
        var xmlMsg = WxMpXmlMessage.fromXml(rawPayload);
        return switch (xmlMsg.getMsgType()) {
            case "text" -> UnifiedMessage.inboundText(
                    ChannelTypeEnum.WECHAT_MINI,
                    xmlMsg.getFromUser(),
                    xmlMsg.getContent(),
                    rawPayload);
            case "event" -> UnifiedMessage.inboundEvent(
                    ChannelTypeEnum.WECHAT_MINI,
                    xmlMsg.getFromUser(),
                    xmlMsg.getEvent(),
                    xmlMsg.getEventKey(),
                    rawPayload);
            default -> UnifiedMessage.inboundText(
                    ChannelTypeEnum.WECHAT_MINI,
                    xmlMsg.getFromUser(),
                    "[不支持的消息类型: " + xmlMsg.getMsgType() + "]",
                    rawPayload);
        };
    }

    @Override
    public void reply(UnifiedMessage message) {
        try {
            var kefuMsg = WxMpKefuMessage.TEXT()
                    .toUser(message.externalUserId())
                    .content(message.content() != null ? message.content() : "")
                    .build();
            wxMiniMpService.getKefuService().sendKefuMessage(kefuMsg);
        } catch (Exception e) {
            log.error("小程序客服消息回复失败: user={}, error={}",
                    message.externalUserId(), e.getMessage());
        }
    }

    @Override
    public void pushTemplate(
            String externalUserId, String templateId, Map<String, String> variables) {
        // 小程序使用订阅消息，暂不实现（需 weixin-java-miniapp SDK）
        log.warn("小程序模板消息暂未实现: user={}, template={}", externalUserId, templateId);
    }
}
