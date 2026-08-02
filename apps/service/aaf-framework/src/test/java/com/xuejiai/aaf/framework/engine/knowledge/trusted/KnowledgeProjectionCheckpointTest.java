package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.ProjectionRebuild;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.ProjectionSnapshot;

class KnowledgeProjectionCheckpointTest {

    @Test
    @DisplayName("Given 多文档水位范围内无 outbox When 刷新 checkpoint Then 零 fact run 收敛为 READY")
    void should_converge_zero_fact_generation_at_knowledge_base_watermark() {
        var calls = new ArrayList<SqlCall>();
        var store = new TrustedKnowledgeStore(jdbc(calls, 0, 0, 7));

        store.refreshGraphCheckpoint(9L);

        assertThat(calls)
                .anySatisfy(
                        call ->
                                assertThat(call.sql())
                                        .contains(
                                                "outbox.projection_watermark > checkpoint.applied_watermark")
                                        .contains(
                                                "outbox.projection_watermark <= checkpoint.desired_watermark"));
        assertThat(calls)
                .filteredOn(call -> call.sql().contains("SET applied_watermark"))
                .singleElement()
                .satisfies(call -> assertThat(call.arguments()).contains(true));
    }

    @Test
    @DisplayName("Given 当前水位范围存在 DEAD outbox When 刷新 checkpoint Then 不得进入 READY")
    void should_keep_graph_degraded_when_dead_event_exists() {
        var calls = new ArrayList<SqlCall>();
        var store = new TrustedKnowledgeStore(jdbc(calls, 1, 1, 7));

        store.refreshGraphCheckpoint(9L);

        assertThat(calls)
                .filteredOn(call -> call.sql().contains("SET applied_watermark"))
                .singleElement()
                .satisfies(call -> assertThat(call.arguments()).contains(false, 1L));
    }

    @Test
    @DisplayName("Given 集合验证成功且水位未变化 When 完成 rebuild Then 仅所有者推进 applied 水位")
    void should_complete_rebuild_only_for_same_request_and_watermark() {
        var calls = new ArrayList<SqlCall>();
        var store = new TrustedKnowledgeStore(jdbc(calls, 0, 0, 7));
        var rebuild =
                new ProjectionRebuild(
                        new ProjectionSnapshot(9L, UUID.randomUUID(), 7L), "request-1", true);

        assertThat(store.completeGraphRebuild(rebuild, true, null)).isTrue();

        assertThat(calls)
                .anySatisfy(
                        call ->
                                assertThat(call.sql())
                                        .contains("rebuild_request_key = ?")
                                        .contains("status = 'REBUILDING'")
                                        .contains("desired_watermark = ?"));
        assertThat(calls)
                .anySatisfy(
                        call ->
                                assertThat(call.sql())
                                        .contains("projection_watermark <= ?")
                                        .contains("status = 'DONE'"));
        assertThat(calls)
                .anySatisfy(
                        call ->
                                assertThat(call.sql())
                                        .contains("checkpoint.status = 'REBUILDING'")
                                        .contains("status = 'READY'"));
    }

    @Test
    @DisplayName("Given 同 requestKey 已持有同水位 rebuild When 并发重试 Then 复用且仅首个执行")
    void should_reuse_same_request_rebuild_owner() {
        var stableId = UUID.randomUUID();
        var calls = new ArrayList<SqlCall>();
        var store =
                new TrustedKnowledgeStore(rebuildJdbc(calls, stableId, new AtomicBoolean(true)));

        var first = store.beginGraphRebuild(stableId, "request-1");
        var retry = store.beginGraphRebuild(stableId, "request-1");

        assertThat(List.of(first.execute(), retry.execute())).containsExactly(true, false);
        assertAtomicOwnershipSql(calls);
    }

    @Test
    @DisplayName("Given 不同 requestKey 并发申请同水位 rebuild When 数据库 CAS Then 仅一方执行")
    void should_allow_only_one_different_request_to_execute() {
        var stableId = UUID.randomUUID();
        var calls = new ArrayList<SqlCall>();
        var store =
                new TrustedKnowledgeStore(rebuildJdbc(calls, stableId, new AtomicBoolean(true)));

        var first = store.beginGraphRebuild(stableId, "request-1");
        var second = store.beginGraphRebuild(stableId, "request-2");

        assertThat(List.of(first.execute(), second.execute())).containsExactly(true, false);
        assertAtomicOwnershipSql(calls);
    }

    @Test
    @DisplayName("Given 同 requestKey 已完成同水位 rebuild When 重试 Then CAS 幂等 no-op")
    void should_noop_completed_rebuild_for_same_request_key() {
        var stableId = UUID.randomUUID();
        var calls = new ArrayList<SqlCall>();
        var store =
                new TrustedKnowledgeStore(rebuildJdbc(calls, stableId, new AtomicBoolean(false)));

        var rebuild = store.beginGraphRebuild(stableId, "request-1");

        assertThat(rebuild.execute()).isFalse();
        assertAtomicOwnershipSql(calls);
    }

