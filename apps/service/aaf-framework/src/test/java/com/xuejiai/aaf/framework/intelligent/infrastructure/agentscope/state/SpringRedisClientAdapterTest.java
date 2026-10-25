package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class SpringRedisClientAdapterTest extends BaseMockitoUnitTest {

    @Mock private StringRedisTemplate redisTemplate;

    private SpringRedisClientAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringRedisClientAdapter(redisTemplate);
    }

    @Test
    @DisplayName("Given Lua 脚本与键参数 When 原子执行 Then 返回 Long 结果并完整透传")
    void should_execute_script_and_return_long_result() {
        // 准备参数
        var script = "return 7";
        var keys = List.of("payload", "version", "session-keys");
        var args = List.of("json", "-1", "agent_state");
        when(redisTemplate.execute(
                        any(RedisScript.class), eq(keys), eq("json"), eq("-1"), eq("agent_state")))
                .thenReturn(7L);

        // 调用
        var result = adapter.evalScript(script, keys, args);

        // 断言
        assertThat(result).isEqualTo(7L);
        verify(redisTemplate)
                .execute(
                        org.mockito.ArgumentMatchers.argThat(
                                candidate ->
                                        script.equals(candidate.getScriptAsString())
                                                && Long.class.equals(candidate.getResultType())),
                        eq(keys),
                        eq("json"),
                        eq("-1"),
                        eq("agent_state"));
    }

    @Test
    @DisplayName("Given Redis 未返回脚本结果 When 原子执行 Then 抛出明确异常")
    void should_throw_when_script_result_is_null() {
        // mock 方法
        var keys = List.of("key");
        when(redisTemplate.execute(any(RedisScript.class), eq(keys), eq("arg"))).thenReturn(null);

        // 调用 + 断言
        assertThatThrownBy(() -> adapter.evalScript("return nil", keys, List.of("arg")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected Lua script result: null");
    }
}
