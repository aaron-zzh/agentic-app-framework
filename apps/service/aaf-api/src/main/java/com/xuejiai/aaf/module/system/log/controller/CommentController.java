package com.xuejiai.aaf.module.system.log.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.dto.CrudMetaDTO;
import com.xuejiai.aaf.framework.crud.web.NestedCrudResourceController;
import com.xuejiai.aaf.module.system.log.service.CommentService;
import com.xuejiai.aaf.module.system.log.vo.CommentCreateDTO;
import com.xuejiai.aaf.module.system.log.vo.CommentPageDTO;
import com.xuejiai.aaf.module.system.log.vo.CommentUpdateDTO;
import com.xuejiai.aaf.module.system.log.vo.CommentVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 评论嵌套资源接口。 */
@Tag(name = "评论")
@RestController
@RequestMapping("/api/{entity}/{entityId}/comments")
@RequiredArgsConstructor
public class CommentController
        implements NestedCrudResourceController<
                com.xuejiai.aaf.module.system.log.domain.Comment,
                CommentVO,
                CommentCreateDTO,
                CommentUpdateDTO,
                CommentPageDTO> {

    private final CommentService commentService;

    @Override
    public CommentService getService() {
        return commentService;
    }

    @Operation(summary = "分页查询评论")
    @GetMapping
    public Result<PageResult<CommentVO>> page(
            @PathVariable String entity, @PathVariable Long entityId, CommentPageDTO request) {
        return Result.success(commentService.pageByEntity(entity, entityId, request));
    }

    @Operation(summary = "查询评论窗口", description = "返回评论列表、ID 列表、queryToken 和字段集。")
    @GetMapping("/_query")
    public Result<PageResult<CommentVO>> queryWindow(
            @PathVariable String entity,
            @PathVariable Long entityId,
            CommentPageDTO request,
            @Parameter(description = "字段集：list/detail，默认 list") @RequestParam(defaultValue = "list")
                    String fieldSet) {
        return Result.success(
                commentService.queryWindowByEntity(entity, entityId, request, fieldSet));
    }

    @Operation(summary = "查询评论详情")
    @GetMapping("/{commentId}")
    public Result<CommentVO> get(
            @PathVariable String entity,
            @PathVariable Long entityId,
            @PathVariable Long commentId,
            @RequestParam(required = false) String queryToken,
            @RequestParam(defaultValue = "detail") String fieldSet) {
        return Result.success(
                commentService.getByEntity(entity, entityId, commentId, queryToken, fieldSet));
    }

    @Operation(summary = "评论元数据")
    @GetMapping("/_meta")
    public Result<CrudMetaDTO> meta() {
        return Result.success(commentService.meta());
    }

    @Operation(summary = "创建评论")
    @PostMapping
    public Result<CommentVO> create(
            @PathVariable String entity,
            @PathVariable Long entityId,
            @RequestBody CommentCreateDTO request) {
        return Result.success(commentService.createByEntity(entity, entityId, request));
    }

    @Operation(summary = "更新评论")
    @PutMapping("/{commentId}")
    public Result<CommentVO> update(
            @PathVariable String entity,
            @PathVariable Long entityId,
            @PathVariable Long commentId,
            @RequestBody CommentUpdateDTO request) {
        return Result.success(commentService.updateByEntity(entity, entityId, commentId, request));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public Result<Void> delete(
            @PathVariable String entity,
            @PathVariable Long entityId,
            @PathVariable Long commentId) {
        commentService.deleteByEntity(entity, entityId, commentId);
        return Result.success();
    }
}
