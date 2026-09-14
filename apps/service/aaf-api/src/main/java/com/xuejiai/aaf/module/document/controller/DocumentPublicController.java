package com.xuejiai.aaf.module.document.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.document.service.DocumentService;
import com.xuejiai.aaf.module.document.vo.DocumentPublicVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 文档公开阅读接口（无需登录）。
 *
 * <p>挂在 {@code /api/public/docs/**}，由 SecurityConfig.PRODUCT_PUBLIC_PATHS 中的 {@code /api/public/**}
 * 放行。只返回已发布文档的精确 {@code publishedVersionId} 正文，不读取或暴露 {@code currentVersionId} 对应的未发布编辑草稿。
 *
 * @author AaronZZH &amp; Kiro
 */
@Tag(name = "文档（公开）")
@RestController
@RequestMapping("/api/public/docs")
@RequiredArgsConstructor
public class DocumentPublicController {

    private final DocumentService documentService;

    @Operation(summary = "获取单个已发布文档的精确版本正文；未发布或不存在返回 404")
    @GetMapping("/{id}")
    public Result<DocumentPublicVO> getPublishedById(@PathVariable Long id) {
        return Result.success(documentService.getPublishedById(id));
    }
}
