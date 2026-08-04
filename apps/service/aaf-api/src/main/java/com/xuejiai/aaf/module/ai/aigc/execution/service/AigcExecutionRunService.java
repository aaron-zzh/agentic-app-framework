package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionRunPageDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionRunVO;

import lombok.RequiredArgsConstructor;

/** ExecutionRun 只读管理根，状态变更只由动作命令执行。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcExecutionRunService
        extends BaseCrudService<
                AigcExecutionRun, AigcExecutionRunVO, Void, Void, AigcExecutionRunPageDTO> {

    private final AigcExecutionRunRepository repository;
    private final AigcExecutionTaskRefRepository taskRefRepository;

    @Override
    protected AigcExecutionRunRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcExecutionRunVO toVO(AigcExecutionRun run) {
        return new AigcExecutionRunVO(
                run.getId(),
                run.getProjectId(),
                run.getObjectId(),
                run.getParentRunId(),
                run.getActionKey(),
                run.getTargetType(),
                run.getTargetRef(),
                run.getStatus(),
                run.getGenerationMode(),
                run.getRoleProfileCode(),
                run.getSelectedModelVersion(),
                run.getOutputPayload(),
                taskIds(run.getId()),
                run.getCostCredits(),
                run.getRetryCount(),
                run.getErrorMessage(),
                run.getStartTime(),
                run.getEndTime(),
                run.getCreateTime());
    }

    public AigcExecutionRunVO toView(AigcExecutionRun run) {
        return toVO(run);
    }

    @Override
    protected AigcExecutionRun toEntity(Void ignored) {
        throw new UnsupportedOperationException("执行记录只能由动作命令创建");
    }

    @Override
    protected void updateEntity(AigcExecutionRun run, Void ignored) {
        throw new UnsupportedOperationException("执行记录状态只能由受控命令变更");
    }

    @Override
    protected Specification<AigcExecutionRun> buildSpec(AigcExecutionRunPageDTO request) {
        return SpecificationBuilder.<AigcExecutionRun>builder()
                .eqIfPresent("projectId", request.getProjectId())
                .eqIfPresent("objectId", request.getObjectId())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    private List<Long> taskIds(Long runId) {
        return taskRefRepository
                .findByExecutionRunIdAndDeletedFalseOrderBySortOrderAsc(runId)
                .stream()
                .map(ref -> ref.getTaskId())
                .toList();
    }
}
