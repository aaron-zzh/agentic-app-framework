package com.xuejiai.aaf.module.system.task.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.repository.TodoRepository;
import com.xuejiai.aaf.module.system.task.vo.TodoCreateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoPageDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoUpdateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

import lombok.RequiredArgsConstructor;

/**
 * 待办业务逻辑，继承 BaseCrudService 获得标准 CRUD 能力。
 *
 * <p>用户隔离（普通用户只能看自己的待办）通过行级数据权限规则实现： entity=todo,
 * condition={"field":"assigneeId","op":"eq","value":"$user.id"} 管理员角色（super_admin）自动绕过，可查看/操作所有待办。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class TodoService
        extends BaseCrudService<Todo, TodoVO, TodoCreateDTO, TodoUpdateDTO, TodoPageDTO> {

    private final TodoRepository todoRepository;

    @Override
    protected JpaRepository<Todo, Long> getRepository() {
        return todoRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Todo> getSpecExecutor() {
        return todoRepository;
    }

    @Override
    protected TodoVO toVO(Todo t) {
        return new TodoVO(
                t.getId(),
                t.getAssigneeId(),
                t.getTitle(),
                t.getCategory(),
                t.getSourceType(),
                t.getSourceEntity(),
                t.getSourceId(),
                t.getStatus(),
                t.getDueDate(),
                t.getCreateTime());
    }

    @Override
    protected Todo toEntity(TodoCreateDTO dto) {
        var todo = new Todo();
        todo.setAssigneeId(dto.assigneeId());
        todo.setTitle(dto.title());
        todo.setCategory(dto.category() != null ? dto.category() : "todo");
        todo.setSourceEntity(dto.sourceEntity());
        todo.setSourceId(dto.sourceId());
        todo.setDueDate(dto.dueDate());
        return todo;
    }

    @Override
    protected void updateEntity(Todo todo, TodoUpdateDTO dto) {
        if (dto.title() != null) todo.setTitle(dto.title());
        if (dto.category() != null) todo.setCategory(dto.category());
        if (dto.status() != null) todo.setStatus(dto.status());
        if (dto.dueDate() != null) todo.setDueDate(dto.dueDate());
    }

    @Override
    protected Specification<Todo> buildSpec(TodoPageDTO req) {
        return SpecificationBuilder.<Todo>builder()
                .eqIfPresent("status", req.getStatus())
                .eqIfPresent("category", req.getCategory())
                .eqIfPresent("sourceEntity", req.getSourceEntity())
                .eqIfPresent("sourceId", req.getSourceId())
                .build();
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by("id").descending();
    }

    @Override
    protected String entityName() {
        return "待办";
    }

    @Override
    protected String entitySlug() {
        return "todo";
    }

    /** 查询指定实体的待办列表（ActivityStream 用） */
    public List<TodoVO> listByEntity(String sourceEntity, Long sourceId) {
        Specification<Todo> spec =
                SpecificationBuilder.<Todo>builder()
                        .eqIfPresent("sourceEntity", sourceEntity)
                        .eqIfPresent("sourceId", sourceId)
                        .build();
        return todoRepository.findAll(spec, Sort.by("id").descending()).stream()
                .map(this::toVO)
                .toList();
    }

    /** 内部调用：快速创建待办（供 CommentService 等内部模块使用） */
    @Transactional
    public void create(
            Long assigneeId, String title, String sourceType, String sourceEntity, Long sourceId) {
        var todo = new Todo();
        todo.setAssigneeId(assigneeId);
        todo.setTitle(title);
        todo.setSourceType(sourceType);
        todo.setSourceEntity(sourceEntity);
        todo.setSourceId(sourceId);
        todoRepository.save(todo);
    }
}
