package com.xuejiai.aaf.framework.agentscope.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** ConversationContextResolver 单元测试。 */
class ConversationContextResolverTest extends BaseMockitoUnitTest {

    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks ConversationContextResolver resolver;

    @Test
    void resolve_fromForwardedProps_noDbLookup() {
        var props =
                Map.<String, Object>of(
                        "userId",
                        1L,
                        "conversationId",
                        100L,
                        "knowledgeBaseId",
                        5L,
                        "assistantId",
                        2L);

        var ctx = resolver.resolve("t1", props, null);

        assertThat(ctx.userId()).isEqualTo(1L);
        assertThat(ctx.conversationId()).isEqualTo(100L);
        assertThat(ctx.knowledgeBaseId()).isEqualTo(5L);
        assertThat(ctx.assistantId()).isEqualTo(2L);
        assertThat(ctx.threadId()).isEqualTo("t1");
        // 全部字段来自 forwardedProps，不应查 DB
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(RowMapper.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolve_missingFields_fallsBackToDb() {
        // forwardedProps 只有 userId，其余字段从 DB 读
        var props = Map.<String, Object>of("userId", 1L);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("t1")))
                .thenAnswer(
                        inv -> {
                            RowMapper<?> rm = inv.getArgument(1);
                            var rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
                            when(rs.getLong("id")).thenReturn(100L);
                            when(rs.getObject("creator_id")).thenReturn(1L);
                            when(rs.getObject("assistant_id")).thenReturn(2L);
                            when(rs.getObject("knowledge_base_id")).thenReturn(5L);
                            return rm.mapRow(rs, 0);
                        });

        var ctx = resolver.resolve("t1", props, null);

        assertThat(ctx.userId()).isEqualTo(1L);
        assertThat(ctx.conversationId()).isEqualTo(100L);
        assertThat(ctx.assistantId()).isEqualTo(2L);
        assertThat(ctx.knowledgeBaseId()).isEqualTo(5L);
    }

    @Test
    void resolve_dbNotFound_fallsBackToJwt() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString()))
                .thenThrow(new EmptyResultDataAccessException(1));

        var ctx = resolver.resolve("t-unknown", null, 42L);

        assertThat(ctx.userId()).isEqualTo(42L);
        assertThat(ctx.conversationId()).isNull();
    }

    @Test
    void resolve_nullForwardedProps_doesNotThrow() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString()))
                .thenThrow(new EmptyResultDataAccessException(1));

        var ctx = resolver.resolve("t1", null, null);

        assertThat(ctx.userId()).isNull();
        assertThat(ctx.threadId()).isEqualTo("t1");
    }

    @Test
    void resolve_stringUserId_parsedCorrectly() {
        var props =
                Map.<String, Object>of(
                        "userId",
                        "123",
                        "conversationId",
                        "456",
                        "knowledgeBaseId",
                        "7",
                        "assistantId",
                        "8");

        var ctx = resolver.resolve("t1", props, null);

        assertThat(ctx.userId()).isEqualTo(123L);
        assertThat(ctx.conversationId()).isEqualTo(456L);
    }

    @Test
    void resolve_enableThinking_parsedFromProps() {
        var props =
                Map.<String, Object>of(
                        "userId",
                        1L,
                        "conversationId",
                        100L,
                        "knowledgeBaseId",
                        5L,
                        "assistantId",
                        2L,
                        "enableThinking",
                        true,
                        "thinkingBudget",
                        16000);

        var ctx = resolver.resolve("t1", props, null);

        assertThat(ctx.enableThinking()).isTrue();
        assertThat(ctx.thinkingBudget()).isEqualTo(16000);
    }

    @Test
    void resolve_enableThinking_absentMeansNull() {
        var props =
                Map.<String, Object>of(
                        "userId",
                        1L,
                        "conversationId",
                        100L,
                        "knowledgeBaseId",
                        5L,
                        "assistantId",
                        2L);

        var ctx = resolver.resolve("t1", props, null);

        assertThat(ctx.enableThinking()).isNull();
        assertThat(ctx.thinkingBudget()).isNull();
    }
}
