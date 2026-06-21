package com.xuejiai.aaf.module.system.file;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.file.service.FileConfigService;
import com.xuejiai.aaf.module.system.file.vo.FileConfigCreateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigUpdateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 文件存储配置管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "文件存储配置")
@RestController
@RequestMapping("/api/system/file-configs")
@RequiredArgsConstructor
public class FileConfigController {

    private final FileConfigService fileConfigService;

    @Operation(summary = "查询所有文件存储配置")
    @GetMapping
    public Result<List<FileConfigVO>> list() {
        return Result.success(fileConfigService.list());
    }

    @Operation(summary = "获取配置详情")
    @GetMapping("/{id}")
    public Result<FileConfigVO> get(@PathVariable Long id) {
        return Result.success(fileConfigService.getById(id));
    }

    @Operation(summary = "创建文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    public Result<FileConfigVO> create(@Validated @RequestBody FileConfigCreateDTO req) {
        return Result.success(fileConfigService.create(req));
    }

    @Operation(summary = "更新文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}")
    public Result<FileConfigVO> update(
            @PathVariable Long id, @Validated @RequestBody FileConfigUpdateDTO req) {
        return Result.success(fileConfigService.update(id, req));
    }

    @Operation(summary = "删除文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileConfigService.delete(id);
        return Result.success();
    }

    @Operation(summary = "设为主配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/master")
    public Result<Void> setMaster(@PathVariable Long id) {
        fileConfigService.setMaster(id);
        return Result.success();
    }
}
