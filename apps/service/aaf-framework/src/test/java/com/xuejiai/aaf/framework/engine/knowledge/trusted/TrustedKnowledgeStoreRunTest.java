package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.intelligent.core.prompt.ResolvedPromptTemplate;

class TrustedKnowledgeStoreRunTest {

    @Test
    @DisplayName("Given 同文档并发 beginRun When 分配 runNo Then 先锁文档再查询序号与插入")
    void should_lock_document_before_allocating_concurrent_run_number() {
        var sql = new ArrayList<String>();
        var jdbc = beginRunJdbc(sql);
        var store = new TrustedKnowledgeStore(jdbc);

        var run =
                store.beginRun(
                        1L,
                        2L,
                        "content-hash",
                        "fingerprint",
                        "parser",
                        "chunk",
                        snapshot(),
                        "embedding");

        assertThat(run.runNo()).isEqualTo(1L);
        assertThat(run.extractionSystemPrompt()).isEqualTo("prompt snapshot");
        assertThat(run.extractionModelId()).isEqualTo("extraction-model");
        assertThat(run.entityResolutionSystemPrompt()).isEqualTo("resolution prompt snapshot");
        var lockIndex = indexOf(sql, "FOR UPDATE OF d");
        var allocateIndex = indexOf(sql, "MAX(run_no)");
        var insertIndex = indexOf(sql, "INSERT INTO ai_knowledge_ingest_run");
        assertThat(lockIndex).isGreaterThanOrEqualTo(0);
        assertThat(allocateIndex).isGreaterThan(lockIndex);
        assertThat(insertIndex).isGreaterThan(allocateIndex);
    }

