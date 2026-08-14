package com.xuejiai.aaf.module.system.task.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.framework.task.AsyncTask;
import com.xuejiai.aaf.framework.task.AsyncTaskRepository;
import com.xuejiai.aaf.module.system.task.vo.AsyncTaskPageParam;
import com.xuejiai.aaf.module.system.task.vo.AsyncTaskVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 异步任务只读 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsyncTaskCrudService
        extends ReadonlyCrudService<AsyncTask, AsyncTaskVO, AsyncTaskPageParam> {

    private final AsyncTaskRepository asyncTaskRepository;

    @Override
    protected AsyncTaskRepository getRepository() {
        return asyncTaskRepository;
    }

    @Override
    protected Specification<AsyncTask> buildSpec(AsyncTaskPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            if (request.getTaskId() != null && !request.getTaskId().isBlank()) {
                predicates.add(cb.like(root.get("taskId"), "%" + request.getTaskId().trim() + "%"));
            }
            if (request.getTaskType() != null && !request.getTaskType().isBlank()) {
                predicates.add(cb.equal(root.get("taskType"), request.getTaskType().trim()));
            }
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    protected AsyncTaskVO toVO(AsyncTask task) {
        return AsyncTaskVO.from(task);
    }

    @Override
    protected List<AsyncTaskVO> toVOList(List<AsyncTask> tasks, String fieldSet) {
        return tasks.stream().map(AsyncTaskVO::from).toList();
    }
}
