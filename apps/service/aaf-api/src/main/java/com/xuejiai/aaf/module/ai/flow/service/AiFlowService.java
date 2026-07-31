package com.xuejiai.aaf.module.ai.flow.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
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

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "name",
                    "mode",
                    "status",
                    "agentCallable",
                    "requireConfirm",
                    "publishedAt",
                    "createTime",
                    "updateTime");

    private static final String COMMAND_DEPLOY = "DEPLOY";
    private static final Set<String> DEPLOY_FIELDS =
            Set.of("deploymentId", "status", "publishedAt");

    private final AiFlowDefinitionRepository repository;
    private final BpmnEngine bpmnEngine;
    private final AiFlowBpmnCompiler bpmnCompiler;

    @Override
    protected AiFlowDefinitionRepository getRepository() {
        return repository;
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

    /** 发布：由服务端将已保存的编辑态 JSON 编译成 BPMN，再部署到 Flowable。 */
    @Transactional
    public AiFlowDefinitionVO deploy(Long id) {
        var command = new DeployCommand(id);
        var plan =
                new CustomUpdatePlan<AiFlowDefinition, DeployCommand, String, AiFlowDefinitionVO>(
                        COMMAND_DEPLOY,
                        DEPLOY_FIELDS,
                        (flow, ignored) -> {},
                        (flow, ignored) -> {
                            flow.setStatus("PUBLISHED");
                            flow.setPublishedAt(LocalDateTime.now());
                        },
                        (flow, ignored) -> {
                            var bpmnXml = bpmnCompiler.compile(flow.getId(), flow.getDefinition());
                            var deploymentId = bpmnEngine.deploy(flow.getName(), bpmnXml);
                            flow.setDeploymentId(deploymentId);
                            return deploymentId;
                        },
                        true,
                        (flow, ignored, deploymentId) -> {},
                        (flow, ignored, deploymentId) -> toVO(flow));
        return executeCustomUpdateCommand(id, command, plan);
    }

    private record DeployCommand(Long flowId) {}
}
