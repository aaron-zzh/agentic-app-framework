package com.xuejiai.aaf.module.system.log.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.log.service.AuditLogService;
import com.xuejiai.aaf.module.system.log.vo.AuditLogPageDTO;
import com.xuejiai.aaf.module.system.log.vo.AuditLogVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 审计日志接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页查询审计日志")
    @GetMapping
    public Result<PageResult<AuditLogVO>> page(
            @Validated @ParameterObject AuditLogPageDTO request) {
        return Result.success(auditLogService.page(request));
    }
}
