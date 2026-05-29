package com.xuejiai.aaf.module.livechat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.livechat.domain.ChatSession;
import com.xuejiai.aaf.module.livechat.repository.ChatSessionRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 客服会话服务单元测试。 */
class ChatSessionServiceTest extends BaseMockitoUnitTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @InjectMocks
    private ChatSessionService sessionService;

    @Test
    void createSession_应创建机器人服务状态会话() {
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var session = sessionService.createSession(1L, "wechat");

        assertThat(session).isNotNull();
        assertThat(session.getUserId()).isEqualTo(1L);
        assertThat(session.getStatus()).isEqualTo("bot");
    }
}
