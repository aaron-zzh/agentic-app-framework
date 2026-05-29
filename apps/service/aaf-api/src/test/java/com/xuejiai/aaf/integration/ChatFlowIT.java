package com.xuejiai.aaf.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xuejiai.aaf.module.ai.chat.service.ChatService;

/** 对话核心流程集成测试。 */
@SpringBootTest
@ActiveProfiles({"test", "mock"})
class ChatFlowIT {

    @Autowired
    private ChatService chatService;

    @Test
    void 发送消息_应返回AI响应() {
        var response = chatService.chat(1L, "default-session", "你好，请介绍一下 AAF 框架");

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotBlank();
    }
}
