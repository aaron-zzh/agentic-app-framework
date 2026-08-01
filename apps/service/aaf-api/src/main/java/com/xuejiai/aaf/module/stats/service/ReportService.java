package com.xuejiai.aaf.module.stats.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.stats.ReportTypeEnum;
import com.xuejiai.aaf.common.enums.stats.StatPeriodEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.stats.vo.TrendPointVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报表服务。
 *
 * <p>支持日报/周报/月报生成与 CSV 导出；PDF 导出尚未实现（调用即显式报错，不静默降级）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final StatsService statsService;
    private final JdbcTemplate jdbcTemplate;

    /** 核心指标列表 */
    private static final List<String> METRICS = List.of("dau", "messages", "tokens", "revenue");

    /** 生成报表数据（供定时任务调用）。 */
    public Map<String, List<TrendPointVO>> generateReport(
            ReportTypeEnum type, LocalDate reportDate) {
        var range = calcDateRange(type, reportDate);
        var period = type == ReportTypeEnum.DAILY ? StatPeriodEnum.HOUR : StatPeriodEnum.DAY;

        return METRICS.stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                metric -> metric,
                                metric ->
                                        statsService.queryTrendPoints(
                                                metric, period, range[0], range[1], null)));
    }

    /** CSV 导出：写入指定输出流。 */
    public void exportCsv(ReportTypeEnum type, LocalDate reportDate, OutputStream out)
            throws IOException {
        var data = generateReport(type, reportDate);
        try (var writer = new PrintWriter(out)) {
            // 表头
            writer.println("指标,时间,数值");
            // 数据行
            for (var entry : data.entrySet()) {
                var metric = entry.getKey();
                for (var point : entry.getValue()) {
                    writer.printf("%s,%s,%d%n", metric, point.time(), point.value());
                }
            }
        }
    }

    /**
     * PDF 导出——尚未实现。
     *
     * <p>占位修复：原实现向响应流写入 "PDF 报表生成待实现" 纯文本，但 Content-Type 已声明为 application/pdf，调用方会拿到一个"下载成功但打不开"的假
     * PDF，属静默降级。 未实现的能力必须显式失败，接入 iText 后再放开。
     */
    public void exportPdf(ReportTypeEnum type, LocalDate reportDate, OutputStream out)
            throws IOException {
        throw new BusinessException(GlobalErrorCode.SERVICE_UNAVAILABLE, "PDF 报表导出尚未实现，请改用 CSV 导出");
    }

    // ========== 内部方法 ==========

    private LocalDate[] calcDateRange(ReportTypeEnum type, LocalDate reportDate) {
        return switch (type) {
            case DAILY -> new LocalDate[] {reportDate, reportDate};
            case WEEKLY -> new LocalDate[] {reportDate.minusDays(6), reportDate};
            case MONTHLY -> new LocalDate[] {reportDate.withDayOfMonth(1), reportDate};
        };
    }
}
