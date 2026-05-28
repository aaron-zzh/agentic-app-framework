package com.xuejiai.aaf.module.intelligent.assistant.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.SkillMatchService;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryStrategy;
import com.xuejiai.aaf.module.intelligent.assistant.vo.*;

import lombok.RequiredArgsConstructor;

/**
 * Assistant 管理服务——委托 framework 层。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class AssistantManagementService {

    private final AssistantDefinitionRepository assistantRepo;
    private final SkillMatchService skillMatchService;

    /**
     * 创建 Assistant。
     *
     * @param dto 创建请求
     * @return Assistant 信息
     */
    @Transactional
    public AssistantVO create(AssistantCreateDTO dto) {
        var entity = new AssistantDefinition();
        entity.setAssistantId(dto.assistantId());
        entity.setUserId(dto.userId());
        entity.setActorId(dto.actorId());
        entity.setRoleId(dto.roleId());
        if (dto.memoryStrategy() != null) {
            entity.setMemoryStrategy(MemoryStrategy.valueOf(dto.memoryStrategy()));
        }
        entity.setKnowledgeBaseId(dto.knowledgeBaseId());
        var saved = assistantRepo.save(entity);
        return toVO(saved);
    }

    /**
     * 分页查询 Assistant 列表。
     *
     * @param userId 用户 ID 过滤（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<AssistantVO> list(Long userId, Pageable pageable) {
        Page<AssistantDefinition> page;
        if (userId != null) {
            page = assistantRepo.findByUserId(userId, pageable);
        } else {
            page = assistantRepo.findAll(pageable);
        }
        return new PageResult<>(page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 获取 Assistant 详情。
     *
     * @param id 数据库 ID
     * @return Assistant 信息
     */
    @Transactional(readOnly = true)
    public AssistantVO getById(Long id) {
        var entity = assistantRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        return toVO(entity);
    }

    /**
     * 更新 Assistant。
     *
     * @param id 数据库 ID
     * @param dto 更新请求
     * @return 更新后的 Assistant 信息
     */
    @Transactional
    public AssistantVO update(Long id, AssistantUpdateDTO dto) {
        var entity = assistantRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        if (dto.actorId() != null) entity.setActorId(dto.actorId());
        if (dto.roleId() != null) entity.setRoleId(dto.roleId());
        if (dto.memoryStrategy() != null) {
            entity.setMemoryStrategy(MemoryStrategy.valueOf(dto.memoryStrategy()));
        }
        if (dto.knowledgeBaseId() != null) entity.setKnowledgeBaseId(dto.knowledgeBaseId());
        var saved = assistantRepo.save(entity);
        return toVO(saved);
    }

    /**
     * 删除 Assistant（逻辑删除）。
     *
     * @param id 数据库 ID
     */
    @Transactional
    public void delete(Long id) {
        var entity = assistantRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        entity.setStatus("inactive");
        assistantRepo.save(entity);
    }

    /**
     * 绑定技能到 Assistant。
     *
     * @param id 数据库 ID
     * @param skillIds 技能 ID 列表
     */
    @Transactional
    public void bindSkills(Long id, List<String> skillIds) {
        // TODO: 委托 framework 层 RoleStore 更新 Role 的 skillIds
        // 当前 Role 的 skillIds 管理在 RoleStore 中，需通过 roleId 更新
        var entity = assistantRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        // TODO: roleStore.updateSkillIds(entity.getRoleId(), skillIds);
        throw new UnsupportedOperationException("绑定技能功能待 RoleStore 接口完善后实现");
    }

    /**
     * 配置工具白名单。
     *
     * @param id 数据库 ID
     * @param toolWhitelist 工具白名单列表
     */
    @Transactional
    public void configureToolWhitelist(Long id, List<String> toolWhitelist) {
        // TODO: 委托 framework 层配置 Assistant 关联 Agent 的 allowedTools
        var entity = assistantRepo.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Assistant 不存在"));
        // TODO: 通过 Role → Agent 链路更新 allowedTools
        throw new UnsupportedOperationException("配置工具白名单功能待完善");
    }

    private AssistantVO toVO(AssistantDefinition e) {
        return new AssistantVO(
                e.getId(), e.getAssistantId(), e.getUserId(),
                e.getActorId(), e.getRoleId(),
                e.getMemoryStrategy() != null ? e.getMemoryStrategy().name() : null,
                e.getKnowledgeBaseId(), e.getStatus(),
                e.getCreateTime(), e.getUpdateTime());
    }
}
