package com.xuejiai.aaf.module.channel.service.adapter;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.ChannelAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * Mock 渠道适配器——无真实渠道配置时的默认实现。
 *
 * <p>所有操作仅打印日志，不做真实网络调用。
 */
@Slf4j
@Component
@ConditionalOnMissingBean(value = ChannelAdapter.class, ignored = MockChannelAdapter.class)
public class MockChannelAdapter implements ChannelAdapter {

    @Override
    public ChannelTypeEnum channelType() {
        return ChannelTypeEnum.WEB;
    }

    @Override
    public UnifiedMessage receive(String rawPayload) {
        log.info("[Mock] 接收消息: {}", rawPayload);
        return UnifiedMessage.inboundText(ChannelTypeEnum.WEB, "mock_user", rawPayload, rawPayload);
    }

    @Override
    public void reply(UnifiedMessage message) {
        log.info("[Mock] 回复消息: user={}, content={}",
                message.externalUserId(), message.content());
    }

    @Override
    public void pushTemplate(
            String externalUserId, String templateId, Map<String, String> variables) {
        log.info("[Mock] 推送模板: user={}, template={}, vars={}",
                externalUserId, templateId, variables);
    }
}
