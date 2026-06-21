package com.xuejiai.aaf.module.ai.flow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.module.ai.flow.domain.AiFlowDefinition;
import com.xuejiai.aaf.module.ai.flow.repository.AiFlowDefinitionRepository;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionCreateDTO;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionPageDTO;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionUpdateDTO;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiFlowService
        extends BaseCrudService<
                AiFlowDefinition,
                AiFlowDefinitionVO,
                AiFlowDefinitionCreateDTO,
                AiFlowDefinitionUpdateDTO,
                AiFlowDefinitionPageDTO> {

    private final AiFlowDefinitionRepository repository;
    private final WorkflowEngine workflowEngine;

    @Override
    protected JpaRepository<AiFlowDefinition, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<AiFlowDefinition> getSpecExecutor() {
        return repository;
    }

    @Override
    protected String entityName() {
        return "AI 工作流";
    }

    @Override
    protected String entitlementCode() {
        return "workflow_count";
    }

    @Override
    protected AiFlowDefinitionVO toVO(AiFlowDefinition e) {
        var vo = new AiFlowDefinitionVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setMode(e.getMode());
        vo.setDefinition(e.getDefinition());
        vo.setStatus(e.getStatus());
        vo.setDeploymentId(e.getDeploymentId());
        vo.setPublishedAt(e.getPublishedAt());
        vo.setAgentCallable(e.getAgentCallable());
        vo.setRequireConfirm(e.getRequireConfirm());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    @Override
    protected AiFlowDefinition toEntity(AiFlowDefinitionCreateDTO dto) {
        var e = new AiFlowDefinition();
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setMode(dto.getMode());
        if (dto.getDefinition() != null) e.setDefinition(dto.getDefinition());
        e.setAgentCallable(dto.getAgentCallable());
        e.setRequireConfirm(dto.getRequireConfirm());
        return e;
    }

    @Override
    protected void updateEntity(AiFlowDefinition e, AiFlowDefinitionUpdateDTO dto) {
        if (dto.getName() != null) e.setName(dto.getName());
        if (dto.getDescription() != null) e.setDescription(dto.getDescription());
        if (dto.getMode() != null) e.setMode(dto.getMode());
        if (dto.getDefinition() != null) e.setDefinition(dto.getDefinition());
        if (dto.getAgentCallable() != null) e.setAgentCallable(dto.getAgentCallable());
        if (dto.getRequireConfirm() != null) e.setRequireConfirm(dto.getRequireConfirm());
    }

    @Override
    protected Specification<AiFlowDefinition> buildSpec(AiFlowDefinitionPageDTO dto) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (dto.getName() != null && !dto.getName().isBlank())
                predicates.add(cb.like(root.get("name"), "%" + dto.getName() + "%"));
            if (dto.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), dto.getStatus()));
            if (dto.getAgentCallable() != null)
                predicates.add(cb.equal(root.get("agentCallable"), dto.getAgentCallable()));
            return predicates.isEmpty()
                    ? null
                    : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** 发布：将编辑态 JSON 转 BPMN 部署到 Flowable，更新状态为 PUBLISHED。 */
    @Transactional
    public AiFlowDefinitionVO deploy(Long id, String bpmnXml) {
        var entity = requireEntity(id);
        var deploymentId = workflowEngine.deploy(entity.getName(), bpmnXml);
        entity.setDeploymentId(deploymentId);
        entity.setStatus("PUBLISHED");
        entity.setPublishedAt(LocalDateTime.now());
        repository.save(entity);
        return toVO(entity);
    }

    /** 查询智能体可调用的已发布工作流列表（供 WorkflowTool.listWorkflows 使用）。 */
    public List<AiFlowDefinitionVO> listAgentCallable() {
        return repository
                .findAll(
                        (root, query, cb) ->
                                cb.and(
                                        cb.equal(root.get("status"), "PUBLISHED"),
                                        cb.equal(root.get("agentCallable"), true),
                                        cb.equal(root.get("deleted"), false)))
                .stream()
                .map(this::toVO)
                .toList();
    }
}
