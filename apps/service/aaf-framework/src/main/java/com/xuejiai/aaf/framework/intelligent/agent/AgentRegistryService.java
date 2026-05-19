/**
 * Agent 注册表服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Agent 定义与注册：元数据管理、能力声明、生命周期。
 * 运行时 Agent 实例由 AgentFactory 根据定义创建。
 */
@Service
@RequiredArgsConstructor
public class AgentRegistryService {

    private final AgentDefinitionRepository repository;

    /** 注册 Agent */
    @Transactional
    public AgentDefinition register(AgentDefinition definition) {
        definition.setStatus("active");
        return repository.save(definition);
    }

    /** 按 ID 查找 */
    public Optional<AgentDefinition> findByAgentId(String agentId) {
        return repository.findByAgentId(agentId);
    }

    /** 获取所有活跃 Agent */
    public List<AgentDefinition> listActive() {
        return repository.findByStatus("active");
    }

    /** 按能力查找 Agent */
    public List<AgentDefinition> findByCapability(String capability) {
        return repository.findByStatusAndCapabilitiesContaining("active", capability);
    }

    /** 停用 Agent */
    @Transactional
    public void deactivate(String agentId) {
        repository.findByAgentId(agentId).ifPresent(d -> {
            d.setStatus("inactive");
            repository.save(d);
        });
    }

    /** 归档 Agent */
    @Transactional
    public void archive(String agentId) {
        repository.findByAgentId(agentId).ifPresent(d -> {
            d.setStatus("archived");
            repository.save(d);
        });
    }
}
