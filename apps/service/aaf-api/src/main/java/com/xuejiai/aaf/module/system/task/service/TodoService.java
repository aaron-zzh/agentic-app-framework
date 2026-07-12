package com.xuejiai.aaf.module.system.task.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.role.relation.GrantRelationDTO;
import com.xuejiai.aaf.module.system.role.relation.ResourceRelationService;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.mapper.TodoConvert;
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

    /** ReBAC 对象类型标识，对应 sys_permission_tuple.object_type。 */
    private static final String REBAC_OBJECT_TYPE = "todo";

    private final TodoRepository todoRepository;
    private final ResourceRelationService resourceRelationService;
    private final OperatorContext operatorContext;

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
        return TodoConvert.INSTANCE.toVO(t);
    }

    @Override
    protected Todo toEntity(TodoCreateDTO dto) {
        var todo = TodoConvert.INSTANCE.toEntity(dto);
        // Swagger 承诺"执行人 ID，不传则指派给当前用户"，assigneeId 非空约束要求此处兜底填充。
        if (todo.getAssigneeId() == null) {
            operatorContext.currentOwnerId().ifPresent(todo::setAssigneeId);
        }
        return todo;
    }

    @Override
    protected void updateEntity(Todo todo, TodoUpdateDTO dto) {
        TodoConvert.INSTANCE.updateFromDTO(dto, todo);
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

    /**
     * 分享待办给协作者（L2 ReBAC 演示）。
     *
     * <p>写入 sys_permission_tuple 关系元组：todo#{relation}@user:{subjectId}。 被分享者不受 L3 assigneeId
     * 行级隔离限制，凭关系元组通过 {@link com.xuejiai.aaf.framework.security.access.AafPermissionEvaluator} 的 L2
     * 通道单独获得对象级权限。
     */
    @Transactional
    public void share(Long todoId, Long subjectId, String relation) {
        resourceRelationService.grant(
                new GrantRelationDTO(
                        REBAC_OBJECT_TYPE,
                        String.valueOf(todoId),
                        relation,
                        "USER",
                        String.valueOf(subjectId),
                        null,
                        null));
    }

    /**
     * 查询协作待办详情（L2 ReBAC 演示）。
     *
     * <p>绕过 L3 assigneeId 行级隔离，直接按 ID 查询；对象级权限已由 Controller 的 {@code
     * hasPermission(#id,'todo','can_read')} 校验。
     */
    public TodoVO getSharedTodo(Long todoId) {
        var todo =
                todoRepository
                        .findById(todoId)
                        .orElseThrow(() -> new IllegalArgumentException("待办不存在"));
        return toVO(todo);
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

    /**
     * 批量清理已完成待办（管理端维护操作）。
     *
     * <p>不受行级数据权限限制——按设计仅管理员可调用（Controller 层 {@code hasRole} 校验），需要跨用户清理全部已完成记录。
     */
    @Transactional
    public long clearDoneTodos() {
        var doneTodos =
                todoRepository.findAll(
                        SpecificationBuilder.<Todo>builder()
                                .eqIfPresent("status", TodoStatusEnum.DONE.getCode())
                                .build());
        todoRepository.deleteAll(doneTodos);
        return doneTodos.size();
    }
}
