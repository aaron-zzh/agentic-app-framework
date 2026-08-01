package com.xuejiai.aaf.module.billing.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.service.BillingQueryService;
import com.xuejiai.aaf.module.billing.vo.BillingSummaryVO;

import lombok.RequiredArgsConstructor;

/** 账单汇总与导出接口。 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class BillingController {

    private final BillingQueryService billingQueryService;
    private final OperatorContext operatorContext;

    @GetMapping("/summary")
    public Result<BillingSummaryVO> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(billingQueryService.getSummary(currentOwnerId(), startDate, endDate));
    }

    @GetMapping("/export-csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        var csv = billingQueryService.exportCsv(currentOwnerId(), startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=billing.csv")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(csv.getBytes());
    }

    /**
     * M1：当前身份来源唯一——只从 OperatorContext 取，取不到即 401。
     *
     * <p>原实现是 currentOwnerId().orElse(客户端传入的 userId)：一旦认证上下文解析不出归属者
     * （如 API Key 认证未绑定用户），就会采信请求参数里的 userId，形成任意用户数据读取。
     * 管理员代查须走带显式鉴权的管理端接口，不复用本接口。
     */
    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(
                        () ->
                                new com.xuejiai.aaf.common.exception.BusinessException(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED));
    }
}
