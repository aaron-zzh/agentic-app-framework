package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;

import lombok.RequiredArgsConstructor;

/** 在独立事务中固化动作意图，确保业务事务失败后仍可恢复。 */
@Service
@RequiredArgsConstructor
public class AigcExecutionSubmissionService {

    private static final long INITIAL_RECOVERY_BACKOFF_SECONDS = 30;
    private static final long MAX_RECOVERY_BACKOFF_SECONDS = 1_800;

    private final AigcExecutionSubmissionRepository repository;
    private final JdbcClient jdbcClient;
    private final AigcProjectApi projectApi;
    private final OperatorContext operatorContext;
    private final PlatformTransactionManager transactionManager;

    public SubmissionReceipt record(AigcActionCommand command) {
        projectApi.requireProject(command.projectId());
        var requestPayload = normalize(command);
        var requestHash =
                AigcCanonicalRequest.of(
                                "execution.submit",
                                Map.of(
                                        "projectId", command.projectId(),
                                        "projectObjectId",
                                                command.projectObjectId() == null
                                                        ? 0L
                                                        : command.projectObjectId(),
                                        "actionKey", command.actionKey(),
                                        "prompt", command.prompt() == null ? "" : command.prompt(),
                                        "requestedModelId",
                                                command.requestedModelId() == null
                                                        ? ""
                                                        : command.requestedModelId(),
                                        "actionArguments", command.actionArguments(),
                                        "attachmentMediaVersionIds",
                                                command.attachmentMediaVersionIds(),
                                        "selectedProjectObjectIds",
                                                requestPayload.get("selectedProjectObjectIds")),
                                Map.of(
                                        "expectedGraphRevision",
                                        command.expectedGraphRevision() == null
                                                ? -1L
                                                : command.expectedGraphRevision(),
                                        "confirmed",
                                        command.confirmed()))
                        .sha256();
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        var row =
                requiresNew()
                        .execute(
                                ignored ->
                                        jdbcClient
                                                .sql(
                                                        """
                                                        INSERT INTO aigc_execution_submission (
                                                            version, org_id, workspace_id, project_id,
                                                            idempotency_key, request_hash,
                                                            request_payload, status, owner_id,
                                                            create_time, update_time, deleted
                                                        ) VALUES (
                                                            0, :orgId, :workspaceId, :projectId,
                                                            :idempotencyKey, :requestHash,
                                                            CAST(:requestPayload AS jsonb),
                                                            'INTENT_RECORDED', :ownerId,
                                                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                                                            false
                                                        )
                                                        ON CONFLICT (project_id, idempotency_key)
                                                            WHERE deleted = false
                                                        DO UPDATE SET
                                                            idempotency_key = EXCLUDED.idempotency_key
                                                        RETURNING id, request_hash,
                                                                  (xmax = 0) AS created
                                                        """)
                                                .param("orgId", OrgContext.getCurrentOrgId())
                                                .param(
                                                        "workspaceId",
                                                        OrgContext.getCurrentWorkspaceId())
                                                .param("projectId", command.projectId())
                                                .param("idempotencyKey", command.idempotencyKey())
                                                .param("requestHash", requestHash)
                                                .param(
                                                        "requestPayload",
                                                        JsonUtils.toJsonString(requestPayload))
                                                .param("ownerId", ownerId)
                                                .query(
                                                        (resultSet, rowNum) ->
                                                                new RecordedRow(
                                                                        resultSet.getLong("id"),
                                                                        resultSet.getString(
                                                                                "request_hash"),
                                                                        resultSet.getBoolean(
                                                                                "created")))
                                                .single());
        if (row == null || !requestHash.equals(row.requestHash())) {
            throw new BusinessException(409, "动作幂等键已用于不同请求");
        }
        var submission =
                requiresNew()
                        .execute(
                                ignored ->
                                        repository
                                                .findById(row.id())
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalStateException(
                                                                        "submission 原子写入后不可见")));
        requireSameRequest(submission, requestHash);
        return new SubmissionReceipt(submission, row.created());
    }

    public record SubmissionReceipt(AigcExecutionSubmission submission, boolean created) {
        public boolean replayed() {
            return !created;
        }
    }

    @Transactional
    public void recordRetryableRecoveryFailure(Long submissionId, String errorMessage) {
        var submission =
                repository
                        .findLockedById(submissionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 submission 不存在"));
        if ("TERMINAL".equals(submission.getStatus())
                || "RECOVERY_FAILED".equals(submission.getStatus())) {
            return;
        }
        var previous = recoveryRetry(submission.getRemark());
        var attempt = previous == null ? 1 : previous.attempt() + 1;
        var backoffSeconds = recoveryBackoffSeconds(attempt);
        var retryAfter = LocalDateTime.now().plusSeconds(backoffSeconds);
        submission.setLastError(truncateError(errorMessage));
        submission.setRemark(
                JsonUtils.toJsonString(new RecoveryRetry(attempt, backoffSeconds, retryAfter)));
        repository.save(submission);
    }

    @Transactional
    public void clearRecoveryFailure(Long submissionId) {
        repository
                .findLockedById(submissionId)
                .ifPresent(
                        submission -> {
                            if (submission.getLastError() == null
                                    && recoveryRetry(submission.getRemark()) == null) {
                                return;
                            }
                            submission.setLastError(null);
                            submission.setRemark(null);
                            repository.save(submission);
                        });
    }

    public boolean isRecoveryDue(AigcExecutionSubmission submission, LocalDateTime now) {
        var retry = recoveryRetry(submission.getRemark());
        return retry == null || !retry.retryAfter().isAfter(now);
    }

    private long recoveryBackoffSeconds(int attempt) {
        var exponent = Math.min(Math.max(0, attempt - 1), 10);
        return Math.min(INITIAL_RECOVERY_BACKOFF_SECONDS << exponent, MAX_RECOVERY_BACKOFF_SECONDS);
    }

    private RecoveryRetry recoveryRetry(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parseObject(value, RecoveryRetry.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String truncateError(String value) {
        var message = value == null || value.isBlank() ? "execution submission 恢复失败" : value;
        return message.substring(0, Math.min(message.length(), 1_000));
    }

    private record RecoveryRetry(int attempt, long backoffSeconds, LocalDateTime retryAfter) {}

    private record RecordedRow(Long id, String requestHash, boolean created) {}

    private AigcExecutionSubmission requireSameRequest(
            AigcExecutionSubmission submission, String requestHash) {
        var ownerId = operatorContext.currentOwnerId().orElseThrow();
        if (!Objects.equals(submission.getOwnerId(), ownerId)
                || !Objects.equals(submission.getOrgId(), OrgContext.getCurrentOrgId())
                || !Objects.equals(
                        submission.getWorkspaceId(), OrgContext.getCurrentWorkspaceId())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "动作 submission 不存在");
        }
        if (!requestHash.equals(submission.getRequestHash())) {
            throw new BusinessException(409, "动作幂等键已用于不同请求");
        }
        return submission;
    }

    private TransactionTemplate requiresNew() {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    public AigcActionCommand toCommand(AigcExecutionSubmission submission) {
        var payload = submission.getRequestPayload();
        return new AigcActionCommand(
                longValue(payload.get("projectId")),
                longValue(payload.get("projectObjectId")),
                String.valueOf(payload.get("actionKey")),
                text(payload.get("prompt")),
                text(payload.get("requestedModelId")),
                objectMap(payload.get("actionArguments")),
                longList(payload.get("attachmentMediaVersionIds")),
                longList(payload.get("selectedProjectObjectIds")),
                longValue(payload.get("expectedGraphRevision")),
                Boolean.TRUE.equals(payload.get("confirmed")),
                submission.getIdempotencyKey());
    }

    Map<String, Object> normalize(AigcActionCommand command) {
        var result = new LinkedHashMap<String, Object>();
        result.put("projectId", command.projectId());
        result.put("projectObjectId", command.projectObjectId());
        result.put("actionKey", command.actionKey());
        result.put("prompt", command.prompt());
        result.put("requestedModelId", command.requestedModelId());
        result.put(
                "actionArguments",
                AigcCanonicalRequest.of("execution.payload", command.actionArguments(), Map.of())
                        .business());
        result.put("attachmentMediaVersionIds", List.copyOf(command.attachmentMediaVersionIds()));
        result.put(
                "selectedProjectObjectIds",
                command.selectedProjectObjectIds().stream().distinct().sorted().toList());
        result.put("expectedGraphRevision", command.expectedGraphRevision());
        result.put("confirmed", command.confirmed());
        return java.util.Collections.unmodifiableMap(result);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return java.util.Collections.unmodifiableMap(result);
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .toList();
    }
}
