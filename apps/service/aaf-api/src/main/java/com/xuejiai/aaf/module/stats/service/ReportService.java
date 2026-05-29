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
import com.xuejiai.aaf.module.stats.vo.TrendPointVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报表服务。
 *
 * <p>支持日报/周报/月报生成，CSV 导出，PDF 导出骨架。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final StatsService statsService;
    private final JdbcTemplate jdbcTemplate;

    /** 核心指标列表 */
    private static final List<String> METRICS = List.of("dau", "messages", "tokens", "revenue");

    /**
     * 生成报表数据（供定时任务调用）。
     */
    public Map<String, List<TrendPointVO>> generateReport(ReportTypeEnum type, LocalDate reportDate) {
        var range = calcDateRange(type, reportDate);
        var period = type == ReportTypeEnum.DAILY ? StatPeriodEnum.HOUR : StatPeriodEnum.DAY;

        return METRICS.stream().collect(
                java.util.stream.Collectors.toMap(
                        metric -> metric,
                        metric -> statsService.queryTrendPoints(metric, period, range[0], range[1])
                )
        );
    }

    /**
     * CSV 导出：写入指定输出流。
     */
    public void exportCsv(ReportTypeEnum type, LocalDate reportDate, OutputStream out) throws IOException {
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
     * PDF 导出骨架（预留接口，内部待接入 iText）。
     */
    public void exportPdf(ReportTypeEnum type, LocalDate reportDate, OutputStream out) throws IOException {
        // TODO: 接入 iText 8 生成 PDF 报表
        var data = generateReport(type, reportDate);
        log.info("PDF 导出骨架调用，报表类型={}，日期={}，指标数={}", type, reportDate, data.size());
        out.write(("PDF 报表生成待实现 - %s %s".formatted(type.getLabel(), reportDate)).getBytes());
    }

    // ========== 内部方法 ==========

    private LocalDate[] calcDateRange(ReportTypeEnum type, LocalDate reportDate) {
        return switch (type) {
            case DAILY -> new LocalDate[]{reportDate, reportDate};
            case WEEKLY -> new LocalDate[]{reportDate.minusDays(6), reportDate};
            case MONTHLY -> new LocalDate[]{reportDate.withDayOfMonth(1), reportDate};
        };
    }
}
