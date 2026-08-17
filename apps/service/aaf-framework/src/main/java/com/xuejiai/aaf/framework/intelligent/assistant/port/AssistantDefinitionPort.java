package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** ai_assistant 当前定义的唯一读取边界；不提供运行时版本选择。 */
public interface AssistantDefinitionPort {

    Optional<AssistantDefinition> findById(TenantId tenantId, AssistantId assistantId);

    Optional<AssistantDefinition> findDefaultForUser(TenantId tenantId, UserId userId);
}
