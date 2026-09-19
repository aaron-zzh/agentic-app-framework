package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 将已授权 Assistant 的持久记录身份解析为 canonical 运行时身份。 */
public interface AssistantIdentityPort {

    Optional<ResolvedAssistant> resolveExecutable(
            TenantId tenantId, UserId userId, AssistantId assistantId);

    Optional<ResolvedAssistant> resolveExecutable(
            TenantId tenantId, UserId userId, Long assistantRecordId);

    record ResolvedAssistant(Long assistantRecordId, AssistantId assistantId) {
        public ResolvedAssistant {
            if (assistantRecordId == null || assistantRecordId < 1) {
                throw new IllegalArgumentException("assistantRecordId 必须为正数");
            }
        }
    }
}