    @Test
    @DisplayName("Given 新轮次已取得所有权 When 旧轮次迟到完成或失败 Then 不覆盖 checkpoint 或 outbox")
    void should_ignore_late_rebuild_completion_and_failure() {
        var calls = new ArrayList<SqlCall>();
        var store = new TrustedKnowledgeStore(jdbc(calls, 0, 0, 7, false));
        var rebuild =
                new ProjectionRebuild(
                        new ProjectionSnapshot(9L, UUID.randomUUID(), 7L), "old-request", true);

        assertThat(store.completeGraphRebuild(rebuild, true, null)).isFalse();
        assertThat(store.completeGraphRebuild(rebuild, false, "late failure")).isFalse();

        assertThat(calls)
                .filteredOn(call -> call.sql().contains("SET updated_at = updated_at"))
                .hasSize(2)
                .allSatisfy(
                        call ->
                                assertThat(call.sql())
                                        .contains("rebuild_request_key = ?")
                                        .contains("status = 'REBUILDING'")
                                        .contains("desired_watermark = ?"));
        assertThat(calls)
                .noneMatch(
                        call ->
                                call.sql().contains("UPDATE ai_knowledge_graph_outbox")
                                        || call.sql().contains("SET status = 'DEGRADED'")
                                        || call.sql().contains("SET desired_watermark = ?"));
    }

    @Test
    @DisplayName("Given rebuild 已验证旧事件 When 迟到 worker 完成或失败 Then 仅允许从 PROCESSING 状态回写")
    void should_cas_late_outbox_worker_updates() {
        var calls = new ArrayList<SqlCall>();
        var store = new TrustedKnowledgeStore(jdbc(calls, 0, 0, 7));
        var event =
                new TrustedKnowledgeStore.OutboxEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "FACT_UPSERT",
                        9L,
                        UUID.randomUUID(),
                        7L,
                        1);

        store.completeOutbox(event);
        store.failOutbox(event, "late failure");

