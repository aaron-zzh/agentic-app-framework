package com.xuejiai.aaf.module.system.task.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.service.TodoQueueService;
import com.xuejiai.aaf.module.system.task.service.TodoService;
import com.xuejiai.aaf.module.system.task.vo.ShareTodoDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoCreateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoPageDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoStatusDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoUpdateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 待办事项接口。
 *
 * <p>继承 BaseCrudController 获得标准 CRUD 端点。Controller 仅校验已认证；L1/L4、租户、记录、个人和字段约束统一由 BaseCrud Service 的
 * CRUD PEP 执行。
 *
 * <p>Todo 标准 GET 在默认 L3 {@code assigneeId} 范围未命中时，可按 {@code todo:{id}#can_read}
 * 关系要求重试；更新与删除不声明关系替代路径。自定义端点继续保留各自显式的权限或角色约束。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "待办事项")
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController
        extends BaseCrudController<Todo, TodoVO, TodoCreateDTO, TodoUpdateDTO, TodoPageDTO> {

    private final TodoService todoService;
    private final TodoQueueService todoQueueService;

    @Override
    protected BaseCrudService<Todo, TodoVO, TodoCreateDTO, TodoUpdateDTO, TodoPageDTO>
            getService() {
        return todoService;
    }

    @Operation(summary = "查询实体关联的待办列表", description = "用于 ActivityStream 展示某实体记录关联的所有待办。")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/by-entity/{entity}/{id}")
    public Result<List<TodoVO>> listByEntity(
            @Parameter(description = "关联来源 resource，如 system.order") @PathVariable("entity")
                    String entity,
            @Parameter(description = "关联实体记录 ID") @PathVariable("id") Long id) {
        return Result.success(todoService.listByEntity(entity, id));
    }

    @Operation(summary = "更新待办状态", description = "快捷接口，仅更新状态字段，无需传完整更新 DTO。")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/status")
    public Result<TodoVO> updateStatus(
            @Parameter(description = "待办 ID") @PathVariable Long id,
            @Valid @RequestBody TodoStatusDTO dto) {
        return Result.success(
                todoService.update(
                        id,
                        new TodoUpdateDTO(
                                Patch.absent(),
                                Patch.absent(),
                                Patch.value(dto.status()),
                                Patch.absent(),
                                Patch.absent(),
                                Patch.absent(),
                                Patch.absent(),
                                dto.expectedVersion())));
    }

    @Operation(summary = "批量清理已完成待办", description = "管理端维护操作，清理全部用户的已完成待办，跨用户不受行级数据权限限制。")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/_clear-done")
    public Result<Long> clearDoneTodos() {
        return Result.success(todoService.clearDoneTodos());
    }

    @Operation(summary = "异步清理已完成待办", description = "通用 Redis Stream 任务队列试点，立即返回任务 ID。")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/_clear-done/async")
    public Result<String> clearDoneTodosAsync() {
        return Result.success(todoQueueService.enqueueClearDone());
    }

    @Operation(
            summary = "分享待办给协作者",
            description =
                    "演示 L2 ReBAC：把该待办的对象级关系（OWNER/EDITOR/VIEWER）授予其他用户，"
                            + "使其不受 assigneeId 行级隔离限制即可访问。仅能改待办的人（L1）才能发起分享。")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/share")
    public Result<Void> share(
            @Parameter(description = "待办 ID") @PathVariable Long id,
            @Valid @RequestBody ShareTodoDTO dto) {
        todoService.share(id, dto.subjectId(), dto.relation());
        return Result.success();
    }
}
