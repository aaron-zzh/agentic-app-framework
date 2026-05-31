package com.xuejiai.aaf.module.billing.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.credit.CreditTransaction;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.domain.EntitlementLedger;
import com.xuejiai.aaf.module.billing.service.BillingQueryService;
import com.xuejiai.aaf.module.billing.vo.BillingSummaryVO;

import lombok.RequiredArgsConstructor;

/** 账单查询接口 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingQueryService billingQueryService;
    private final OperatorContext operatorContext;

    /** 积分流水 */
    @GetMapping("/credit-transactions")
    public Result<Page<CreditTransaction>> creditTransactions(
            @RequestParam(required = false) Long userId, Pageable pageable) {
        return Result.success(billingQueryService.getCreditTransactions(ownerId(userId), pageable));
    }

    /** 权益消费流水 */
    @GetMapping("/entitlement-ledger")
    public Result<Page<EntitlementLedger>> entitlementLedger(
            @RequestParam(required = false) Long userId, Pageable pageable) {
        return Result.success(billingQueryService.getEntitlementLedger(ownerId(userId), pageable));
    }

    /** 账单汇总 */
    @GetMapping("/summary")
    public Result<BillingSummaryVO> summary(
            @RequestParam(required = false) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(billingQueryService.getSummary(ownerId(userId), startDate, endDate));
    }

    /** 导出 CSV */
    @GetMapping("/export-csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        var csv = billingQueryService.exportCsv(ownerId(userId), startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=billing.csv")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(csv.getBytes());
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
