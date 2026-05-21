package com.xuejiai.aaf.module.system.log.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.log.service.OperationLogService;
import com.xuejiai.aaf.module.system.log.vo.OperationLogPageDTO;
import com.xuejiai.aaf.module.system.log.vo.OperationLogVO;

import lombok.RequiredArgsConstructor;

/** 操作日志查询接口。 */
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /** 分页查询操作日志（支持按 module/type/userId/时间范围筛选）。 */
    @GetMapping
    public Result<PageResult<OperationLogVO>> page(OperationLogPageDTO req) {
        return Result.success(operationLogService.page(req));
    }
}
