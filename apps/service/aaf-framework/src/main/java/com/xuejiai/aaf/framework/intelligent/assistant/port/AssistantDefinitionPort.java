package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** Assistant 定义唯一真理源的读取边界。 */
public interface AssistantDefinitionPort {

    Optional<AssistantDefinition> findByIdAndVersion(
            TenantId tenantId, AssistantId assistantId, AssistantVersion version);
}
