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
import com.xuejiai.aaf.module.system.task.domain.Todo;
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
 * <p>继承 BaseCrudController 获得标准 CRUD 接口； 用户隔离由行级数据权限规则（entity=todo）控制，管理员可查看全部。
 *
 * <p><b>权限测试指引</b>：本类演示四种鉴权写法，可分别构造以下场景验证。
 *
 * <table border="1">
 *   <caption>鉴权写法对照</caption>
 *   <tr><th>端点</th><th>写法</th><th>判断依据</th></tr>
 *   <tr><td>{@code GET/POST/PUT/DELETE /api/todos}（继承自基类）</td>
 *       <td>{@code @PreAuthorize("@crudAuth.can(#root.getThis(), 'read'/'create'/...")}</td>
 *       <td>权限码未在 sys_permission_code 注册时降级为仅登录；已注册则严格查权限码</td></tr>
 *   <tr><td>{@link #listByEntity} / {@link #updateStatus} / {@link #share}</td>
 *       <td>{@code @PreAuthorize("hasPermission(null, 'system:todo:xxx')")}</td>
 *       <td>L1 功能权限：直查用户是否持有该权限码，super_admin 自动放行</td></tr>
 *   <tr><td>{@link #clearDoneTodos}</td>
 *       <td>{@code @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")}</td>
 *       <td>纯角色判断：只读 JWT 的 ROLE_* authority，不查权限码表</td></tr>
 *   <tr><td>{@link #collabView}</td>
 *       <td>{@code @PreAuthorize("hasPermission(#id, 'todo', 'can_read')")}</td>
 *       <td>L2 关系权限（ReBAC）：查 sys_permission_tuple 是否存在满足条件的关系元组</td></tr>
 * </table>
 *
 * <p><b>手工测试步骤</b>（需两个测试账号 A、B，均为非 super_admin 的普通用户）：
 *
 * <ol>
 *   <li>L3 行级隔离：A 创建一条待办（{@code POST /api/todos}，不传 assigneeId 默认指派给自己）； B 直接 {@code GET
 *       /api/todos/{id}} 或在列表中查询 → 因 assigneeId≠B，规则过滤后应返回 404 / 空列表。
 *   <li>L1 权限码：不持有 {@code system:todo:update} 权限码的账号调用 {@link #updateStatus} 或 {@link #share} →
 *       403；持有该权限码的账号调用应成功。
 *   <li>L1 角色：非 ADMIN/SUPER_ADMIN 账号调用 {@link #clearDoneTodos} → 403； 赋予 ADMIN 角色后调用应成功且仅清理
 *       status=done 的记录（跨用户）。
 *   <li>L2 对象级关系：延续步骤 1 的场景，B 直接调用 {@link #collabView}（{@code GET /api/todos/{id}/collab-view}）应仍为
 *       403（尚无关系元组）；A 调用 {@link #share} 授予 B {@code VIEWER} 关系后，B 再次调用 {@link #collabView} 应成功返回详情，
 *       验证其绕过了步骤 1 的 assigneeId 行级限制。
 *   <li>super_admin 快速通道：以 super_admin 账号重复步骤 1、3、4 中被拒绝的调用，均应放行， 验证 {@code
 *       AafPermissionEvaluator}/{@code CrudPermissionAuthorizer} 的超管绕过逻辑。
 * </ol>
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

    @Override
    protected BaseCrudService<Todo, TodoVO, TodoCreateDTO, TodoUpdateDTO, TodoPageDTO>
            getService() {
        return todoService;
    }

    @Operation(summary = "查询实体关联的待办列表", description = "用于 ActivityStream 展示某实体记录关联的所有待办。")
    @PreAuthorize("hasPermission(null, 'system:todo:read')")
    @GetMapping("/by-entity/{entity}/{id}")
    public Result<List<TodoVO>> listByEntity(
            @Parameter(description = "关联实体标识，如 order/contract") @PathVariable("entity")
                    String entity,
            @Parameter(description = "关联实体记录 ID") @PathVariable("id") Long id) {
        return Result.success(todoService.listByEntity(entity, id));
    }

    @Operation(summary = "更新待办状态", description = "快捷接口，仅更新状态字段，无需传完整更新 DTO。")
    @PreAuthorize("hasPermission(null, 'system:todo:update')")
    @PutMapping("/{id}/status")
    public Result<TodoVO> updateStatus(
            @Parameter(description = "待办 ID") @PathVariable Long id,
            @Valid @RequestBody TodoStatusDTO dto) {
        return Result.success(
                todoService.update(id, new TodoUpdateDTO(null, null, dto.status(), null)));
    }

    @Operation(summary = "批量清理已完成待办", description = "管理端维护操作，清理全部用户的已完成待办，跨用户不受行级数据权限限制。")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/_clear-done")
    public Result<Long> clearDoneTodos() {
        return Result.success(todoService.clearDoneTodos());
    }

    @Operation(
            summary = "分享待办给协作者",
            description =
                    "演示 L2 ReBAC：把该待办的对象级关系（OWNER/EDITOR/VIEWER）授予其他用户，"
                            + "使其不受 assigneeId 行级隔离限制即可访问。仅能改待办的人（L1）才能发起分享。")
    @PreAuthorize("hasPermission(null, 'system:todo:update')")
    @PostMapping("/{id}/share")
    public Result<Void> share(
            @Parameter(description = "待办 ID") @PathVariable Long id,
            @Valid @RequestBody ShareTodoDTO dto) {
        todoService.share(id, dto.subjectId(), dto.relation());
        return Result.success();
    }

    @Operation(
            summary = "查询协作待办详情",
            description =
                    "演示 L2 ReBAC：非执行人但被授予关系（can_read 对应 OWNER/EDITOR/VIEWER）的用户可查看。"
                            + "对象级权限通过 sys_permission_tuple 关系元组判断，不受 assigneeId 行级隔离限制。")
    @PreAuthorize("hasPermission(#id, 'todo', 'can_read')")
    @GetMapping("/{id}/collab-view")
    public Result<TodoVO> collabView(@Parameter(description = "待办 ID") @PathVariable Long id) {
        return Result.success(todoService.getSharedTodo(id));
    }
}
