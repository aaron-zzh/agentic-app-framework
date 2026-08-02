package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;

class KnowledgeTemporalQueryTest {

    @Test
    @DisplayName("Given 当前事实查询 When 生成 SQL Then 使用数据库时钟和左闭右开断言区间")
    void should_use_database_clock_for_current_assertions() {
        var sql = new AtomicReference<String>();
        var store = new TrustedKnowledgeStore(capturingJdbc(sql));

        store.currentFactIds(UUID.randomUUID());

        assertThat(sql.get())
                .contains("assertion.recorded_at IS NOT NULL")
                .contains("assertion.expired_at IS NULL")
                .contains("assertion.valid_at <= CURRENT_TIMESTAMP")
                .contains("assertion.invalid_at > CURRENT_TIMESTAMP");
    }

    @Test
    @DisplayName("Given 显式 validAt 和 knownAt When 查询历史事实 Then 两个区间均按左闭右开过滤")
    void should_use_left_closed_right_open_predicate_for_as_of_query() {
        var sql = new AtomicReference<String>();
        var store = new TrustedKnowledgeStore(capturingJdbc(sql));
        var scope =
                new KnowledgeTemporalScope(
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-02-01T00:00:00Z"));

        store.factIds(UUID.randomUUID(), scope);

        assertThat(sql.get())
                .contains("assertion.valid_at <= ?")
                .contains("assertion.invalid_at > ?")
                .contains("assertion.recorded_at <= ?")
                .contains("assertion.expired_at > ?");
    }

    @Test
    @DisplayName("Given Neo4j 返回 factKey When 回源图候选 Then PostgreSQL 复核当前代际和当前断言")
    void should_revalidate_graph_candidates_against_current_temporal_truth() {
        var sql = new AtomicReference<String>();
        var store = new TrustedKnowledgeStore(capturingJdbc(sql));

        store.graphCandidates(
                Set.of("fact-key"),
                Set.of(UUID.randomUUID()),
                SourceFilters.from(Map.of()),
                10);

        assertThat(sql.get())
                .contains("d.active_run_id = e.run_id")
                .contains("assertion.recorded_at IS NOT NULL")
                .contains("assertion.expired_at IS NULL")
                .contains("assertion.valid_at <= CURRENT_TIMESTAMP")
                .contains("assertion.invalid_at > CURRENT_TIMESTAMP");
    }

    private JdbcTemplate capturingJdbc(AtomicReference<String> capturedSql) {
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if ("query".equals(invocation.getMethod().getName())
                            && invocation.getArgument(0) instanceof String sql) {
                        capturedSql.set(sql);
                        return List.of();
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }
}
