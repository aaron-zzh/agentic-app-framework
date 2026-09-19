package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantIdentityPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/**
 * ai_assistant 持久记录与 canonical AssistantId 的受控解析边界。
 *
 * <p>只接受调用者自己拥有的 Assistant 行：与 {@code AssistantExecutionService.resolveAssistant} 的显式选择规则一致， 裸
 * SYSTEM_MANAGED 模板（{@code user_id=0}）不可被显式指定，否则会绑定出永远不可执行的目标。
 */
public final class JpaAssistantIdentityAdapter implements AssistantIdentityPort {

    private static final String ACTIVE = "active";

    private final AssistantRepository assistants;

    public JpaAssistantIdentityAdapter(AssistantRepository assistants) {
        this.assistants = Objects.requireNonNull(assistants, "assistants 不能为空");
    }

    @Override
    public Optional<ResolvedAssistant> resolveExecutable(
            TenantId tenantId, UserId userId, AssistantId assistantId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        var ownerId = parseNumericUserId(userId);
        if (ownerId == null) {
            return Optional.empty();
        }
        return assistants
                .findByCodeAndStatus(assistantId.value(), ACTIVE)
                .filter(assistant -> ownerId.equals(assistant.getUserId()))
                .map(
                        assistant ->
                                new ResolvedAssistant(
                                        assistant.getId(), new AssistantId(assistant.getCode())));
    }

    @Override
    public Optional<ResolvedAssistant> resolveExecutable(
            TenantId tenantId, UserId userId, Long assistantRecordId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        if (assistantRecordId == null || assistantRecordId < 1) {
            return Optional.empty();
        }
        var ownerId = parseNumericUserId(userId);
        if (ownerId == null) {
            return Optional.empty();
        }
        return assistants
                .findById(assistantRecordId)
                .filter(assistant -> ACTIVE.equals(assistant.getStatus()))
                .filter(assistant -> ownerId.equals(assistant.getUserId()))
                .map(
                        assistant ->
                                new ResolvedAssistant(
                                        assistant.getId(), new AssistantId(assistant.getCode())));
    }

    private Long parseNumericUserId(UserId userId) {
        try {
            return Long.valueOf(userId.value());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
