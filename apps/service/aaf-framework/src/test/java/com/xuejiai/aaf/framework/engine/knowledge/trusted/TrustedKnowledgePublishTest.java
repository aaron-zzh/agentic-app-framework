package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
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

class TrustedKnowledgePublishTest {

    @Test
    @DisplayName("Given PROCESSING 代际 When 标记 READY Then 独立持久化检查点且不切换 active run")
    void should_persist_ready_checkpoint_without_switching_active_run() {
        var statements = new ArrayList<String>();
        var activeRun = UUID.randomUUID();
        var run = run(activeRun, "PROCESSING");
        var store = new TrustedKnowledgeStore(jdbc(statements, run, activeRun, "PROCESSING"));

        store.markReady(run);

        assertThat(statements)
                .anyMatch(sql -> sql.contains("SET status = 'READY'"))
                .noneMatch(sql -> sql.contains("SET active_run_id"));
    }

    @Test
    @DisplayName("Given READY 代际 When 发布 Then 同事务先记录新断言并结束旧断言再切换指针")
    void should_publish_temporal_assertions_before_switching_pointer() {
        var statements = new ArrayList<String>();
        var activeRun = UUID.randomUUID();
        var run = run(activeRun, "READY");
        var store = new TrustedKnowledgeStore(jdbc(statements, run, activeRun, "READY"));

        var receipt = store.publish(run);

        assertThat(receipt.chunkCount()).isEqualTo(2);
        assertThat(receipt.factCount()).isEqualTo(1);
        assertThat(indexOf(statements, "SET recorded_at = transaction_timestamp()"))
                .isLessThan(indexOf(statements, "SET expired_at = transaction_timestamp()"));
        assertThat(indexOf(statements, "SET expired_at = transaction_timestamp()"))
                .isLessThan(indexOf(statements, "SET active_run_id"));
        assertThat(indexOf(statements, "SET active_run_id"))
                .isLessThan(indexOf(statements, "SET status = 'PUBLISHED'"));
    }

    @Test
    @DisplayName("Given 已发布来源 When 撤销文档 Then 先结束当前断言再清除 active run")
    void should_expire_current_assertions_before_clearing_active_run() {
        var statements = new ArrayList<String>();
        var activeRun = UUID.randomUUID();
        var run = run(null, "PUBLISHED", activeRun);
        var store = new TrustedKnowledgeStore(jdbc(statements, run, activeRun, "PUBLISHED"));

        store.revokeDocument(run.documentId());

        assertThat(indexOf(statements, "SET expired_at = transaction_timestamp()"))
                .isLessThan(indexOf(statements, "SET active_run_id = NULL"));
        assertThat(statements)
                .anyMatch(sql -> sql.contains("expiration_reason = 'SOURCE_REVOKED'"));
    }

    @Test
    @DisplayName("Given 已发布代际 When 重试 publish Then 返回数据库统计且不重复切换")
    void should_return_idempotent_receipt_for_published_run() {
        var statements = new ArrayList<String>();
        var runId = UUID.randomUUID();
        var run = run(null, "PUBLISHED", runId);
        var store = new TrustedKnowledgeStore(jdbc(statements, run, runId, "PUBLISHED"));

        var receipt = store.publish(run);

        assertThat(receipt.chunkCount()).isEqualTo(2);
        assertThat(statements).noneMatch(sql -> sql.contains("SET active_run_id"));
    }

    @Test
    @DisplayName("Given 代际已经 PUBLISHED When 迟到失败回调 Then 不覆盖 run 或 document 状态")
    void should_ignore_failure_after_publication() {
        var statements = new ArrayList<String>();
        var runId = UUID.randomUUID();
        var run = run(null, "PUBLISHED", runId);
        var store = new TrustedKnowledgeStore(jdbc(statements, run, runId, "PUBLISHED"));

        var failed = store.failRun(run, "迟到失败");

        assertThat(failed).isFalse();
        assertThat(statements).noneMatch(sql -> sql.contains("SET status = 3"));
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbc(
            List<String> statements, RunContext run, UUID activeRun, String databaseStatus) {
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length == 0
                            || !(invocation.getArgument(0) instanceof String sql)) {
                        return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                    }
                    statements.add(sql);
                    var method = invocation.getMethod().getName();
                    if ("update".equals(method)) {
                        if (sql.contains("status IN ('PROCESSING', 'READY')")
                                && "PUBLISHED".equals(databaseStatus)) {
                            return 0;
                        }
                        return 1;
                    }
                    if ("queryForObject".equals(method)) {
                        if (sql.contains("SELECT knowledge_base_id, active_run_id")) {
                            var mapper = (RowMapper<Object>) invocation.getArgument(1);
                            var rs = mock(ResultSet.class);
                            when(rs.getLong(1)).thenReturn(run.knowledgeBaseId());
                            when(rs.getObject(2, UUID.class)).thenReturn(activeRun);
                            return mapper.mapRow(rs, 0);
                        }
                        if (sql.contains("active_run_id, ingest_fence")) {
                            var mapper = (RowMapper<Object>) invocation.getArgument(1);
                            var rs = mock(ResultSet.class);
                            when(rs.getObject(1, UUID.class)).thenReturn(activeRun);
                            when(rs.getLong(2)).thenReturn(run.fencingToken());
                            return mapper.mapRow(rs, 0);
                        }
                        if (sql.contains("SELECT status")) {
                            return databaseStatus;
                        }
                        if (sql.contains("COUNT(*) FROM ai_knowledge_chunk")) {
                            var mapper = (RowMapper<Object>) invocation.getArgument(1);
                            var rs = mock(ResultSet.class);
                            when(rs.getInt(1)).thenReturn(2);
                            when(rs.getInt(2)).thenReturn(1);
                            when(rs.getLong(3)).thenReturn(7L);
                            return mapper.mapRow(rs, 0);
                        }
                        if (sql.contains("RETURNING projection_watermark")) {
                            return 7L;
                        }
                        if (sql.contains("COUNT(*) FILTER")) {
                            var mapper = (RowMapper<Object>) invocation.getArgument(1);
                            var rs = mock(ResultSet.class);
                            when(rs.getLong(1)).thenReturn(0L);
                            when(rs.getLong(2)).thenReturn(0L);
                            return mapper.mapRow(rs, 0);
                        }
                        if (sql.contains("SELECT COUNT(*)")) {
                            return 1L;
                        }
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }

    private RunContext run(UUID activeRun, String status) {
        return run(activeRun, status, UUID.randomUUID());
    }

    private RunContext run(UUID activeRun, String status, UUID runId) {
        return new RunContext(
                runId,
                2,
                1L,
                2L,
                new BillingContext("tenant", runId, 10L),
                activeRun,
                4,
                status,
                "prompt",
                "prompt-digest",
                EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION,
                "extraction-model",
                "resolution prompt",
                "resolution-prompt-digest",
                EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION,
                "resolution-model");
    }

    private int indexOf(List<String> statements, String fragment) {
        for (var index = 0; index < statements.size(); index++) {
            if (statements.get(index).contains(fragment)) {
                return index;
            }
        }
        return -1;
    }
}
