package com.xuejiai.aaf.autodev.codegen;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.autodev.codegen.dto.EntityDefDTO;
import com.xuejiai.aaf.autodev.codegen.dto.GeneratedFile;
import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 代码生成接口。 */
@Tag(name = "代码生成")
@RestController
@RequestMapping("/api/autodev/codegen")
@RequiredArgsConstructor
public class CodegenController {

    private final CodegenService codegenService;

    @Operation(summary = "生成 CRUD 代码并写入文件")
    @PostMapping("/generate")
    public Result<List<GeneratedFile>> generate(@Valid @RequestBody EntityDefDTO def) {
        return Result.success(codegenService.generate(def));
    }

    @Operation(summary = "列出可用模板")
    @GetMapping("/templates")
    public Result<List<String>> templates() {
        return Result.success(codegenService.listTemplates());
    }

    @Operation(summary = "预览生成结果（不写入文件）")
    @PostMapping("/preview")
    public Result<List<GeneratedFile>> preview(@Valid @RequestBody EntityDefDTO def) {
        return Result.success(codegenService.preview(def));
    }
}
