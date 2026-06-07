package com.xuejiai.aaf.module.system.task.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.service.TodoService;
import com.xuejiai.aaf.module.system.task.vo.TodoCreateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoPageDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoStatusDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoUpdateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 待办事项接口。
 *
 * <p>继承 BaseCrudController 获得标准 CRUD 接口； 用户隔离由行级数据权限规则（entity=todo）控制，管理员可查看全部。
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

    @Operation(summary = "查询实体关联的待办列表（ActivityStream 用）")
    @GetMapping("/by-entity/{entity}/{id}")
    public Result<List<TodoVO>> listByEntity(
            @PathVariable("entity") String entity, @PathVariable("id") Long id) {
        return Result.success(todoService.listByEntity(entity, id));
    }

    @Operation(summary = "更新待办状态（快捷接口）")
    @PutMapping("/{id}/status")
    public Result<TodoVO> updateStatus(
            @PathVariable Long id, @Valid @RequestBody TodoStatusDTO dto) {
        return Result.success(
                todoService.update(id, new TodoUpdateDTO(null, null, dto.status(), null)));
    }
}
