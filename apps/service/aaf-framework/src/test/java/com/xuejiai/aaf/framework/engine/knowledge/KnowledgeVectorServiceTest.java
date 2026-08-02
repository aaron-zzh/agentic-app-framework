package com.xuejiai.aaf.framework.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.ProjectionSnapshot;

class KnowledgeVectorServiceTest {

    @Test
    @DisplayName("Given 知识库水位快照 When 重建向量 Then PostgreSQL 与 embedding chunk 集合双向全等才 READY")
    void should_require_exact_chunk_id_sets_before_ready() {
        var sql = new ArrayList<String>();
        var jdbcTemplate = jdbc(sql, true);
        var truthStore = mock(TrustedKnowledgeStore.class);
        var stableId = UUID.randomUUID();
        when(truthStore.projectionSnapshot(stableId))
                .thenReturn(new ProjectionSnapshot(7L, stableId, 12L));
        var service = service(jdbcTemplate, truthStore);

        service.rebuild(stableId);

        assertThat(sql)
                .anySatisfy(
                        statement -> {
                            assertThat(statement).contains("EXCEPT");
                            assertThat(statement)
                                    .contains("document.active_run_id = embedding.run_id");
                            assertThat(statement).contains("base.projection_watermark = ?");
                            assertThat(statement).contains("SET applied_watermark = ?");
                        });
    }

    @Test
    @DisplayName("Given rebuild 期间并发 publish 推进水位 When 最终重检 Then checkpoint 保持 DEGRADED")
    void should_degrade_when_publish_advances_watermark_during_rebuild() {
        var sql = new ArrayList<String>();
        var jdbcTemplate = jdbc(sql, false);
        var truthStore = mock(TrustedKnowledgeStore.class);
        var stableId = UUID.randomUUID();
        when(truthStore.projectionSnapshot(stableId))
                .thenReturn(new ProjectionSnapshot(7L, stableId, 12L));
        var service = service(jdbcTemplate, truthStore);

        service.rebuild(stableId);

        assertThat(sql)
                .anySatisfy(
                        statement ->
                                assertThat(statement)
                                        .contains("SET status = 'DEGRADED'")
                                        .contains("desired_watermark = ?"));
    }

    private KnowledgeVectorService service(
            JdbcTemplate jdbcTemplate, TrustedKnowledgeStore truthStore) {
        return new KnowledgeVectorService(
                mock(VectorStore.class),
                jdbcTemplate,
                mock(EmbeddingService.class),
                new EmbeddingProperties("model", 1536, 100, 1, 1),
                truthStore);
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbc(List<String> sql, boolean ready) {
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length > 0
                            && invocation.getArgument(0) instanceof String statement) {
                        sql.add(statement);
                        if ("query".equals(invocation.getMethod().getName())) {
                            return List.of();
                        }
                        if ("update".equals(invocation.getMethod().getName())) {
                            if (statement.contains("SET applied_watermark = ?")) {
                                return ready ? 1 : 0;
                            }
                            return 1;
                        }
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }
}
