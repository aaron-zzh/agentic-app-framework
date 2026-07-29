package com.xuejiai.aaf.module.ai.agent.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionCreateDTO;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionUpdateDTO;
import com.xuejiai.aaf.module.ai.agent.vo.AgentDefinitionVO;

import lombok.RequiredArgsConstructor;

/** 预定义子智能体模板管理服务，不承载 Agent 运行时执行职责。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentDefinitionService {

    private static final String ACTIVE = "active";
    private static final String INACTIVE = "inactive";
    private static final String ARCHIVED = "archived";
    private static final Set<String> STATUSES = Set.of(ACTIVE, INACTIVE, ARCHIVED);
    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "agentId",
                    "name",
                    "modelId",
                    "maxIterations",
                    "timeoutSeconds",
                    "status",
                    "version",
                    "createTime",
                    "updateTime");

    private final AgentDefinitionRepository repository;

    /** 创建预定义模板。 */
    @Transactional
    public AgentDefinitionVO create(AgentDefinitionCreateDTO request) {
        if (repository.findByAgentId(request.agentId()).isPresent()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Agent 模板标识已存在");
        }
        var entity = new AgentDefinition();
        entity.setVersion(1);
        entity.setAgentId(request.agentId());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setSystemPrompt(request.systemPrompt());
        entity.setModelId(request.modelId());
        entity.setCapabilities(toJson(request.capabilities()));
        entity.setTools(toJson(request.tools()));
        entity.setAllowedTools(toJson(request.allowedTools()));
        entity.setMcpServers(toJson(request.mcpServers()));
        entity.setMaxIterations(request.maxIterations() != null ? request.maxIterations() : 10);
        entity.setTimeoutSeconds(request.timeoutSeconds() != null ? request.timeoutSeconds() : 120);
        entity.setStatus(ACTIVE);
        return toVO(repository.save(entity));
    }

    /** 分页查询模板。 */
    public PageResult<AgentDefinitionVO> page(String status, PageParam pageParam) {
        var normalizedStatus = normalizeStatus(status);
        var pageable =
                pageParam.toPageable(Sort.by(Sort.Direction.DESC, "updateTime"), SORTABLE_FIELDS);
        Page<AgentDefinition> page =
                normalizedStatus == null
                        ? repository.findAll(pageable)
                        : repository.findByStatus(normalizedStatus, pageable);
        return new PageResult<>(
                page.map(AgentDefinitionService::toVO).toList(), page.getTotalElements());
    }

    /** 查询模板详情。 */
    public AgentDefinitionVO get(Long id) {
        return toVO(getEntity(id));
    }

    /** 更新模板配置，稳定 agentId 不允许修改。 */
    @Transactional
    public AgentDefinitionVO update(Long id, AgentDefinitionUpdateDTO request) {
        var entity = getEntity(id);
        requireMutable(entity);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setSystemPrompt(request.systemPrompt());
        entity.setModelId(request.modelId());
        entity.setCapabilities(toJson(request.capabilities()));
        entity.setTools(toJson(request.tools()));
        entity.setAllowedTools(toJson(request.allowedTools()));
        entity.setMcpServers(toJson(request.mcpServers()));
        entity.setMaxIterations(request.maxIterations());
        entity.setTimeoutSeconds(request.timeoutSeconds());
        entity.setVersion(Math.incrementExact(currentVersion(entity)));
        return toVO(repository.save(entity));
    }

    /** 启用模板。 */
    @Transactional
    public AgentDefinitionVO enable(Long id) {
        return changeStatus(id, ACTIVE);
    }

    /** 停用模板。 */
    @Transactional
    public AgentDefinitionVO disable(Long id) {
        return changeStatus(id, INACTIVE);
    }

    /** 将模板归档。归档是终态，但重复归档保持幂等。 */
    @Transactional
    public void archive(Long id) {
        var entity = getEntity(id);
        if (!ARCHIVED.equals(entity.getStatus())) {
            entity.setStatus(ARCHIVED);
            repository.save(entity);
        }
    }

    private AgentDefinitionVO changeStatus(Long id, String status) {
        var entity = getEntity(id);
        requireMutable(entity);
        entity.setStatus(status);
        return toVO(repository.save(entity));
    }

    private AgentDefinition getEntity(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Agent 模板不存在"));
    }

    private static void requireMutable(AgentDefinition entity) {
        if (ARCHIVED.equals(entity.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已归档 Agent 模板不可修改");
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        var normalized = status.trim().toLowerCase();
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Agent 模板状态不合法");
        }
        return normalized;
    }

    private static int currentVersion(AgentDefinition entity) {
        return entity.getVersion() != null ? entity.getVersion() : 0;
    }

    private static String toJson(List<String> values) {
        return JsonUtils.toJsonString(values != null ? List.copyOf(values) : List.of());
    }

    private static AgentDefinitionVO toVO(AgentDefinition entity) {
        return new AgentDefinitionVO(
                entity.getId(),
                entity.getAgentId(),
                entity.getVersion(),
                entity.getName(),
                entity.getDescription(),
                entity.getSystemPrompt(),
                entity.getModelId(),
                JsonUtils.parseArray(entity.getCapabilities(), String.class),
                JsonUtils.parseArray(entity.getTools(), String.class),
                JsonUtils.parseArray(entity.getAllowedTools(), String.class),
                JsonUtils.parseArray(entity.getMcpServers(), String.class),
                entity.getMaxIterations(),
                entity.getTimeoutSeconds(),
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
