package com.xuejiai.aaf.module.stats.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.stats.StatPeriodEnum;
import com.xuejiai.aaf.framework.engine.monitor.MonitorEngine;
import com.xuejiai.aaf.module.stats.vo.TrendPointVO;
import com.xuejiai.aaf.module.stats.vo.TrendQueryDTO;
import com.xuejiai.aaf.module.stats.vo.TrendSeriesVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 趋势统计服务。
 *
 * <p>核心指标：DAU/MAU、消息量、Token 消耗、收入。
 * 使用原生 SQL + date_trunc 聚合，避免全量加载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final JdbcTemplate jdbcTemplate;
    private final MonitorEngine monitorEngine;

    /** 异常检测阈值：环比增长超过此倍数触发告警 */
    private static final double ALERT_THRESHOLD = 3.0;

    /**
     * 查询趋势数据（ECharts 格式）。
     */
    public TrendSeriesVO queryTrend(TrendQueryDTO query) {
        var points = queryTrendPoints(query.metric(), query.period(), query.startDate(), query.endDate());
        var categories = points.stream().map(TrendPointVO::time).toList();
        var data = points.stream().map(TrendPointVO::value).toList();

        // 异常检测：最后一个点与前一个点比较
        detectAnomaly(query.metric(), points);

        return new TrendSeriesVO(categories, List.of(new TrendSeriesVO.Series(query.metric(), data)));
    }

    /**
     * 查询趋势数据点列表。
     */
    public List<TrendPointVO> queryTrendPoints(String metric, StatPeriodEnum period, LocalDate start, LocalDate end) {
        var startTime = start.atStartOfDay();
        var endTime = end.atTime(LocalTime.MAX);
        var trunc = period.toDateTrunc();

        var sql = buildMetricSql(metric, trunc);
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new TrendPointVO(rs.getString("time_bucket"), rs.getLong("value")),
                startTime, endTime
        );
    }

    /**
     * 查询同比/环比数据。
     */
    public TrendSeriesVO queryTrendWithComparison(TrendQueryDTO query) {
        var current = queryTrendPoints(query.metric(), query.period(), query.startDate(), query.endDate());

        // 计算环比区间（同等长度的前一段）
        long days = query.endDate().toEpochDay() - query.startDate().toEpochDay();
        var prevStart = query.startDate().minusDays(days + 1);
        var prevEnd = query.startDate().minusDays(1);
        var previous = queryTrendPoints(query.metric(), query.period(), prevStart, prevEnd);

        var categories = current.stream().map(TrendPointVO::time).toList();
        return new TrendSeriesVO(categories, List.of(
                new TrendSeriesVO.Series("当期", current.stream().map(TrendPointVO::value).toList()),
                new TrendSeriesVO.Series("环比", previous.stream().map(TrendPointVO::value).toList())
        ));
    }

    // ========== 内部方法 ==========

    private String buildMetricSql(String metric, String trunc) {
        return switch (metric) {
            case "dau" -> """
                    SELECT date_trunc('%s', last_login_time) AS time_bucket, COUNT(DISTINCT id) AS value
                    FROM sys_user WHERE deleted = false AND last_login_time BETWEEN ? AND ?
                    GROUP BY time_bucket ORDER BY time_bucket
                    """.formatted(trunc);
            case "mau" -> """
                    SELECT date_trunc('month', last_login_time) AS time_bucket, COUNT(DISTINCT id) AS value
                    FROM sys_user WHERE deleted = false AND last_login_time BETWEEN ? AND ?
                    GROUP BY time_bucket ORDER BY time_bucket
                    """;
            case "messages" -> """
                    SELECT date_trunc('%s', create_time) AS time_bucket, COUNT(*) AS value
                    FROM chat_message WHERE deleted = false AND create_time BETWEEN ? AND ?
                    GROUP BY time_bucket ORDER BY time_bucket
                    """.formatted(trunc);
            case "tokens" -> """
                    SELECT date_trunc('%s', created_at) AS time_bucket, COALESCE(SUM(ABS(delta)), 0) AS value
                    FROM entitlement_ledger WHERE operation = 'USE' AND created_at BETWEEN ? AND ?
                    GROUP BY time_bucket ORDER BY time_bucket
                    """.formatted(trunc);
            case "revenue" -> """
                    SELECT date_trunc('%s', success_time) AS time_bucket, COALESCE(SUM(amount), 0) AS value
                    FROM pay_order WHERE status = 10 AND deleted = false AND success_time BETWEEN ? AND ?
                    GROUP BY time_bucket ORDER BY time_bucket
                    """.formatted(trunc);
            default -> throw new IllegalArgumentException("不支持的指标: " + metric);
        };
    }

    /** 简单异常检测：最后一个点与前一个点比较，超阈值告警 */
    private void detectAnomaly(String metric, List<TrendPointVO> points) {
        if (points.size() < 2) return;
        var last = points.getLast().value();
        var prev = points.get(points.size() - 2).value();
        if (prev > 0 && last > prev * ALERT_THRESHOLD) {
            monitorEngine.alert(MonitorEngine.AlertLevel.WARNING,
                    "指标突增告警",
                    "指标 [%s] 突增：%d → %d（%.1f倍）".formatted(metric, prev, last, (double) last / prev));
        }
        if (prev > 0 && last < prev / ALERT_THRESHOLD) {
            monitorEngine.alert(MonitorEngine.AlertLevel.WARNING,
                    "指标骤降告警",
                    "指标 [%s] 骤降：%d → %d".formatted(metric, prev, last));
        }
    }
}
