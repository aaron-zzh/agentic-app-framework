package com.xuejiai.aaf.framework.intelligent.agent;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.agent.AgentExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Agent 工厂 stub——v1 实现已归档，待对接新 agentscope 路径。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    public AgentExecutor create(AgentDefinition definition) {
        throw new UnsupportedOperationException("AgentFactory 待重新实现（v1 已归档）");
    }
}