        assertThat(calls)
                .filteredOn(call -> call.sql().contains("WHERE event_id = ?"))
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.sql()).contains("status = 'PROCESSING'"));
    }

    @Test
    @DisplayName("Given rebuild 幂等键为空或超长 When 申请重建 Then 在访问数据库前拒绝")
    void should_reject_invalid_rebuild_request_key() {
        var store = new TrustedKnowledgeStore(mock(JdbcTemplate.class));
        var knowledgeBaseId = UUID.randomUUID();

        assertThatThrownBy(() -> store.beginGraphRebuild(knowledgeBaseId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> store.beginGraphRebuild(knowledgeBaseId, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> store.beginGraphRebuild(knowledgeBaseId, "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过 200");
    }

    @Test
    @DisplayName("Given Neo4j checkpoint 与知识库水位一致 When 查询状态 Then 返回 READY")
    void should_report_ready_graph_projection_status() {
        var knowledgeBaseId = UUID.randomUUID();
        var updatedAt = LocalDateTime.of(2026, 8, 2, 11, 30);
        var calls = new ArrayList<SqlCall>();
        var store =
                new TrustedKnowledgeStore(projectionStatusJdbc(calls, knowledgeBaseId, updatedAt));

        var status = store.graphProjectionStatus(knowledgeBaseId).orElseThrow();

        assertThat(status.knowledgeBaseId()).isEqualTo(knowledgeBaseId);
        assertThat(status.baseWatermark()).isEqualTo(7);
        assertThat(status.desiredWatermark()).isEqualTo(7);
        assertThat(status.appliedWatermark()).isEqualTo(7);
        assertThat(status.status()).isEqualTo("READY");
        assertThat(status.rebuildRequestKey()).isEqualTo("request-1");
        assertThat(status.errorMessage()).isNull();
        assertThat(status.updatedAt()).isEqualTo(updatedAt);
        assertThat(status.ready()).isTrue();
        assertThat(calls)
                .singleElement()
                .satisfies(
                        call ->
                                assertThat(call.sql())
                                        .contains("LEFT JOIN ai_knowledge_projection_checkpoint")
                                        .contains("checkpoint.projection_kind = 'NEO4J'")
                                        .contains("base.deleted = false"));
    }

    private void assertAtomicOwnershipSql(List<SqlCall> calls) {
        assertThat(calls)
                .filteredOn(
                        call ->
                                call.sql()
                                        .contains("INSERT INTO ai_knowledge_projection_checkpoint"))
                .isNotEmpty()
                .allSatisfy(
                        call ->
                                assertThat(call.sql())
                                        .contains("ON CONFLICT")
                                        .contains("status <> 'REBUILDING'")
                                        .contains("EXCLUDED.desired_watermark")
                                        .contains("RETURNING desired_watermark"));
    }

    private JdbcTemplate jdbc(
            List<SqlCall> calls, long unfinished, long dead, long currentWatermark) {
        return jdbc(calls, unfinished, dead, currentWatermark, true);
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbc(
            List<SqlCall> calls,
            long unfinished,
            long dead,
            long currentWatermark,
            boolean ownsRebuild) {
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length == 0
                            || !(invocation.getArgument(0) instanceof String sql)) {
                        return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                    }
                    calls.add(new SqlCall(sql, arguments(invocation.getArguments())));
                    if ("queryForObject".equals(invocation.getMethod().getName())) {
                        if (sql.contains("COUNT(*) FILTER")) {
                            var mapper = (RowMapper<Object>) invocation.getArgument(1);
                            var resultSet = mock(ResultSet.class);
                            when(resultSet.getLong(1)).thenReturn(unfinished);
                            when(resultSet.getLong(2)).thenReturn(dead);
                            return mapper.mapRow(resultSet, 0);
                        }
                        if (sql.contains("SELECT projection_watermark")) {
                            return currentWatermark;
                        }
                    }
                    if ("update".equals(invocation.getMethod().getName())) {
                        if (sql.contains("SET updated_at = updated_at")) {
                            return ownsRebuild ? 1 : 0;
                        }
                        return 1;
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate projectionStatusJdbc(
            List<SqlCall> calls, UUID knowledgeBaseId, LocalDateTime updatedAt) {
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length == 0
                            || !(invocation.getArgument(0) instanceof String sql)) {
                        return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                    }
                    calls.add(new SqlCall(sql, arguments(invocation.getArguments())));
                    if ("query".equals(invocation.getMethod().getName())
                            && sql.contains("LEFT JOIN ai_knowledge_projection_checkpoint")) {
                        var mapper = (RowMapper<Object>) invocation.getArgument(1);
                        var resultSet = mock(ResultSet.class);
                        when(resultSet.getObject(1, UUID.class)).thenReturn(knowledgeBaseId);
                        when(resultSet.getLong(2)).thenReturn(7L);
                        when(resultSet.getLong(3)).thenReturn(7L);
                        when(resultSet.getLong(4)).thenReturn(7L);
                        when(resultSet.getString(5)).thenReturn("READY");
                        when(resultSet.getString(6)).thenReturn("request-1");
                        when(resultSet.getObject(8, LocalDateTime.class)).thenReturn(updatedAt);
                        return List.of(mapper.mapRow(resultSet, 0));
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate rebuildJdbc(
            List<SqlCall> calls, UUID stableId, AtomicBoolean ownershipAvailable) {
        return mock(
                JdbcTemplate.class,
                invocation -> {
                    if (invocation.getArguments().length == 0
                            || !(invocation.getArgument(0) instanceof String sql)) {
                        return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                    }
                    calls.add(new SqlCall(sql, arguments(invocation.getArguments())));
                    if ("queryForObject".equals(invocation.getMethod().getName())) {
                        var mapper = (RowMapper<Object>) invocation.getArgument(1);
                        var resultSet = mock(ResultSet.class);
                        when(resultSet.getLong(1)).thenReturn(9L);
                        when(resultSet.getObject(2, UUID.class)).thenReturn(stableId);
                        when(resultSet.getLong(3)).thenReturn(7L);
                        return mapper.mapRow(resultSet, 0);
                    }
                    if ("query".equals(invocation.getMethod().getName())
                            && sql.contains("INSERT INTO ai_knowledge_projection_checkpoint")) {
                        if (!ownershipAvailable.compareAndSet(true, false)) {
                            return List.of();
                        }
                        var mapper = (RowMapper<Object>) invocation.getArgument(1);
                        var resultSet = mock(ResultSet.class);
                        when(resultSet.getLong(1)).thenReturn(7L);
                        when(resultSet.getLong(2)).thenReturn(0L);
                        when(resultSet.getString(3)).thenReturn("REBUILDING");
                        when(resultSet.getString(4)).thenReturn("owner");
                        return List.of(mapper.mapRow(resultSet, 0));
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                });
    }

    private List<Object> arguments(Object[] invocationArguments) {
        var result = new ArrayList<Object>();
        for (var index = 1; index < invocationArguments.length; index++) {
            if (invocationArguments[index] instanceof Object[] values) {
                result.addAll(Arrays.asList(values));
            } else {
                result.add(invocationArguments[index]);
            }
        }
        return result;
    }

    private record SqlCall(String sql, List<Object> arguments) {}
}
