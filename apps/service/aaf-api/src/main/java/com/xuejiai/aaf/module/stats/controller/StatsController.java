package com.xuejiai.aaf.module.stats.controller;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.enums.stats.ReportTypeEnum;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.stats.service.BehaviorService;
import com.xuejiai.aaf.module.stats.service.ReportService;
import com.xuejiai.aaf.module.stats.service.StatsService;
import com.xuejiai.aaf.module.stats.vo.FunnelVO;
import com.xuejiai.aaf.module.stats.vo.RetentionVO;
import com.xuejiai.aaf.module.stats.vo.TrendQueryDTO;
import com.xuejiai.aaf.module.stats.vo.TrendSeriesVO;
import com.xuejiai.aaf.module.stats.vo.UserEventBatchDTO;
import com.xuejiai.aaf.module.stats.vo.UserProfileVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 运营统计接口。
 *
 * <p>提供趋势统计、行为分析、报表导出三大能力。
 */
@Tag(name = "运营统计")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final BehaviorService behaviorService;
    private final ReportService reportService;

    // ========== 趋势统计 ==========

    @Operation(summary = "查询趋势数据")
    @GetMapping("/trend")
    public Result<TrendSeriesVO> queryTrend(@Validated TrendQueryDTO query) {
        return Result.success(statsService.queryTrend(query));
    }

    @Operation(summary = "查询趋势数据（含环比）")
    @GetMapping("/trend/comparison")
    public Result<TrendSeriesVO> queryTrendWithComparison(@Validated TrendQueryDTO query) {
        return Result.success(statsService.queryTrendWithComparison(query));
    }

    // ========== 行为分析 ==========

    @Operation(summary = "批量上报行为事件")
    @PostMapping("/events")
    public Result<Void> trackEvents(@Validated @RequestBody UserEventBatchDTO dto) {
        behaviorService.trackEvents(dto);
        return Result.success();
    }

    @Operation(summary = "漏斗分析")
    @GetMapping("/funnel")
    public Result<FunnelVO> queryFunnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(7);
        if (endDate == null) endDate = LocalDate.now();
        return Result.success(behaviorService.queryFunnel(startDate, endDate));
    }

    @Operation(summary = "留存分析")
    @GetMapping("/retention")
    public Result<RetentionVO> queryRetention(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate baseDate) {
        if (baseDate == null) baseDate = LocalDate.now();
        return Result.success(behaviorService.queryRetention(baseDate));
    }

    @Operation(summary = "用户画像")
    @GetMapping("/profile")
    public Result<UserProfileVO> queryUserProfile(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(behaviorService.queryUserProfile(startDate, endDate));
    }

    // ========== 报表导出 ==========

    @Operation(summary = "导出 CSV 报表")
    @GetMapping("/report/csv")
    public void exportCsv(
            @RequestParam ReportTypeEnum type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            HttpServletResponse response)
            throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=report_%s_%s.csv".formatted(type.getCode(), reportDate));
        reportService.exportCsv(type, reportDate, response.getOutputStream());
    }

    @Operation(summary = "导出 PDF 报表（骨架）")
    @GetMapping("/report/pdf")
    public void exportPdf(
            @RequestParam ReportTypeEnum type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            HttpServletResponse response)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=report_%s_%s.pdf".formatted(type.getCode(), reportDate));
        reportService.exportPdf(type, reportDate, response.getOutputStream());
    }
}
