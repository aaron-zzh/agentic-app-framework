/**
 * Assistant 管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.assistant;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Assistant 管理")
@RestController
@RequestMapping("/api/ai/assistants")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantManagementService assistantService;

    @Operation(summary = "创建 Assistant")
    @PostMapping
    public Result<AssistantVO> create(@Validated @RequestBody AssistantCreateDTO dto) {
        return Result.success(assistantService.create(dto));
    }

    @Operation(summary = "我的 Assistant 列表（分页）")
    @GetMapping
    public Result<PageResult<AssistantVO>> list(Pageable pageable) {
        return Result.success(assistantService.list(pageable));
    }

    @Operation(summary = "Assistant 详情")
    @GetMapping("/{id}")
    public Result<AssistantVO> getById(@PathVariable Long id) {
        return Result.success(assistantService.getById(id));
    }

    @Operation(summary = "更新 Assistant")
    @PutMapping("/{id}")
    public Result<AssistantVO> update(@PathVariable Long id, @Validated @RequestBody AssistantUpdateDTO dto) {
        return Result.success(assistantService.update(id, dto));
    }

    @Operation(summary = "删除 Assistant")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assistantService.delete(id);
        return Result.success();
    }
}
