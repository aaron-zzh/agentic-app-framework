package com.xuejiai.aaf.module.system.user.controller;

import java.io.IOException;
import java.util.List;

import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.task.async.AsyncTaskService;
import com.xuejiai.aaf.module.system.user.service.UserService;
import com.xuejiai.aaf.module.system.user.vo.UserChangePasswordDTO;
import com.xuejiai.aaf.module.system.user.vo.UserCreateDTO;
import com.xuejiai.aaf.module.system.user.vo.UserExportVO;
import com.xuejiai.aaf.module.system.user.vo.UserImportVO;
import com.xuejiai.aaf.module.system.user.vo.UserPageDTO;
import com.xuejiai.aaf.module.system.user.vo.UserResetPasswordDTO;
import com.xuejiai.aaf.module.system.user.vo.UserSimpleVO;
import com.xuejiai.aaf.module.system.user.vo.UserUpdateDTO;
import com.xuejiai.aaf.module.system.user.vo.UserUpdateStatusDTO;
import com.xuejiai.aaf.module.system.user.vo.UserVO;
import com.xuejiai.aaf.util.ExcelUtils;
import com.xuejiai.aaf.util.ImportExecutor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 用户管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AsyncTaskService asyncTaskService;

    @Operation(summary = "获取用户精简列表", description = "下拉选择场景，不分页")
    @GetMapping("/simple")
    public Result<List<UserSimpleVO>> simpleList() {
        return Result.success(userService.getSimpleList());
    }

    @Operation(summary = "创建用户")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UserVO> create(@Validated @RequestBody UserCreateDTO request) {
        return Result.success(userService.create(request));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @Operation(summary = "分页查询用户")
    @GetMapping
    public Result<PageResult<UserVO>> page(@Validated @ParameterObject UserPageDTO request) {
        return Result.success(userService.page(request));
    }

    @Operation(summary = "更新用户")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}")
    public Result<UserVO> update(
            @PathVariable Long id, @Validated @RequestBody UserUpdateDTO request) {
        return Result.success(userService.update(id, request));
    }

    @Operation(summary = "删除用户")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除用户", description = "超过 100 条自动转异步，返回 taskId")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping
    public Result<?> deleteBatch(@RequestBody List<Long> ids) {
        if (ids.size() <= 100) {
            userService.deleteBatch(ids);
            return Result.success();
        }
        String taskId =
                asyncTaskService.submit(
                        "user:deleteBatch",
                        ids.size(),
                        task -> {
                            for (int i = 0; i < ids.size(); i++) {
                                try {
                                    userService.delete(ids.get(i));
                                } catch (Exception ignored) {
                                }
                                task.setCurrent(i + 1);
                            }
                        });
        return Result.success(java.util.Map.of("taskId", taskId, "async", true));
    }

    @Operation(summary = "修改用户状态", description = "启用/禁用")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id, @RequestBody UserUpdateStatusDTO request) {
        userService.updateStatus(id, request.status());
        return Result.success();
    }

    @Operation(summary = "修改密码", description = "用户自行修改，需提供旧密码")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/password")
    public Result<Void> changePassword(
            @PathVariable Long id, @Validated @RequestBody UserChangePasswordDTO request) {
        userService.changePassword(id, request);
        return Result.success();
    }

    @Operation(summary = "重置密码", description = "管理员操作，强制重置")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/password/reset")
    public Result<Void> resetPassword(
            @PathVariable Long id, @Validated @RequestBody UserResetPasswordDTO request) {
        userService.resetPassword(id, request.password());
        return Result.success();
    }

    @Operation(summary = "导入用户", description = "上传 Excel 文件批量导入用户")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/import")
    public Result<ImportExecutor.ImportResult> importUsers(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateSupport", defaultValue = "false") boolean updateSupport)
            throws IOException {
        var list = ExcelUtils.read(file, UserImportVO.class);
        return Result.success(userService.importUsers(list, updateSupport));
    }

    @Operation(summary = "导出用户列表", description = "支持 xlsx/csv 格式")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/export")
    public void export(
            @ParameterObject UserPageDTO request,
            @RequestParam(defaultValue = "xlsx") String format,
            HttpServletResponse response)
            throws IOException {
        var type = "csv".equalsIgnoreCase(format) ? ExcelTypeEnum.CSV : ExcelTypeEnum.XLSX;
        var data = userService.listForExport(request);
        ExcelUtils.write(response, "用户列表", "用户", UserExportVO.class, data, type);
    }
}
