package com.xuejiai.aaf.module.aigc.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.aigc.service.GenerationTemplateService;
import com.xuejiai.aaf.module.aigc.vo.GenerationTemplateCreateDTO;
import com.xuejiai.aaf.module.aigc.vo.GenerationTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 参数模板接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 参数模板")
@RestController
@RequestMapping("/api/aigc/templates")
@RequiredArgsConstructor
public class GenerationTemplateController {

    private final GenerationTemplateService templateService;

    @Operation(summary = "查询用户模板")
    @GetMapping
    public Result<Page<GenerationTemplateVO>> listByUser(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(templateService.listByUser(userId, page, size));
    }

    @Operation(summary = "查询公开模板")
    @GetMapping("/public")
    public Result<Page<GenerationTemplateVO>> listPublic(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(templateService.listPublic(category, page, size));
    }

    @Operation(summary = "创建模板")
    @PostMapping
    public Result<GenerationTemplateVO> create(
            @RequestParam Long userId, @Valid @RequestBody GenerationTemplateCreateDTO dto) {
        return Result.success(templateService.create(userId, dto));
    }

    @Operation(summary = "使用模板")
    @PostMapping("/{id}/use")
    public Result<GenerationTemplateVO> use(@PathVariable Long id) {
        return Result.success(templateService.use(id));
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }
}
