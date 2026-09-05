package com.xuejiai.aaf.module.ai.aigc.task.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;

import lombok.RequiredArgsConstructor;

/** execution Task intent 的原子创建/重放命令。冲突路径不会使当前事务进入失败态。 */
@Component
@RequiredArgsConstructor
public class AigcTaskIntentStore {

    private final JdbcClient jdbcClient;
    private final AigcTaskRepository repository;

    @Transactional
    public PrepareResult prepare(AigcTask intent) {
        var row =
                jdbcClient
                        .sql(
                                """
                                INSERT INTO aigc_task (
                                    version, org_id, workspace_id, user_id, project_id,
                                    execution_run_id, project_object_id, idempotency_key,
                                    request_hash, type, status, provider_key, provider, model,
                                    model_name, prompt, params, owner_id, create_time, update_time,
                                    deleted
                                ) VALUES (
                                    0, :orgId, :workspaceId, :userId, :projectId,
                                    :executionRunId, :projectObjectId, :idempotencyKey,
                                    :requestHash, :type, 'PREPARED', :providerKey, :provider,
                                    :model, :modelName, :prompt, CAST(:params AS jsonb), :ownerId,
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
                                )
                                ON CONFLICT (execution_run_id, idempotency_key)
                                    WHERE execution_run_id IS NOT NULL
                                      AND idempotency_key IS NOT NULL
                                      AND deleted = false
                                DO UPDATE SET idempotency_key = EXCLUDED.idempotency_key
                                RETURNING id, request_hash, (xmax = 0) AS created
                                """)
                        .param("orgId", intent.getOrgId())
                        .param("workspaceId", intent.getWorkspaceId())
                        .param("userId", intent.getUserId())
                        .param("projectId", intent.getProjectId())
                        .param("executionRunId", intent.getExecutionRunId())
                        .param("projectObjectId", intent.getProjectObjectId())
                        .param("idempotencyKey", intent.getIdempotencyKey())
                        .param("requestHash", intent.getRequestHash())
                        .param("type", intent.getType())
                        .param("providerKey", intent.getProviderKey())
                        .param("provider", intent.getProvider())
                        .param("model", intent.getModel())
                        .param("modelName", intent.getModelName())
                        .param("prompt", intent.getPrompt())
                        .param("params", intent.getParams())
                        .param("ownerId", intent.getOwnerId())
                        .query(
                                (resultSet, rowNum) ->
                                        new PreparedRow(
                                                resultSet.getLong("id"),
                                                resultSet.getString("request_hash"),
                                                resultSet.getBoolean("created")))
                        .single();
        if (!intent.getRequestHash().equals(row.requestHash())) {
            throw new BusinessException(409, "AIGC 子任务幂等键已用于不同请求");
        }
        var task =
                repository
                        .findById(row.id())
                        .orElseThrow(() -> new IllegalStateException("Task intent 原子写入后不可见"));
        return new PrepareResult(task, row.created());
    }

    public record PrepareResult(AigcTask task, boolean created) {
        public boolean replayed() {
            return !created;
        }
    }

    private record PreparedRow(Long id, String requestHash, boolean created) {}
}
