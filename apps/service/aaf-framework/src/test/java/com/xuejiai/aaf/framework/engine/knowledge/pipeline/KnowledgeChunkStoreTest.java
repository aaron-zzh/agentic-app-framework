package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.engine.knowledge.chunker.DocumentChunk;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgeChunkStoreTest extends BaseMockitoUnitTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private KnowledgeChunkStore chunkStore;

    @Test
    @DisplayName("Given 文档重试 When 替换知识块 Then 先删除旧向量和旧块再写入新块")
    void should_delete_previous_data_before_inserting_new_chunks() {
        // 准备参数
        var chunk = new DocumentChunk("知识内容", 0, Map.of("page", 1), 4);
        when(jdbcTemplate.queryForObject(
                        anyString(),
                        eq(Long.class),
                        eq(11L),
                        eq(3L),
                        eq("知识内容"),
                        eq(0),
                        anyString(),
                        eq(4)))
                .thenReturn(101L);

        // 调用
        var ids = chunkStore.replace(3L, 11L, List.of(chunk));

        // 断言
        assertThat(ids).containsExactly(101L);
        var ordered = inOrder(jdbcTemplate);
        ordered.verify(jdbcTemplate)
                .update(
                        "DELETE FROM ai_knowledge_embedding WHERE metadata ->> 'document_id' = ?",
                        "11");
        ordered.verify(jdbcTemplate)
                .update("DELETE FROM ai_knowledge_chunk WHERE document_id = ?", 11L);
        ordered.verify(jdbcTemplate)
                .queryForObject(
                        anyString(),
                        eq(Long.class),
                        eq(11L),
                        eq(3L),
                        eq("知识内容"),
                        eq(0),
                        anyString(),
                        eq(4));
        verify(jdbcTemplate)
                .queryForObject(
                        anyString(),
                        eq(Long.class),
                        eq(11L),
                        eq(3L),
                        eq("知识内容"),
                        eq(0),
                        anyString(),
                        eq(4));
    }
}
