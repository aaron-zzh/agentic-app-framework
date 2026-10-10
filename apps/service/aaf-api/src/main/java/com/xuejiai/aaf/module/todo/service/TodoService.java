package com.xuejiai.aaf.module.todo.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.todo.ErrorCodeConstants.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.enums.sys.RebacRelationEnum;
import com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum;
import com.xuejiai.aaf.common.enums.sys.TodoSourceTypeEnum;
import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.exception.ErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.ReferenceEnforcementService;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessage;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.module.system.role.relation.GrantRelationDTO;
import com.xuejiai.aaf.module.system.role.relation.ResourceRelationService;
import com.xuejiai.aaf.module.todo.TodoResource;
import com.xuejiai.aaf.module.todo.domain.Todo;
import com.xuejiai.aaf.module.todo.mapper.TodoConvert;
import com.xuejiai.aaf.module.todo.repository.TodoRepository;
import com.xuejiai.aaf.module.todo.vo.TodoCollaboratorVO;
import com.xuejiai.aaf.module.todo.vo.TodoCreateDTO;
import com.xuejiai.aaf.module.todo.vo.TodoPageDTO;
import com.xuejiai.aaf.module.todo.vo.TodoUpdateDTO;
import com.xuejiai.aaf.module.todo.vo.TodoVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Todo 业务服务；所有公开与受控入口统一进入 BaseCrud enforcement。
 *
 * <p>本类是"标准业务服务"的参考实现，按职责分为：
 *
 * <ul>
 *   <li>必须实现的扩展点：{@link #toEntity}、{@link #updateEntity}、{@link #toVO}、{@link #buildSpec}
 *   <li>Patch 三态更新：请求字段缺失表示不变、显式 null 表示清空（必填字段则拒绝）、有值表示设置
 *   <li>具名自定义 UPDATE 命令：协作者分享与撤销，见 {@link #share}、{@link #unshare}
 *   <li>内部受控入口：系统任务创建 {@link #createSystemTodo}、管理清理 {@link #clearDoneTodos}
 *   <li>创建钩子与更新中的指派通知：事务提交后才发送，回滚不产生通知
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService
        extends BaseCrudService<Todo, TodoVO, TodoCreateDTO, TodoUpdateDTO, TodoPageDTO> {

    private static final String REBAC_OBJECT_TYPE = "todo";
    private static final String SUBJECT_TYPE_USER = "USER";

    /** 状态流转规则：已完成与已忽略只能先重新打开为待处理，不能互相直接转换；同状态视为幂等不检查。 */
    private static final Map<String, Set<String>> STATUS_TRANSITIONS =
            Map.of(
                    TodoStatusEnum.PENDING.getCode(),
                    Set.of(TodoStatusEnum.DONE.getCode(), TodoStatusEnum.IGNORED.getCode()),
                    TodoStatusEnum.DONE.getCode(),
                    Set.of(TodoStatusEnum.PENDING.getCode()),
                    TodoStatusEnum.IGNORED.getCode(),
                    Set.of(TodoStatusEnum.PENDING.getCode()));

    private final TodoRepository todoRepository;
    private final ResourceRelationService resourceRelationService;
    private final OperatorContext operatorContext;
    private final ReferenceEnforcementService referenceEnforcementService;
    private final InternalMessageSender messageSender;

    @Override
    protected TodoRepository getRepository() {
        return todoRepository;
    }

    /** 关联输出由通用视图组装器生成；该基础转换只作为无关联字段集的后备路径。 */
    @Override
    protected TodoVO toVO(Todo entity) {
        return JsonUtils.convertValue(entity, TodoVO.class);
    }

    /** 创建权限已由 BaseCrud 在调用本方法前完成校验。 */
    @Override
    protected Todo toEntity(TodoCreateDTO dto) {
        var subjectId = currentSubjectId();
        var todo = TodoConvert.INSTANCE.toEntity(dto);
        var assigneeId = dto.assigneeId() == null ? subjectId : dto.assigneeId();
        todo.setAssigneeId(assigneeId);
        todo.setSourceType(TodoSourceTypeEnum.MANUAL.getCode());
        // 创建时来源缺失与显式 null 等价：都表示不关联记录
        setSource(todo, dto.source().valueOrNull());
        return todo;
    }

    /** 创建并同步关系后，把待办指派给他人时通知新执行人；指派给自己不通知。 */
    @Override
    protected void afterCreate(Todo todo, TodoCreateDTO request) {
        notifyAssigned(todo);
    }

    /**
     * 应用更新请求；每个字段按 Patch 三态处理（缺失不变 / null 清空或拒绝 / 有值设置）。
     *
     * <p>乐观锁由调用方通过 expectedVersion 声明，版本不一致直接 409。
     */
    @Override
    protected void updateEntity(Todo todo, TodoUpdateDTO dto) {
        requireExpectedVersion(todo, dto.expectedVersion());
        applyRequired(dto.title(), TODO_TITLE_REQUIRED, this::requireTitle, todo::setTitle);
        applyRequired(
                dto.category(), TODO_CATEGORY_INVALID, this::requireCategory, todo::setCategory);
        applyRequired(
                dto.status(),
                TODO_STATUS_INVALID,
                this::requireStatus,
                status -> changeStatus(todo, status));
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
                .eqIfPresent("sourceEntity", request.getSourceEntity())
                .eqIfPresent("sourceId", request.getSourceId())
                .build();
    }

    /** 仅更新状态的快捷入口；复用标准更新流程，因此同样经过状态流转校验和乐观锁。 */
    @Transactional
    public TodoVO updateStatus(Long id, String status, Integer expectedVersion) {
        return update(id, TodoUpdateDTO.ofStatus(status, expectedVersion));
    }

    /** 来源读策略先于 Todo 查询及关系 Loader。 */
    public List<TodoVO> listByEntity(String sourceEntity, Long sourceId) {
        var reference = new ResourceReference(sourceEntity, sourceId);
        requireValidReference(reference);
        if (!referenceEnforcementService.canRead(TodoResource.KEY, null, "source", reference)) {
            throw exception(TODO_SOURCE_RESOURCE_NOT_FOUND);
        }
        var spec =
                SpecificationBuilder.<Todo>builder()
                        .eqIfPresent("sourceEntity", sourceEntity)
                        .eqIfPresent("sourceId", sourceId)
                        .build();
        return queryListWithAccess(
                spec, Sort.by("id").descending(), "detail", CrudOperation.GET, AccessMode.DEFAULT);
    }

    // ==================== 协作者：分享 / 撤销 / 列出 ====================

    /** 分享使用具名 UPDATE 命令绑定目标用户、关系、CURRENT/PROPOSED 与命令摘要。 */
    @Transactional
    public void share(Long todoId, Long subjectId, String relation) {
        changeCollaborator(
                todoId,
                TodoResource.COMMAND_SHARE,
                new CollaboratorCommand(subjectId, relation),
                resourceRelationService::grant);
    }

    /** 撤销协作者的对象级关系；与分享对称，同样走具名 UPDATE 命令，由 L1 更新权限和对象级 L4 保护。 */
    @Transactional
    public void unshare(Long todoId, Long subjectId, String relation) {
        changeCollaborator(
                todoId,
                TodoResource.COMMAND_UNSHARE,
                new CollaboratorCommand(subjectId, relation),
                resourceRelationService::revoke);
    }

    /**
     * 列出待办的协作者。
     *
     * <p>先按标准详情读取校验当前用户可见该待办，再读取关系元组，因此无权读取的待办不会泄露协作者。
     */
    @Transactional(readOnly = true)
    public List<TodoCollaboratorVO> listCollaborators(Long todoId) {
        getById(todoId, null, "detail");
        return resourceRelationService
                .listByResource(REBAC_OBJECT_TYPE, String.valueOf(todoId))
                .stream()
                .filter(tuple -> SUBJECT_TYPE_USER.equals(tuple.subjectType()))
                .map(
                        tuple ->
                                new TodoCollaboratorVO(
                                        Long.valueOf(tuple.subjectId()),
                                        tuple.relation(),
                                        tuple.expiresAt()))
                .toList();
    }

    /**
     * 以具名自定义 UPDATE 命令变更协作者关系（分享与撤销共用）。
     *
     * @param relationChange 目标 L4 授权通过后才执行的关系元组写入动作
     */
    private void changeCollaborator(
            Long todoId,
            String commandType,
            CollaboratorCommand command,
            Consumer<GrantRelationDTO> relationChange) {
        var plan =
                new CustomUpdatePlan<Todo, CollaboratorCommand, Void, Void>(
                        // 命令类型：必须已在 TodoResource 的 withCustomUpdateCommands 中声明
                        commandType,
                        // 声明修改字段：自定义 UPDATE 要求非空，协作者变更沿用 participants 字段的写权限
                        Set.of("participants"),
                        // 实体加载并锁定后、变更前校验命令
                        (todo, request) -> requireCollaboratorCommand(request),
                        // 不对实体字段做 proposal 变更
                        (todo, request) -> {},
                        // 授权通过后的受控动作：写入或撤销关系元组
                        (todo, request) -> {
                            relationChange.accept(toRelation(todo, request));
                            return null;
                        },
                        // 主实体本身未变化，无需保存
                        false,
                        // 无保存后动作
                        (todo, request, ignored) -> {},
                        // 无返回值
                        (todo, request, ignored) -> null);
        executeCustomUpdateCommand(todoId, command, plan);
    }

    private GrantRelationDTO toRelation(Todo todo, CollaboratorCommand command) {
        return new GrantRelationDTO(
                REBAC_OBJECT_TYPE,
                String.valueOf(todo.getId()),
                command.relation(),
                SUBJECT_TYPE_USER,
                String.valueOf(command.subjectId()),
                null,
                null);
    }

    private void requireCollaboratorCommand(CollaboratorCommand command) {
        if (command.subjectId() == null
                || command.subjectId() <= 0
                || command.relation() == null
                || command.relation().isBlank()) {
            throw exception(TODO_SHARE_COMMAND_INVALID);
        }
        try {
            RebacRelationEnum.valueOf(command.relation().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw exception(TODO_SHARE_COMMAND_INVALID);
        }
    }

    /** 协作者变更命令：目标用户与授予（或撤销）的关系。 */
    private record CollaboratorCommand(Long subjectId, String relation) {}

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

    // ==================== 内部受控入口 ====================

    /**
     * 系统任务创建待办（如评论、任务流程生成）。
     *
     * <p>内部业务创建必须处于 SYSTEM_JOB 权限上下文，不提供默认模式 fallback。
     */
    @Transactional
    public void createSystemTodo(
            Long assigneeId,
            String title,
            TodoSourceTypeEnum sourceType,
            String sourceEntity,
            Long sourceId) {
        requireTitle(title);
        if (sourceType == null) {
            throw exception(TODO_SOURCE_TYPE_REQUIRED);
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

    // ==================== Patch 应用辅助 ====================

    /**
     * 设置或清空来源；null 表示清空，非 null 须是合法引用。
     *
     * <p>来源的读取权限由引用策略在入口统一校验，这里只做格式校验并写入字段。
     */
    private void setSource(Todo todo, ResourceReference source) {
        if (source == null) {
            todo.setSourceEntity(null);
            todo.setSourceId(null);
            return;
        }
        requireValidReference(source);
        todo.setSourceEntity(source.resource());
        todo.setSourceId(source.id());
    }

    /** 更新来源：缺失不变；与当前一致不变；系统来源不可修改；null 清空；有值设置。 */
    private void applySourcePatch(Todo todo, Patch<ResourceReference> patch) {
        if (patch.isAbsent()) {
            return;
        }
        var source = patch.valueOrNull();
        if (matches(todo, source)) {
            return;
        }
        if (!TodoSourceTypeEnum.isSourceEditable(todo.getSourceType())) {
            throw exception(TODO_SOURCE_UPDATE_FORBIDDEN);
        }
        setSource(todo, source);
    }

    /** 更新执行人：缺失不变；执行人为必填，显式 null 拒绝；真正变更时通知新执行人。 */
    private void applyAssignee(Todo todo, Patch<Long> patch) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(TODO_ASSIGNEE_REQUIRED);
        }
        var assigneeId = patch.valueOrNull();
        if (!assigneeId.equals(todo.getAssigneeId())) {
            todo.setAssigneeId(assigneeId);
            notifyAssigned(todo);
        }
    }

    /** 变更状态前校验流转规则，非法流转返回 {@code TODO_STATUS_TRANSITION_INVALID}。 */
    private void changeStatus(Todo todo, String newStatus) {
        var current = todo.getStatus();
        if (current != null
                && !current.equals(newStatus)
                && !STATUS_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw exception(TODO_STATUS_TRANSITION_INVALID, current, newStatus);
        }
        todo.setStatus(newStatus);
    }

    /**
     * 应用必填字段的 Patch：缺失不变；显式 null 抛出 {@code nullCode}；有值先校验再设置。
     *
     * @param nullCode 字段被显式置 null 时抛出的错误码
     * @param validator 值校验器，不合法时自行抛出业务异常
     */
    private <T> void applyRequired(
            Patch<T> patch, ErrorCode nullCode, Consumer<T> validator, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(nullCode);
        }
        var value = patch.valueOrNull();
        validator.accept(value);
        setter.accept(value);
    }

    /** 应用可空字段的 Patch：缺失不变；显式 null 清空；有值设置。 */
    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }

    private void requireValidReference(ResourceReference target) {
        if (target == null
                || target.resource() == null
                || target.resource().isBlank()
                || target.id() == null
                || target.id() <= 0) {
            throw exception(TODO_SOURCE_REFERENCE_INVALID);
        }
    }

    private Long currentSubjectId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new AccessDeniedException("请求未认证"));
    }

    private void requireExpectedVersion(Todo todo, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(todo)) {
            throw exception(TODO_VERSION_CONFLICT);
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

    private void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw exception(TODO_TITLE_REQUIRED);
        }
    }

    private void requireCategory(String category) {
        if (Arrays.stream(TodoCategoryEnum.ARRAYS).noneMatch(category::equals)) {
            throw exception(TODO_CATEGORY_INVALID);
        }
    }

    private void requireStatus(String status) {
        if (Arrays.stream(TodoStatusEnum.ARRAYS).noneMatch(status::equals)) {
            throw exception(TODO_STATUS_INVALID);
        }
    }

    // ==================== 指派通知 ====================

    /**
     * 通知待办的执行人被指派；执行人就是当前操作者时不通知。
     *
     * <p>消息在事务提交后才发送：事务回滚不会产生通知，发送失败也不会影响已提交的业务结果。
     */
    private void notifyAssigned(Todo todo) {
        var assigneeId = todo.getAssigneeId();
        var operatorId = operatorContext.currentOwnerId().orElse(null);
        if (assigneeId == null || assigneeId.equals(operatorId)) {
            return;
        }
        runAfterCommit(
                () ->
                        messageSender.send(
                                InternalMessage.builder()
                                        .userId(assigneeId)
                                        .type("task")
                                        .title("你被指派了新的待办")
                                        .body(todo.getTitle())
                                        .relatedUrl("/module/todo/" + todo.getId())
                                        .entityType(TodoResource.KEY.value())
                                        .entityId(todo.getId())
                                        .build()));
    }

    /** 当前存在事务时在提交后执行；无事务时立即执行。动作抛出的异常只记录日志。 */
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runQuietly(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        runQuietly(action);
                    }
                });
    }

    private void runQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            log.warn("待办提交后动作执行失败", failure);
        }
    }
}
