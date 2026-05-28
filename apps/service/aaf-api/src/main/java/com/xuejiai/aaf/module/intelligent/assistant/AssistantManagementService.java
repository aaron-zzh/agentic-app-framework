/**
 * Assistant 管理 Service。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.intelligent.assistant;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;
import com.xuejiai.aaf.framework.security.ActorContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssistantManagementService {

    private final AssistantDefinitionRepository repository;
    private final ActorContext actorContext;

    /**
     * 创建 Assistant
     *
     * @param dto 创建请求
     * @return Assistant 信息
     */
    @Transactional
    public AssistantVO create(AssistantCreateDTO dto) {
        var userId = actorContext.currentUserId().orElseThrow();
        var entity = new AssistantDefinition();
        entity.setAssistantId(dto.assistantId());
        entity.setUserId(userId);
        entity.setActorId(dto.actorId());
        entity.setRoleId(dto.roleId());
        entity.setMemoryStrategy(dto.memoryStrategy() != null
                ? MemoryStrategy.valueOf(dto.memoryStrategy()) : MemoryStrategy.HYBRID);
        entity.setKnowledgeBaseId(dto.knowledgeBaseId());
        return toVO(repository.save(entity));
    }

    /**
     * 分页查询当前用户的 Assistant 列表
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    public PageResult<AssistantVO> list(Pageable pageable) {
        var userId = actorContext.currentUserId().orElseThrow();
        var page = repository.findByUserId(userId, pageable);
        return new PageResult<>(page.map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 获取 Assistant 详情
     *
     * @param id 编号
     * @return Assistant 信息
     */
    public AssistantVO getById(Long id) {
        return toVO(getEntity(id));
    }

    /**
     * 更新 Assistant
     *
     * @param id 编号
     * @param dto 更新请求
     * @return 更新后的 Assistant 信息
     */
    @Transactional
    public AssistantVO update(Long id, AssistantUpdateDTO dto) {
        var entity = getEntity(id);
        if (dto.actorId() != null) entity.setActorId(dto.actorId());
        if (dto.roleId() != null) entity.setRoleId(dto.roleId());
        if (dto.memoryStrategy() != null) entity.setMemoryStrategy(MemoryStrategy.valueOf(dto.memoryStrategy()));
        if (dto.knowledgeBaseId() != null) entity.setKnowledgeBaseId(dto.knowledgeBaseId());
        return toVO(repository.save(entity));
    }

    /**
     * 删除 Assistant
     *
     * @param id 编号
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private AssistantDefinition getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
    }

    private AssistantVO toVO(AssistantDefinition e) {
        return new AssistantVO(e.getId(), e.getAssistantId(), e.getUserId(), e.getActorId(),
                e.getRoleId(), e.getMemoryStrategy().name(), e.getKnowledgeBaseId(),
                e.getStatus(), e.getCreateTime(), e.getUpdateTime());
    }
}
