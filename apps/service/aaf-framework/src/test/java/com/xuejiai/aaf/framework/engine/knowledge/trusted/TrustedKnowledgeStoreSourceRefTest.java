package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;

class TrustedKnowledgeStoreSourceRefTest {

    @Test
    @DisplayName("Given fact/evidence 聚合集合 When 最终来源复核 Then SQL 只返回当前 run 与焦点 chunk 的有效关联")
    void should_validate_current_fact_evidence_chain_in_postgres() {
        var capturedSql = new AtomicReference<String>();
        var jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            if ("query".equals(invocation.getMethod().getName())
                                    && invocation.getArgument(0) instanceof String sql) {
                                capturedSql.set(sql);
                                return List.of();
                            }
                            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                        });
        var store = new TrustedKnowledgeStore(jdbc);

        assertThat(
                        store.sourceRef(
                                UUID.randomUUID(),
                                Set.of(UUID.randomUUID()),
                                Set.of(UUID.randomUUID()),
                                Set.of(UUID.randomUUID()),
                                new SourceFilters(Set.of(), Set.of(), Set.of())))
                .isEmpty();

        assertThat(capturedSql.get())
                .contains("d.active_run_id = c.run_id")
                .contains("f.id = fe.fact_id")
                .contains("e.id = fe.evidence_id")
                .contains("e.run_id = c.run_id")
                .contains("e.focus_chunk_id = c.stable_id")
                .contains("fe.recorded_at IS NOT NULL")
                .contains("fe.expired_at IS NULL")
                .contains("fe.valid_at <= CURRENT_TIMESTAMP")
                .contains("fe.invalid_at > CURRENT_TIMESTAMP")
                .contains("validated.fact_ids IS NOT NULL");
    }
}
