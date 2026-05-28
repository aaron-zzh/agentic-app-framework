/**
 * Agent 管理 Service。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.intelligent.agent;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentManagementService {

    private final AgentDefinitionRepository repository;

    /**
     * 创建 Agent
     *
     * @param dto 创建请求
     * @return Agent 信息
     */
    @Transactional
    public AgentVO create(AgentCreateDTO dto) {
        var entity = new AgentDefinition();
        entity.setAgentId(dto.agentId());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setModelId(dto.modelId());
        entity.setCapabilities(dto.capabilities());
        entity.setTools(dto.tools());
        entity.setMaxIterations(dto.maxIterations() != null ? dto.maxIterations() : 10);
        entity.setTimeoutSeconds(dto.timeoutSeconds() != null ? dto.timeoutSeconds() : 120);
        return toVO(repository.save(entity));
    }

    /**
     * 分页查询 Agent 列表
     *
     * @param status 状态筛选（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    public PageResult<AgentVO> list(String status, Pageable pageable) {
        var page = (status != null)
                ? repository.findByStatus(status, pageable)
                : repository.findAll(pageable);
        return new PageResult<>(page.map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 获取 Agent 详情
     *
     * @param id 编号
     * @return Agent 信息
     */
    public AgentVO getById(Long id) {
        return toVO(getEntity(id));
    }

    /**
     * 更新 Agent
     *
     * @param id 编号
     * @param dto 更新请求
     * @return 更新后的 Agent 信息
     */
    @Transactional
    public AgentVO update(Long id, AgentUpdateDTO dto) {
        var entity = getEntity(id);
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.systemPrompt() != null) entity.setSystemPrompt(dto.systemPrompt());
        if (dto.modelId() != null) entity.setModelId(dto.modelId());
        if (dto.capabilities() != null) entity.setCapabilities(dto.capabilities());
        if (dto.tools() != null) entity.setTools(dto.tools());
        if (dto.maxIterations() != null) entity.setMaxIterations(dto.maxIterations());
        if (dto.timeoutSeconds() != null) entity.setTimeoutSeconds(dto.timeoutSeconds());
        return toVO(repository.save(entity));
    }

    /**
     * 删除 Agent
     *
     * @param id 编号
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * 更新 Agent 状态
     *
     * @param id 编号
     * @param status 新状态
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        var entity = getEntity(id);
        entity.setStatus(status);
        repository.save(entity);
    }

    private AgentDefinition getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Agent 不存在"));
    }

    private AgentVO toVO(AgentDefinition e) {
        return new AgentVO(e.getId(), e.getAgentId(), e.getName(), e.getDescription(),
                e.getSystemPrompt(), e.getModelId(), e.getCapabilities(), e.getTools(),
                e.getMaxIterations(), e.getTimeoutSeconds(), e.getStatus(),
                e.getCreateTime(), e.getUpdateTime());
    }
}
