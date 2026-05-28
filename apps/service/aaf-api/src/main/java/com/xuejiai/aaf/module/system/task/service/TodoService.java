package com.xuejiai.aaf.module.system.task.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.repository.TodoRepository;
import com.xuejiai.aaf.module.system.task.vo.TodoPageDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

import lombok.RequiredArgsConstructor;

/**
 * 待办业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    /** 分页查询当前用户待办 */
    public PageResult<TodoVO> page(Long userId, TodoPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<Todo> spec =
                SpecificationBuilder.<Todo>builder()
                        .eqIfPresent("assigneeId", userId)
                        .eqIfPresent("status", req.getStatus())
                        .build();
        var page = todoRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 更新待办状态 */
    @Transactional
    public void updateStatus(Long userId, Long id, String status) {
        var todo =
                todoRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "待办不存在"));
        if (!todo.getAssigneeId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权操作此待办");
        }
        todo.setStatus(status);
        todoRepository.save(todo);
    }

    /** 创建待办（供内部调用） */
    @Transactional
    public Todo create(
            Long assigneeId, String title, String sourceType, String sourceEntity, Long sourceId) {
        var todo = new Todo();
        todo.setAssigneeId(assigneeId);
        todo.setTitle(title);
        todo.setSourceType(sourceType);
        todo.setSourceEntity(sourceEntity);
        todo.setSourceId(sourceId);
        return todoRepository.save(todo);
    }

    private TodoVO toVO(Todo t) {
        return new TodoVO(
                t.getId(),
                t.getAssigneeId(),
                t.getTitle(),
                t.getSourceType(),
                t.getSourceEntity(),
                t.getSourceId(),
                t.getStatus(),
                t.getDueDate(),
                t.getCreateTime());
    }
}
