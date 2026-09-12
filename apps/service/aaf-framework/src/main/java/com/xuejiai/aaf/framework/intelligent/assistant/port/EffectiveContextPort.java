package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceType;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 选择本次实际生效上下文、持久引用清单并管理用户来源偏好。 */
public interface EffectiveContextPort {

    EffectiveContextManifest resolve(
            TenantId tenantId,
            UserId userId,
            ExecutionProfileSnapshot profile,
            Task task,
            List<SourceReference> candidates,
            Instant at);

    SourcePreference configure(SourcePreference preference);

    List<SourcePreference> listPreferences(
            TenantId tenantId, UserId userId, AssistantId assistantId);

    record SourcePreference(
            TenantId tenantId,
            UserId userId,
            AssistantId assistantId,
            SourceType sourceType,
            String sourceKey,
            Disposition disposition,
            String reason,
            Instant updatedAt) {
        public SourcePreference {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(userId, "userId 不能为空");
            Objects.requireNonNull(assistantId, "assistantId 不能为空");
            Objects.requireNonNull(sourceType, "sourceType 不能为空");
            if (sourceKey == null || sourceKey.isBlank())
                throw new IllegalArgumentException("sourceKey 不能为空白");
            Objects.requireNonNull(disposition, "disposition 不能为空");
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("reason 不能为空白");
            Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        }
    }

    enum Disposition {
        DEFAULT,
        PREFERRED,
        DISABLED,
        REMOVED
    }
}
