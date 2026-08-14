package com.xuejiai.aaf.module.system.task.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.framework.task.TaskExecution;
import com.xuejiai.aaf.framework.task.TaskExecutionRepository;
import com.xuejiai.aaf.module.system.task.vo.TaskExecutionPageParam;
import com.xuejiai.aaf.module.system.task.vo.TaskExecutionVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 任务执行审计只读 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskExecutionCrudService
        extends ReadonlyCrudService<TaskExecution, TaskExecutionVO, TaskExecutionPageParam> {

    private final TaskExecutionRepository taskExecutionRepository;

    @Override
    protected TaskExecutionRepository getRepository() {
        return taskExecutionRepository;
    }

    @Override
    protected Specification<TaskExecution> buildSpec(TaskExecutionPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            if (request.getTaskName() != null && !request.getTaskName().isBlank()) {
                predicates.add(
                        cb.like(root.get("taskName"), "%" + request.getTaskName().trim() + "%"));
            }
            if (request.getTaskType() != null && !request.getTaskType().isBlank()) {
                predicates.add(cb.equal(root.get("taskType"), request.getTaskType().trim()));
            }
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), request.getStatus().trim()));
            }
            if (request.getBizId() != null && !request.getBizId().isBlank()) {
                predicates.add(cb.like(root.get("bizId"), "%" + request.getBizId().trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    protected TaskExecutionVO toVO(TaskExecution execution) {
        return TaskExecutionVO.from(execution);
    }

    @Override
    protected List<TaskExecutionVO> toVOList(List<TaskExecution> executions, String fieldSet) {
        return executions.stream().map(TaskExecutionVO::from).toList();
    }
}
