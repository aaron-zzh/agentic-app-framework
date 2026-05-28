package com.xuejiai.aaf.module.system.log.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.log.domain.Comment;
import com.xuejiai.aaf.module.system.log.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 评论接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "评论")
@RestController
@RequestMapping("/api/{entity}/{id}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "创建评论")
    @PostMapping
    public Result<Comment> create(
            @PathVariable("entity") String entity,
            @PathVariable("id") Long id,
            @RequestBody CommentCreateDTO request) {
        return Result.success(commentService.create(entity, id, request.content()));
    }

    @Operation(summary = "更新评论")
    @PutMapping("/{commentId}")
    public Result<Comment> update(
            @PathVariable("commentId") Long commentId, @RequestBody CommentCreateDTO request) {
        return Result.success(commentService.update(commentId, request.content()));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public Result<Void> delete(@PathVariable("commentId") Long commentId) {
        commentService.delete(commentId);
        return Result.success();
    }

    /** 评论请求体 */
    public record CommentCreateDTO(String content) {}
}
