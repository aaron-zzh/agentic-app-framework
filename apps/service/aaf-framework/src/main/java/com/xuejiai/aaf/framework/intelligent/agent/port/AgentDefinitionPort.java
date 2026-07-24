package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentSpec;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;

/** Agent 定义唯一真理源的读取端口。 */
public interface AgentDefinitionPort {

    /** 按稳定标识和精确版本读取定义。 */
    Optional<AgentSpec> findByIdAndVersion(AgentId agentId, long version);
}
