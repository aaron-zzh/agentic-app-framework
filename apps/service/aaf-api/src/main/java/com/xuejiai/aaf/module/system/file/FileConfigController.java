package com.xuejiai.aaf.module.system.file;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;
import com.xuejiai.aaf.module.system.file.service.FileConfigService;
import com.xuejiai.aaf.module.system.file.vo.FileConfigActionDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigCreateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigPageDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigUpdateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 文件存储配置管理接口。 */
@Tag(name = "文件存储配置")
@RestController
@RequestMapping("/api/system/file-configs")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FileConfigController
        extends BaseCrudController<
                FileConfig,
                FileConfigVO,
                FileConfigCreateDTO,
                FileConfigUpdateDTO,
                FileConfigPageDTO> {

    private final FileConfigService service;

    @Override
    protected FileConfigService getService() {
        return service;
    }

    @Override
    @Operation(summary = "创建文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<FileConfigVO> create(@Valid @RequestBody FileConfigCreateDTO request) {
        return super.create(request);
    }

    @Override
    @Operation(summary = "更新文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}")
    public Result<FileConfigVO> update(
            @PathVariable Long id, @Valid @RequestBody FileConfigUpdateDTO request) {
        return super.update(id, request);
    }

    @Override
    @Operation(summary = "删除文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return super.delete(id);
    }

    @Operation(summary = "设为主配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/actions/set-master")
    public Result<FileConfigVO> setMaster(@Valid @RequestBody FileConfigActionDTO command) {
        return Result.success(service.setMaster(command.id()));
    }

    @Operation(summary = "退役文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/actions/retire")
    public Result<FileConfigVO> retire(@Valid @RequestBody FileConfigActionDTO command) {
        return Result.success(service.retire(command.id()));
    }

    @Operation(summary = "验证文件存储配置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/actions/validate")
    public Result<Void> validate(@Valid @RequestBody FileConfigActionDTO command) {
        service.test(command.id());
        return Result.success();
    }
}
