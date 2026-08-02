package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

class KnowledgeSearchContractsTest {

    @Test
    @DisplayName("Given 权重 key 或 value 非法 When 构造授权查询 Then 统一拒绝")
    void should_reject_invalid_knowledge_base_weights_in_authorized_query() {
        var knowledgeBaseId = UUID.randomUUID();
        var nullKey = new HashMap<UUID, Double>();
        nullKey.put(null, 1.0);
        var nullValue = new HashMap<UUID, Double>();
        nullValue.put(knowledgeBaseId, null);

        assertThatThrownBy(() -> query(nullKey)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query(nullValue)).isInstanceOf(IllegalArgumentException.class);
        for (var invalid :
                List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.1)) {
            assertThatThrownBy(() -> query(Map.of(knowledgeBaseId, invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Given 零和有限正权重 When 构造授权查询 Then 保留不可变权重")
    void should_accept_finite_non_negative_knowledge_base_weights_in_authorized_query() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        var query = query(Map.of(first, 0.0, second, 1.5));

        assertThat(query.knowledgeBaseWeights())
                .containsEntry(first, 0.0)
                .containsEntry(second, 1.5);
        assertThatThrownBy(() -> query.knowledgeBaseWeights().put(UUID.randomUUID(), 2.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AuthorizedQuery query(Map<UUID, Double> weights) {
        return new AuthorizedQuery(
                new AuthorizationSubject(1L, 1L, 1L, 1L),
                "query",
                Set.of(),
                true,
                weights,
                null,
                5,
                0.7,
                Map.of());
    }
}