    @Test
    @DisplayName("Given 相同文档 fingerprint 已有 run When 再次 beginRun Then 恢复数据库冻结配置且不重复插入")
    void should_reuse_existing_run_for_same_fingerprint() {
        var existingRunId = UUID.randomUUID();
        var statements = new ArrayList<String>();
        var jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            if (invocation.getArguments().length == 0
                                    || !(invocation.getArgument(0) instanceof String statement)) {
                                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                            }
                            statements.add(statement);
                            if ("queryForObject".equals(invocation.getMethod().getName())
                                    && statement.contains("FOR UPDATE OF d")) {
                                @SuppressWarnings("unchecked")
                                var mapper = (RowMapper<Object>) invocation.getArgument(1);
                                var rs = mock(ResultSet.class);
                                when(rs.getObject(1, Long.class)).thenReturn(20L);
                                when(rs.getObject(2, Long.class)).thenReturn(10L);
                                when(rs.getString(3)).thenReturn("tenant");
                                when(rs.getObject(4, UUID.class)).thenReturn(null);
                                when(rs.getLong(5)).thenReturn(1L);
                                return mapper.mapRow(rs, 0);
                            }
                            if ("query".equals(invocation.getMethod().getName())) {
                                @SuppressWarnings("unchecked")
                                var mapper = (RowMapper<Object>) invocation.getArgument(1);
                                var rs = mock(ResultSet.class);
                                when(rs.getObject(1, UUID.class)).thenReturn(existingRunId);
                                when(rs.getLong(2)).thenReturn(3L);
                                when(rs.getLong(3)).thenReturn(1L);
                                when(rs.getLong(4)).thenReturn(2L);
                                when(rs.getLong(5)).thenReturn(10L);
                                when(rs.getString(6)).thenReturn("tenant");
                                when(rs.getObject(7, UUID.class)).thenReturn(null);
                                when(rs.getLong(8)).thenReturn(1L);
                                when(rs.getString(9)).thenReturn("PROCESSING");
                                when(rs.getString(10)).thenReturn("stored prompt");
                                when(rs.getString(11)).thenReturn("stored-prompt-digest");
                                when(rs.getInt(12)).thenReturn(1);
                                when(rs.getString(13)).thenReturn("stored-extraction-user-prompt");
                                when(rs.getString(14))
                                        .thenReturn("stored-extraction-user-prompt-digest");
                                when(rs.getInt(15)).thenReturn(1);
                                when(rs.getString(16))
                                        .thenReturn(EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION);
                                when(rs.getString(17)).thenReturn("stored-extraction-model");
                                when(rs.getString(18)).thenReturn("stored-resolution-prompt");
                                when(rs.getString(19))
                                        .thenReturn("stored-resolution-prompt-digest");
                                when(rs.getInt(20)).thenReturn(1);
                                when(rs.getString(21)).thenReturn("stored-resolution-user-prompt");
                                when(rs.getString(22))
                                        .thenReturn("stored-resolution-user-prompt-digest");
                                when(rs.getInt(23)).thenReturn(1);
                                when(rs.getString(24))
                                        .thenReturn(EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION);
                                when(rs.getString(25)).thenReturn("stored-resolution-model");
                                return List.of(mapper.mapRow(rs, 0));
                            }
                            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                        });

        var run =
                new TrustedKnowledgeStore(jdbc)
                        .beginRun(
                                1L,
                                2L,
                                "content-hash",
                                "fingerprint",
                                "parser",
                                "chunk",
                                snapshot(),
                                "embedding");

        assertThat(run.runId()).isEqualTo(existingRunId);
        assertThat(run.extractionSystemPrompt()).isEqualTo("stored prompt");
        assertThat(run.extractionModelId()).isEqualTo("stored-extraction-model");
        assertThat(run.entityResolutionSystemPrompt()).isEqualTo("stored-resolution-prompt");
        assertThat(run.entityResolutionPromptDigest()).isEqualTo("stored-resolution-prompt-digest");
        assertThat(run.entityResolutionModelId()).isEqualTo("stored-resolution-model");
        assertThat(statements)
                .noneMatch(statement -> statement.contains("INSERT INTO ai_knowledge_ingest_run"));
    }

    @Test
    @DisplayName("Given 新 worker 已推进 fencing token When 旧 worker 迟到 publish Then 拒绝回切当前代际")
    void should_reject_late_publish_after_fencing_token_advances() {
        var activeRun = UUID.randomUUID();
        var run =
                new RunContext(
                        UUID.randomUUID(),
                        2,
                        1L,
                        2L,
                        new BillingContext("tenant", UUID.randomUUID(), 10L),
                        activeRun,
                        4,
                        "PROCESSING",
                        "prompt snapshot",
                        "prompt-digest",
                        1,
                        "user prompt snapshot",
                        "user-prompt-digest",
                        1,
                        EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION,
                        "extraction-model",
                        "resolution prompt snapshot",
                        "resolution-prompt-digest",
                        1,
                        "resolution user prompt snapshot",
                        "resolution-user-prompt-digest",
                        1,
                        EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION,
                        "resolution-model");
        var jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            if (invocation.getArguments().length > 1
                                    && invocation.getArgument(0) instanceof String statement
                                    && statement.contains("active_run_id, ingest_fence")) {
                                @SuppressWarnings("unchecked")
                                var mapper = (RowMapper<Object>) invocation.getArgument(1);
                                var rs = mock(ResultSet.class);
                                when(rs.getObject(1, UUID.class)).thenReturn(activeRun);
                                when(rs.getLong(2)).thenReturn(5L);
                                return mapper.mapRow(rs, 0);
                            }
                            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                        });

        assertThatThrownBy(() -> new TrustedKnowledgeStore(jdbc).publish(run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fencing");
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate beginRunJdbc(List<String> sql) {
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length > 0
                            && invocation.getArgument(0) instanceof String statement) {
                        sql.add(statement);
                        if ("query".equals(invocation.getMethod().getName())) {
                            return List.of();
                        }
                        if ("queryForObject".equals(invocation.getMethod().getName())) {
                            if (statement.contains("FOR UPDATE OF d")) {
                                var mapper = (RowMapper<Object>) invocation.getArgument(1);
                                var rs = mock(ResultSet.class);
                                when(rs.getObject(1, Long.class)).thenReturn(20L);
                                when(rs.getObject(2, Long.class)).thenReturn(10L);
                                when(rs.getString(3)).thenReturn("tenant");
                                when(rs.getObject(4, UUID.class)).thenReturn(null);
                                when(rs.getLong(5)).thenReturn(0L);
                                return mapper.mapRow(rs, 0);
                            }
                            if (statement.contains("MAX(run_no)")) {
                                return 1L;
                            }
                        }
                        if ("update".equals(invocation.getMethod().getName())) {
                            return 1;
                        }
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }

    private KnowledgeIngestConfigurationService.Snapshot snapshot() {
        return new KnowledgeIngestConfigurationService.Snapshot(
                template("aaf.knowledge.fact-extraction.system", "prompt snapshot", 'a'),
                template("aaf.knowledge.fact-extraction.user", "user prompt snapshot", 'b'),
                EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION,
                "extraction-model",
                template(
                        "aaf.knowledge.entity-resolution.system",
                        "resolution prompt snapshot",
                        'c'),
                template(
                        "aaf.knowledge.entity-resolution.user",
                        "resolution user prompt snapshot",
                        'd'),
                EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION,
                "resolution-model");
    }

    private static ResolvedPromptTemplate template(String name, String content, char digestFill) {
        return new ResolvedPromptTemplate(name, 1, content, String.valueOf(digestFill).repeat(64));
    }

    private int indexOf(List<String> sql, String fragment) {
        for (var index = 0; index < sql.size(); index++) {
            if (sql.get(index).contains(fragment)) {
                return index;
            }
        }
        return -1;
    }
}
