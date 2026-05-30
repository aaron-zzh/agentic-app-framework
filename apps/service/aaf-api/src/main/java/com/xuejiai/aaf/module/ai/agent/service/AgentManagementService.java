package com.xuejiai.aaf.module.ai.agent.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.agent.AgentFactory;
import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionRun;
import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionRunRepository;
import com.xuejiai.aaf.module.ai.agent.vo.*;

import lombok.RequiredArgsConstructor;

/**
 * Agent 管理服务——委托 framework 层 AgentRegistryService/AgentFactory。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class AgentManagementService {

    private final AgentRegistryService registryService;
    private final AgentDefinitionRepository agentRepo;
    private final AgentFactory agentFactory;
    private final ExecutionRunRepository executionRunRepo;

    /**
     * 创建 Agent。
     *
     * @param dto 创建请求
     * @return Agent 信息
     */
    @Transactional
    public AgentVO create(AgentCreateDTO dto) {
        var definition = new AgentDefinition();
        definition.setAgentId(dto.agentId());
        definition.setName(dto.name());
        definition.setDescription(dto.description());
        definition.setSystemPrompt(dto.systemPrompt());
        definition.setModelId(dto.modelId());
        definition.setCapabilities(dto.capabilities());
        definition.setTools(dto.tools());
        definition.setAllowedTools(dto.allowedTools());
        definition.setMcpServers(dto.mcpServers());
        if (dto.maxIterations() != null) definition.setMaxIterations(dto.maxIterations());
        if (dto.timeoutSeconds() != null) definition.setTimeoutSeconds(dto.timeoutSeconds());
        var saved = registryService.register(definition);
        return toVO(saved);
    }

    /**
     * 分页查询 Agent 列表。
     *
     * @param status 状态过滤（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<AgentVO> list(String status, Pageable pageable) {
        Page<AgentDefinition> page;
        if (status != null && !status.isBlank()) {
            page = agentRepo.findByStatus(status, pageable);
        } else {
            page = agentRepo.findAll(pageable);
        }
        return new PageResult<>(page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 获取 Agent 详情。
     *
     * @param id 数据库 ID
     * @return Agent 信息
     */
    @Transactional(readOnly = true)
    public AgentVO getById(Long id) {
        var entity = agentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Agent 不存在"));
        return toVO(entity);
    }

    /**
     * 更新 Agent。
     *
     * @param id 数据库 ID
     * @param dto 更新请求
     * @return 更新后的 Agent 信息
     */
    @Transactional
    public AgentVO update(Long id, AgentUpdateDTO dto) {
        var entity = agentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Agent 不存在"));
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.systemPrompt() != null) entity.setSystemPrompt(dto.systemPrompt());
        if (dto.modelId() != null) entity.setModelId(dto.modelId());
        if (dto.capabilities() != null) entity.setCapabilities(dto.capabilities());
        if (dto.tools() != null) entity.setTools(dto.tools());
        if (dto.allowedTools() != null) entity.setAllowedTools(dto.allowedTools());
        if (dto.mcpServers() != null) entity.setMcpServers(dto.mcpServers());
        if (dto.maxIterations() != null) entity.setMaxIterations(dto.maxIterations());
        if (dto.timeoutSeconds() != null) entity.setTimeoutSeconds(dto.timeoutSeconds());
        var saved = agentRepo.save(entity);
        return toVO(saved);
    }

    /**
     * 删除 Agent（归档）。
     *
     * @param id 数据库 ID
     */
    @Transactional
    public void delete(Long id) {
        var entity = agentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Agent 不存在"));
        registryService.archive(entity.getAgentId());
    }

    /**
     * 更新 Agent 状态（启用/禁用）。
     *
     * @param id 数据库 ID
     * @param status 新状态
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        var entity = agentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Agent 不存在"));
        entity.setStatus(status);
        agentRepo.save(entity);
    }

    /**
     * 启动 Agent 执行。
     *
     * @param id 数据库 ID
     * @param input 用户输入
     * @return 执行结果
     */
    public String execute(Long id, String input) {
        var entity = agentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Agent 不存在"));
        var executor = agentFactory.create(entity);
        // TODO: 完善执行上下文（userId、sessionId 等）
        var result = executor.execute(input);
        return result.output();
    }

    /**
     * 停止 Agent 执行。
     *
     * @param executionId 执行 ID
     */
    public void stop(String executionId) {
        // TODO: 委托 framework 层 AgentRuntime 停止执行
        throw new UnsupportedOperationException("停止执行功能待实现");
    }

    /**
     * 查询 Agent 执行状态。
     *
     * @param executionId 执行 ID
     * @return 执行记录
     */
    @Transactional(readOnly = true)
    public AgentExecutionVO getExecutionStatus(String executionId) {
        var run = executionRunRepo.findByExecutionId(executionId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "执行记录不存在"));
        return toExecutionVO(run);
    }

    /**
     * 分页查询 Agent 执行历史。
     *
     * @param agentId Agent 唯一标识
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<AgentExecutionVO> listExecutions(String agentId, Pageable pageable) {
        var page = executionRunRepo.findByAgentId(agentId, pageable);
        return new PageResult<>(page.getContent().stream().map(this::toExecutionVO).toList(), page.getTotalElements());
    }

    private AgentVO toVO(AgentDefinition e) {
        return new AgentVO(
                e.getId(), e.getAgentId(), e.getName(), e.getDescription(),
                e.getSystemPrompt(), e.getModelId(), e.getCapabilities(),
                e.getTools(), e.getAllowedTools(), e.getMcpServers(),
                e.getMaxIterations(), e.getTimeoutSeconds(), e.getStatus(),
                e.getCreateTime(), e.getUpdateTime());
    }

    private AgentExecutionVO toExecutionVO(ExecutionRun r) {
        return new AgentExecutionVO(
                r.getId(), r.getExecutionId(), r.getAgentId(), r.getAgentName(),
                r.getUserId(), r.getConversationId(), r.getInput(), r.getOutput(),
                r.getStatus() != null ? r.getStatus().name() : null, r.getErrorMessage(),
                r.getTokenInput(), r.getTokenOutput(), r.getStartedAt(),
                r.getFinishedAt(), r.getDurationMs());
    }
}
