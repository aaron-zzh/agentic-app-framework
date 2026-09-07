package com.xuejiai.aaf.module.ai.assistant.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.ClarificationRequestRepository;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Clarification 恢复应用服务（AAF-114 #11408 第二版）—— 供 {@code AssistantAguiController#resumeRun} 按 {@code
 * interruptId} 归属分派调用， 遵循 {@code controller → service → repository} 分层，controller 不直接依赖 {@code
 * ClarificationRequestRepository}（架构约束：controller 禁止直接访问 repository）。
 */
@Service
@RequiredArgsConstructor
public class ClarificationResumeService {

    private final ClarificationRequestRepository clarifications;
    private final DelegatedTaskCoordinator delegatedTasks;

    /** 按 requestId 精确查询，附加租户过滤；不加锁，不驱动任何状态转换。 */
    public Optional<ClarificationRequest> findByRequestId(TenantId tenantId, String requestId) {
        return clarifications
                .findByRequestId(tenantId.value(), requestId)
                .map(entity -> entity.getRequest());
    }

    /**
     * 提交澄清补充参数，驱动 {@code ClarificationRequest} 状态机前进。
     *
     * <p>{@code payload} 已由前端按 {@code responseSchema} 编码为字符串值的对象；这里只做浅层类型收窄， 真正的字段校验（是否覆盖
     * requiredFields、值是否在 enum 内）由 {@code ExecutionInput}/{@code ClarificationRequest.apply}
     * 既有校验链承担，不在本服务重复实现。
     */
    public Mono<DelegatedTask> submit(
            TenantId tenantId, UserId userId, ClarificationRequest clarification, Object payload) {
        var values = toStringValues(payload);
        var input =
                new ExecutionInput(
                        UUID.randomUUID().toString(),
                        tenantId,
                        userId,
                        clarification.taskId(),
                        ExecutionInput.Kind.SUPPLEMENT,
                        null,
                        values,
                        Instant.now());
        return delegatedTasks.acceptInput(input);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> toStringValues(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("resume payload 必须是字段对象");
        }
        var values = (Map<String, Object>) map;
        var result = new java.util.LinkedHashMap<String, String>();
        values.forEach(
                (key, value) -> {
                    if (value != null) {
                        result.put(key, String.valueOf(value));
                    }
                });
        return result;
    }
}
