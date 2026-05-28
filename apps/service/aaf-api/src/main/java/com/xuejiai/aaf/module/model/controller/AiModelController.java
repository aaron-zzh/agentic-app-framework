package com.xuejiai.aaf.module.model.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.model.service.AiModelService;
import com.xuejiai.aaf.module.model.vo.AiModelCreateDTO;
import com.xuejiai.aaf.module.model.vo.AiModelUpdateDTO;
import com.xuejiai.aaf.module.model.vo.AiModelVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AI 模型管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AI 模型管理")
@RestController
@RequestMapping("/api/ai/models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    @Operation(summary = "创建模型")
    @PostMapping
    public Result<AiModelVO> create(@Validated @RequestBody AiModelCreateDTO dto) {
        return Result.success(aiModelService.create(dto));
    }

    @Operation(summary = "模型列表（分页）")
    @GetMapping
    public Result<PageResult<AiModelVO>> list(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean enabled,
            Pageable pageable) {
        return Result.success(aiModelService.list(provider, enabled, pageable));
    }

    @Operation(summary = "已启用模型列表（下拉选择用）")
    @GetMapping("/enabled")
    public Result<List<AiModelVO>> listEnabled() {
        return Result.success(aiModelService.listEnabled());
    }

    @Operation(summary = "模型详情")
    @GetMapping("/{id}")
    public Result<AiModelVO> getById(@PathVariable Long id) {
        return Result.success(aiModelService.getById(id));
    }

    @Operation(summary = "更新模型")
    @PutMapping("/{id}")
    public Result<AiModelVO> update(@PathVariable Long id, @RequestBody AiModelUpdateDTO dto) {
        return Result.success(aiModelService.update(id, dto));
    }

    @Operation(summary = "启用模型")
    @PutMapping("/{id}/enable")
    public Result<AiModelVO> enable(@PathVariable Long id) {
        return Result.success(aiModelService.toggleEnabled(id, true));
    }

    @Operation(summary = "禁用模型")
    @PutMapping("/{id}/disable")
    public Result<AiModelVO> disable(@PathVariable Long id) {
        return Result.success(aiModelService.toggleEnabled(id, false));
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelService.delete(id);
        return Result.success();
    }
}
