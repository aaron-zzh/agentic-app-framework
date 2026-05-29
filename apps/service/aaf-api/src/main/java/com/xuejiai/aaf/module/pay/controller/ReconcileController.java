package com.xuejiai.aaf.module.pay.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.pay.service.ReconcileService;
import com.xuejiai.aaf.module.pay.vo.FinanceSummaryVO;
import com.xuejiai.aaf.module.pay.vo.ReconcileRecordVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 对账接口 */
@Tag(name = "对账管理")
@RestController
@RequestMapping("/api/pay/reconcile")
@RequiredArgsConstructor
public class ReconcileController {

    private final ReconcileService reconcileService;

    @Operation(summary = "执行对账")
    @PostMapping
    public Result<ReconcileRecordVO> reconcile(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String channelCode) {
        return Result.success(reconcileService.reconcile(date, channelCode));
    }

    @Operation(summary = "查询对账日报")
    @GetMapping
    public Result<List<ReconcileRecordVO>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.success(reconcileService.listByDateRange(start, end));
    }

    @Operation(summary = "财务统计汇总")
    @GetMapping("/summary")
    public Result<FinanceSummaryVO> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.success(reconcileService.financeSummary(start, end));
    }
}
