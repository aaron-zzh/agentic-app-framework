package com.xuejiai.aaf.framework.task.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AsyncTaskMessageTest {

    @Test
    @DisplayName("Given 首次任务失败 When 创建下一次尝试 Then 保留业务标识并递增 attempt")
    void should_increment_attempt_when_creating_next_attempt() {
        // 准备参数
        var createdAt = LocalDateTime.of(2026, 7, 30, 12, 0);
        var task = new AsyncTaskMessage("task-1", "TODO", "{}", 8, 3, 0, createdAt, null);

        // 调用
        var retry = task.nextAttempt("temporary failure");

        // 断言
        assertThat(retry.id()).isEqualTo("task-1");
        assertThat(retry.attempt()).isEqualTo(1);
        assertThat(retry.lastError()).isEqualTo("temporary failure");
        assertThat(retry.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Given attempt 超过最大重试次数 When 构造消息 Then 拒绝非法契约")
    void should_reject_attempt_when_exceeding_max_retries() {
        assertThatThrownBy(
                        () ->
                                new AsyncTaskMessage(
                                        "task-1", "TODO", "{}", 5, 1, 2, LocalDateTime.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }
}
