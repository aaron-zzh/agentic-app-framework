package com.xuejiai.aaf.module.stats.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.stats.domain.UserEvent;
import com.xuejiai.aaf.module.stats.repository.UserEventRepository;
import com.xuejiai.aaf.module.stats.vo.FunnelVO;
import com.xuejiai.aaf.module.stats.vo.RetentionVO;
import com.xuejiai.aaf.module.stats.vo.UserEventBatchDTO;
import com.xuejiai.aaf.module.stats.vo.UserProfileVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户行为分析服务。
 *
 * <p>漏斗分析、留存分析、用户画像。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorService {

    private final UserEventRepository userEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final OperatorContext operatorContext;

    /** 批量采集行为事件。 */
    @Transactional
    public void trackEvents(UserEventBatchDTO dto) {
        var userId = operatorContext.currentUserId().orElseThrow();
        var now = LocalDateTime.now();
        var events =
                dto.events().stream()
                        .map(
                                item -> {
                                    var event = new UserEvent();
                                    event.setUserId(userId);
                                    event.setEventType(item.eventType());
                                    event.setPage(item.page());
                                    event.setTarget(item.target());
                                    event.setExtra(item.extra());
                                    event.setCreateTime(now);
                                    return event;
                                })
                        .toList();
        userEventRepository.saveAll(events);
    }

    /** 漏斗分析：注册→激活→付费。 */
    public FunnelVO queryFunnel(LocalDate start, LocalDate end) {
        var startTime = start.atStartOfDay();
        var endTime = end.atTime(LocalTime.MAX);

        // 各阶段独立统计去重用户数
        long registered =
                userEventRepository.countDistinctUserByEventType("register", startTime, endTime);
        long activated =
                userEventRepository.countDistinctUserByEventType("activate", startTime, endTime);
        long purchased =
                userEventRepository.countDistinctUserByEventType("purchase", startTime, endTime);

        var steps = new ArrayList<FunnelVO.Step>();
        steps.add(new FunnelVO.Step("注册", registered, null));
        steps.add(
                new FunnelVO.Step(
                        "激活", activated, registered > 0 ? (double) activated / registered : 0.0));
        steps.add(
                new FunnelVO.Step(
                        "付费", purchased, activated > 0 ? (double) purchased / activated : 0.0));
        return new FunnelVO(steps);
    }

    /** 留存分析：次日/7日/30日。 */
    public RetentionVO queryRetention(LocalDate baseDate) {
        var points = new ArrayList<RetentionVO.RetentionPoint>();
        for (int day : List.of(1, 7, 30)) {
            var result = calcRetention(baseDate, day);
            points.add(result);
        }
        return new RetentionVO(points);
    }

    /** 用户画像聚合。 */
    public UserProfileVO queryUserProfile(LocalDate start, LocalDate end) {
        var startTime = start.atStartOfDay();
        var endTime = end.atTime(LocalTime.MAX);

        // 活跃度分布：按事件数分高/中/低
        var activitySql =
                """
                SELECT CASE
                    WHEN cnt >= 50 THEN '高'
                    WHEN cnt >= 10 THEN '中'
                    ELSE '低'
                END AS level, COUNT(*) AS user_count
                FROM (SELECT user_id, COUNT(*) AS cnt FROM user_event
                      WHERE create_time BETWEEN ? AND ? GROUP BY user_id) sub
                GROUP BY level
                """;
        Map<String, Long> activityDist = new LinkedHashMap<>();
        jdbcTemplate.query(
                activitySql,
                (rs, rowNum) -> {
                    activityDist.put(rs.getString("level"), rs.getLong("user_count"));
                    return null;
                },
                startTime,
                endTime);

        // 偏好功能 TOP 10
        var featureSql =
                """
                SELECT COALESCE(page, target) AS feature, COUNT(*) AS cnt
                FROM user_event WHERE create_time BETWEEN ? AND ?
                GROUP BY feature ORDER BY cnt DESC LIMIT 10
                """;
        var topFeatures =
                jdbcTemplate.query(
                        featureSql,
                        (rs, rowNum) ->
                                new UserProfileVO.FeatureUsage(
                                        rs.getString("feature"), rs.getLong("cnt")),
                        startTime,
                        endTime);

        // 使用时段分布
        var hourlySql =
                """
                SELECT EXTRACT(HOUR FROM create_time)::int AS hour, COUNT(*) AS cnt
                FROM user_event WHERE create_time BETWEEN ? AND ?
                GROUP BY hour ORDER BY hour
                """;
        Map<Integer, Long> hourlyDist = new LinkedHashMap<>();
        jdbcTemplate.query(
                hourlySql,
                (rs, rowNum) -> {
                    hourlyDist.put(rs.getInt("hour"), rs.getLong("cnt"));
                    return null;
                },
                startTime,
                endTime);

        return new UserProfileVO(activityDist, topFeatures, hourlyDist);
    }

    // ========== 内部方法 ==========

    private RetentionVO.RetentionPoint calcRetention(LocalDate baseDate, int day) {
        // 基准日新增用户
        var baseSql =
                """
                SELECT COUNT(DISTINCT user_id) FROM user_event
                WHERE event_type = 'register' AND create_time::date = ?
                """;
        var base = jdbcTemplate.queryForObject(baseSql, Long.class, baseDate);
        if (base == null) base = 0L;

        // 第 N 日回访用户
        var retainDate = baseDate.plusDays(day);
        var retainSql =
                """
                SELECT COUNT(DISTINCT e.user_id) FROM user_event e
                WHERE e.create_time::date = ?
                AND e.user_id IN (
                    SELECT DISTINCT user_id FROM user_event
                    WHERE event_type = 'register' AND create_time::date = ?
                )
                """;
        var retained = jdbcTemplate.queryForObject(retainSql, Long.class, retainDate, baseDate);
        if (retained == null) retained = 0L;

        double rate = base > 0 ? (double) retained / base : 0.0;
        return new RetentionVO.RetentionPoint(day, rate, retained, base);
    }
}
