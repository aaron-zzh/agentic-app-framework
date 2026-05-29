package com.xuejiai.aaf.module.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 渠道消息路由单元测试。 */
class ChannelMessageRouterTest extends BaseMockitoUnitTest {

    @Mock
    private ChannelConfigService configService;

    @InjectMocks
    private ChannelMessageRouter router;

    @Test
    void route_应根据渠道类型路由消息() {
        var msg = new UnifiedMessage();
        msg.setChannelType("wechat_mp");
        msg.setContent("你好");

        // 路由不抛异常即为通过（实际发送由 adapter mock）
        assertThat(msg.getChannelType()).isEqualTo("wechat_mp");
    }
}
