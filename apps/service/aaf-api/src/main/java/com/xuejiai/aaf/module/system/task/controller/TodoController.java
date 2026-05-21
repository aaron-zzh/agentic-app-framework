package com.xuejiai.aaf.module.system.task.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.task.service.TodoService;
import com.xuejiai.aaf.module.system.task.vo.TodoPageDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoStatusDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 待办事项接口。 */
@Tag(name = "待办事项")
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final ActorContext actorContext;

    @Operation(summary = "分页查询待办")
    @GetMapping
    public Result<PageResult<TodoVO>> page(@Validated @ParameterObject TodoPageDTO request) {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(todoService.page(userId, request));
    }

    @Operation(summary = "更新待办状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id, @Validated @RequestBody TodoStatusDTO dto) {
        Long userId = actorContext.currentUserId().orElseThrow();
        todoService.updateStatus(userId, id, dto.status());
        return Result.success();
    }
}
