package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;

class DefaultKnowledgeUsageServiceTest {

    @Test
    @DisplayName("Given 相同 ingest unit 已在有效租约内执行 When 并发 reserve Then 后继调用者得到 BUSY")
    void should_allow_only_one_concurrent_provider_caller() {
        var sql = new ArrayList<String>();
        var service =
                new DefaultKnowledgeUsageService(
                        mock(AiCreditGuard.class),
                        jdbc(sql, "IN_PROGRESS", Instant.now().plusSeconds(300)));
        var billing = new KnowledgeUsagePort.BillingContext("tenant", UUID.randomUUID(), 10L);

        var reservation = service.reserve(billing, "embedding", "chunks", "digest");

        assertThat(reservation.state()).isEqualTo(KnowledgeUsagePort.ReservationState.BUSY);
        assertThat(sql).noneMatch(statement -> statement.contains("SET status = 'UNKNOWN'"));
    }

    @Test
    @DisplayName("Given IN_PROGRESS 调用者租约过期且无 provider 结果 When 恢复 Then 标记 UNKNOWN 禁止自动重发")
    void should_mark_expired_in_progress_call_unknown() {
        var sql = new ArrayList<String>();
        var service =
                new DefaultKnowledgeUsageService(
                        mock(AiCreditGuard.class),
                        jdbc(sql, "IN_PROGRESS", Instant.now().minusSeconds(1)));
        var billing = new KnowledgeUsagePort.BillingContext("tenant", UUID.randomUUID(), 10L);

        var reservation = service.reserve(billing, "embedding", "chunks", "digest");

        assertThat(reservation.state()).isEqualTo(KnowledgeUsagePort.ReservationState.UNKNOWN);
        assertThat(sql).anyMatch(statement -> statement.contains("SET status = 'UNKNOWN'"));
    }

    @Test
    @DisplayName("Given 过期 IN_PROGRESS 抢占 CAS 失败且最新行已完成 When reserve Then 返回最新 COMPLETED")
    void should_reload_latest_state_when_expired_in_progress_cas_fails() {
        var sql = new ArrayList<String>();
        var service =
                new DefaultKnowledgeUsageService(
                        mock(AiCreditGuard.class), jdbcAfterFailedUnknownCas(sql));
        var billing = new KnowledgeUsagePort.BillingContext("tenant", UUID.randomUUID(), 10L);

        var reservation = service.reserve(billing, "embedding", "chunks", "digest");

        assertThat(reservation.state()).isEqualTo(KnowledgeUsagePort.ReservationState.COMPLETED);
        assertThat(reservation.providerResult()).isEqualTo("{\"ok\":true}");
        assertThat(sql)
                .filteredOn(statement -> statement.contains("FROM ai_knowledge_ingest_unit"))
                .hasSize(2);
    }

    @Test
    @DisplayName("Given RESERVED 租约已过期且 provider 尚未调用 When 新调用者 reserve Then 安全重新领取")
    void should_reclaim_expired_reservation_before_provider_call() {
        var sql = new ArrayList<String>();
        var service =
                new DefaultKnowledgeUsageService(
                        mock(AiCreditGuard.class),
                        jdbc(sql, "RESERVED", Instant.now().minusSeconds(1)));
        var billing = new KnowledgeUsagePort.BillingContext("tenant", UUID.randomUUID(), 10L);

        var reservation = service.reserve(billing, "embedding", "chunks", "digest");

        assertThat(reservation.state()).isEqualTo(KnowledgeUsagePort.ReservationState.RESERVED);
        assertThat(sql)
                .anyMatch(
                        statement ->
                                statement.contains("SET reservation_token = ?")
                                        && statement.contains("status = 'RESERVED'"));
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbcAfterFailedUnknownCas(List<String> sql) {
        var queryCount = new AtomicInteger();
        var occurredAt = Instant.parse("2026-01-02T03:04:05Z");
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length == 0
                            || !(invocation.getArgument(0) instanceof String statement)) {
                        return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                    }
                    sql.add(statement);
                    if ("update".equals(invocation.getMethod().getName())) {
                        return 0;
                    }
                    if ("query".equals(invocation.getMethod().getName())) {
                        var latest = queryCount.getAndIncrement() > 0;
                        var mapper = (RowMapper<Object>) invocation.getArgument(1);
                        var rs = mock(ResultSet.class);
                        when(rs.getString(1)).thenReturn("invocation");
                        when(rs.getString(2)).thenReturn("usage");
                        when(rs.getString(3)).thenReturn("digest");
                        when(rs.getString(4)).thenReturn(latest ? "{\"ok\":true}" : null);
                        when(rs.getString(5)).thenReturn("provider-request");
                        when(rs.getTimestamp(6)).thenReturn(java.sql.Timestamp.from(occurredAt));
                        when(rs.getString(7)).thenReturn(latest ? "COMPLETED" : "IN_PROGRESS");
                        when(rs.getObject(8, UUID.class)).thenReturn(UUID.randomUUID());
                        when(rs.getTimestamp(9))
                                .thenReturn(
                                        java.sql.Timestamp.from(
                                                latest
                                                        ? Instant.now().plusSeconds(300)
                                                        : Instant.now().minusSeconds(1)));
                        return List.of(mapper.mapRow(rs, 0));
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbc(List<String> sql, String status, Instant leaseUntil) {
        var invocationId = "invocation";
        var usageKey = "usage";
        var occurredAt = Instant.parse("2026-01-02T03:04:05Z");
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length == 0
                            || !(invocation.getArgument(0) instanceof String statement)) {
                        return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                    }
                    sql.add(statement);
                    if ("update".equals(invocation.getMethod().getName())) {
                        if (statement.startsWith("INSERT INTO ai_knowledge_ingest_unit")) {
                            return 0;
                        }
                        return 1;
                    }
                    if ("query".equals(invocation.getMethod().getName())) {
                        var mapper = (RowMapper<Object>) invocation.getArgument(1);
                        var rs = mock(ResultSet.class);
                        when(rs.getString(1)).thenReturn(invocationId);
                        when(rs.getString(2)).thenReturn(usageKey);
                        when(rs.getString(3)).thenReturn("digest");
                        when(rs.getString(4)).thenReturn(null);
                        when(rs.getString(5)).thenReturn(invocationId);
                        when(rs.getTimestamp(6)).thenReturn(java.sql.Timestamp.from(occurredAt));
                        when(rs.getString(7)).thenReturn(status);
                        when(rs.getObject(8, UUID.class)).thenReturn(UUID.randomUUID());
                        when(rs.getTimestamp(9)).thenReturn(java.sql.Timestamp.from(leaseUntil));
                        return List.of(mapper.mapRow(rs, 0));
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }
}
