package com.xuejiai.aaf.module.channel.service.adapter;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.channel.MessageDirectionEnum;
import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelAdapter;
import com.xuejiai.aaf.module.customerservice.service.WecomKfApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 企微客服渠道适配器——将 customerservice 模块桥接到统一渠道体系。
 *
 * <p>入站消息由 WecomKfCallbackService 解密后调用 receive() 解析；
 * 出站回复通过 WecomKfApiClient 发送。
 *
 * <p>需配置 aaf.wecom.kf.enabled=true 激活。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.wecom.kf.enabled", havingValue = "true")
public class WecomKfChannelAdapter implements ChannelAdapter {

    private final WecomKfApiClient apiClient;

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.WECOM_KF;
    }

    @Override
    public UnifiedMessage receive(String rawPayload) {
        // 企微客服的入站消息由 WecomKfCallbackService 独立处理（XML 解密 + 拉取消息）
        // 此方法作为适配器契约实现，供未来统一入站路由时使用
        return new UnifiedMessage(
                ChannelTypeEnum.WECOM_KF,
                MessageDirectionEnum.INBOUND,
                MessageTypeEnum.TEXT,
                "unknown",
                rawPayload,
                null,
                null,
                null,
                Map.of(),
                rawPayload,
                LocalDateTime.now());
    }

    @Override
    public void reply(UnifiedMessage message) {
        var externalUserId = message.externalUserId();
        var content = message.content();
        if (externalUserId == null || content == null) {
            log.warn("企微客服回复缺少必要参数");
            return;
        }
        // 从 extra 中获取 openKfId
        var openKfId = message.extra() != null
                ? (String) message.extra().get("openKfId")
                : null;
        if (openKfId == null) {
            log.warn("企微客服回复缺少 openKfId");
            return;
        }
        apiClient.sendTextMsg(openKfId, externalUserId, content);
    }

    @Override
    public void pushTemplate(String externalUserId, String templateId, Map<String, String> variables) {
        // 企微客服不支持模板消息，忽略
        log.debug("企微客服不支持模板消息推送");
    }

    @Override
    public boolean isAvailable() {
        try {
            return apiClient.getAccessToken() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
