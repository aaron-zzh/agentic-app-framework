package com.xuejiai.aaf.module.system.task.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum;
import com.xuejiai.aaf.common.enums.sys.TodoSourceTypeEnum;
import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.ReferenceEnforcementService;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.module.system.role.relation.GrantRelationDTO;
import com.xuejiai.aaf.module.system.role.relation.ResourceRelationService;
import com.xuejiai.aaf.module.system.task.TodoResource;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.mapper.TodoConvert;
import com.xuejiai.aaf.module.system.task.repository.TodoRepository;
import com.xuejiai.aaf.module.system.task.vo.TodoCreateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoPageDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoUpdateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

import lombok.RequiredArgsConstructor;

/** Todo 业务服务；所有公开与受控入口统一进入 BaseCrud enforcement。 */
@Service
@RequiredArgsConstructor
public class TodoService
        extends BaseCrudService<Todo, TodoVO, TodoCreateDTO, TodoUpdateDTO, TodoPageDTO> {

    private static final String REBAC_OBJECT_TYPE = "todo";

    private final TodoRepository todoRepository;
    private final ResourceRelationService resourceRelationService;
    private final OperatorContext operatorContext;
    private final ReferenceEnforcementService referenceEnforcementService;

    @Override
    protected TodoRepository getRepository() {
        return todoRepository;
    }

    /** 关联输出由通用视图组装器生成；该基础转换只作为无关联字段集的后备路径。 */
    @Override
    protected TodoVO toVO(Todo entity) {
        return com.xuejiai.aaf.common.util.JsonUtils.convertValue(entity, TodoVO.class);
    }

    /** 创建权限已由 BaseCrud 在调用本方法前完成校验。 */
    @Override
    protected Todo toEntity(TodoCreateDTO dto) {
        var subjectId = currentSubjectId();
        var todo = TodoConvert.INSTANCE.toEntity(dto);
        var assigneeId = dto.assigneeId() == null ? subjectId : dto.assigneeId();
        todo.setAssigneeId(assigneeId);
        todo.setSourceType(TodoSourceTypeEnum.MANUAL.getCode());
        applyCreateSource(todo, dto.source());
        return todo;
    }

    @Override
    protected void updateEntity(Todo todo, TodoUpdateDTO dto) {
        requireExpectedVersion(todo, dto.expectedVersion());
        applyRequired(dto.title(), "title", this::requireTitle, todo::setTitle);
        applyRequired(dto.category(), "category", this::requireCategory, todo::setCategory);
        applyRequired(dto.status(), "status", this::requireStatus, todo::setStatus);
        applyAssignee(todo, dto.assigneeId());
        applyNullable(dto.dueDate(), todo::setDueDate);
        applySourcePatch(todo, dto.source());
        todo.setVersion(currentVersion(todo) + 1);
    }

    @Override
    protected Specification<Todo> buildSpec(TodoPageDTO request) {
        return SpecificationBuilder.<Todo>builder()
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("category", request.getCategory())
                .eqIfPresent("dueDate", request.getDueDate())
                .eqIfPresent("sourceEntity", request.getSourceEntity())
                .eqIfPresent("sourceId", request.getSourceId())
                .build();
    }

    /** 来源读策略先于 Todo 查询及关系 Loader。 */
    public List<TodoVO> listByEntity(String sourceEntity, Long sourceId) {
        var reference = new ResourceReference(sourceEntity, sourceId);
        requireValidReference(reference);
        if (!referenceEnforcementService.canRead(TodoResource.KEY, null, "source", reference)) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, "来源记录");
        }
        var spec =
                SpecificationBuilder.<Todo>builder()
                        .eqIfPresent("sourceEntity", sourceEntity)
                        .eqIfPresent("sourceId", sourceId)
                        .build();
        return queryListWithAccess(
                spec, Sort.by("id").descending(), "detail", CrudOperation.GET, AccessMode.DEFAULT);
    }

    /** 分享使用具名 UPDATE 命令绑定目标用户、关系、CURRENT/PROPOSED 与命令摘要。 */
    @Transactional
    public void share(Long todoId, Long subjectId, String relation) {
        var command = new ShareTodoCommand(subjectId, relation);
        var plan =
                new CustomUpdatePlan<Todo, ShareTodoCommand, Void, Void>(
                        TodoResource.COMMAND_SHARE,
                        java.util.Set.of("participants"),
                        (todo, request) -> requireShareCommand(request),
                        (todo, request) -> {},
                        (todo, request) -> {
                            resourceRelationService.grant(
                                    new GrantRelationDTO(
                                            REBAC_OBJECT_TYPE,
                                            String.valueOf(todo.getId()),
                                            request.relation(),
                                            "USER",
                                            String.valueOf(request.subjectId()),
                                            null,
                                            null));
                            return null;
                        },
                        false,
                        (todo, request, ignored) -> {},
                        (todo, request, ignored) -> null);
        executeCustomUpdateCommand(todoId, command, plan);
    }

    private void requireShareCommand(ShareTodoCommand command) {
        if (command.subjectId() == null
                || command.subjectId() <= 0
                || command.relation() == null
                || command.relation().isBlank()) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        try {
            com.xuejiai.aaf.common.enums.sys.RebacRelationEnum.valueOf(
                    command.relation().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private record ShareTodoCommand(Long subjectId, String relation) {}

    /** 标准 GET 在 L3 未命中时可声明 todo:{id}#can_read；其他操作不声明关系替代路径。 */
    @Override
    protected AuthorizationPlan.RelationRequirement relationRequirement(
            Long id, CrudOperation operation) {
        if (operation != CrudOperation.GET) {
            return null;
        }
        return new AuthorizationPlan.RelationRequirement(
                REBAC_OBJECT_TYPE, String.valueOf(id), "can_read");
    }

    /** 内部业务创建必须处于 SYSTEM_JOB 权限上下文，不提供默认模式 fallback。 */
    @Transactional
    public void create(
            Long assigneeId,
            String title,
            TodoSourceTypeEnum sourceType,
            String sourceEntity,
            Long sourceId) {
        requireTitle(title);
        if (sourceType == null) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var source = new ResourceReference(sourceEntity, sourceId);
        var todo = new Todo();
        todo.setAssigneeId(assigneeId);
        todo.setTitle(title);
        todo.setSourceType(sourceType.getCode());
        todo.setSourceEntity(source.resource());
        todo.setSourceId(source.id());
        var request =
                new TodoCreateDTO(
                        title, null, assigneeId, Patch.value(source), null, Patch.absent());
        createEntityWithAccess(todo, request, AccessMode.SYSTEM_JOB);
    }

    /** 管理清理使用 ADMIN_MAINTENANCE，并以唯一 DELETE_BATCH 决策完成范围加载和数据变更。 */
    @Transactional
    public long clearDoneTodos() {
        var spec =
                SpecificationBuilder.<Todo>builder()
                        .eqIfPresent("status", TodoStatusEnum.DONE.getCode())
                        .build();
        return deleteMatchingWithAccess(spec, AccessMode.ADMIN_MAINTENANCE);
    }

    private void applyCreateSource(Todo todo, Patch<ResourceReference> patch) {
        if (patch.isAbsent() || patch.isNullValue()) {
            todo.setSourceEntity(null);
            todo.setSourceId(null);
            return;
        }
        applySource(todo, patch.valueOrNull());
    }

    private void applySourcePatch(Todo todo, Patch<ResourceReference> patch) {
        if (patch.isAbsent()) {
            return;
        }
        var source = patch.valueOrNull();
        if (matches(todo, source)) {
            return;
        }
        if (!TodoSourceTypeEnum.isSourceEditable(todo.getSourceType())) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        if (source == null) {
            todo.setSourceEntity(null);
            todo.setSourceId(null);
            return;
        }
        applySource(todo, source);
    }

    private void applySource(Todo todo, ResourceReference source) {
        requireValidReference(source);
        todo.setSourceEntity(source.resource());
        todo.setSourceId(source.id());
    }

    private void applyAssignee(Todo todo, Patch<Long> patch) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var assigneeId = patch.valueOrNull();
        todo.setAssigneeId(assigneeId);
    }

    private void requireValidReference(ResourceReference target) {
        if (target == null
                || target.resource() == null
                || target.resource().isBlank()
                || target.id() == null
                || target.id() <= 0) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private Long currentSubjectId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
    }

    private void requireExpectedVersion(Todo todo, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(todo)) {
            throw new BusinessException(409, "待办已被其他请求修改，请刷新后重试");
        }
    }

    private int currentVersion(Todo todo) {
        return todo.getVersion() == null ? 0 : todo.getVersion();
    }

    private boolean matches(Todo todo, ResourceReference source) {
        return source == null
                ? todo.getSourceEntity() == null && todo.getSourceId() == null
                : source.resource().equals(todo.getSourceEntity())
                        && source.id().equals(todo.getSourceId());
    }

    private <T> void applyRequired(
            Patch<T> patch, String field, Consumer<T> validator, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw new BusinessException(400, field + " 不能为 null");
        }
        var value = patch.valueOrNull();
        validator.accept(value);
        setter.accept(value);
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }

    private void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private void requireCategory(String category) {
        if (Arrays.stream(TodoCategoryEnum.ARRAYS).noneMatch(category::equals)) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private void requireStatus(String status) {
        if (Arrays.stream(TodoStatusEnum.ARRAYS).noneMatch(status::equals)) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }
}
