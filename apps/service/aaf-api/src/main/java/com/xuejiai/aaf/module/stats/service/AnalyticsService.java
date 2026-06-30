package com.xuejiai.aaf.module.stats.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.stats.domain.CreditSpendRanking;
import com.xuejiai.aaf.module.stats.domain.UserContributionStats;
import com.xuejiai.aaf.module.stats.repository.CreditSpendRankingRepository;
import com.xuejiai.aaf.module.stats.repository.UserContributionStatsRepository;
import com.xuejiai.aaf.module.stats.vo.CreditRecordVO;
import com.xuejiai.aaf.module.stats.vo.CreditsCategoryVO;
import com.xuejiai.aaf.module.stats.vo.CreditsOverviewVO;

import lombok.RequiredArgsConstructor;

/**
 * 分析统计服务——封装视图查询（只读），供 Controller 和 Dashboard Widget 调用。
 *
 * <p>动态过滤指标（工具/技能/工作流/每日趋势）由 StatsService 的 JdbcTemplate 负责； 本类负责结构固定、需要分页的视图查询。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final CreditSpendRankingRepository creditSpendRankingRepository;
    private final UserContributionStatsRepository userContributionStatsRepository;
    private final JdbcTemplate jdbcTemplate;

    /** 积分消耗排行 TOP N */
    public List<CreditSpendRanking> creditSpendRanking(int limit) {
        return creditSpendRankingRepository
                .findAll(PageRequest.of(0, limit, Sort.by("totalSpentCredits").descending()))
                .getContent();
    }

    /** 用户贡献量排行（按创作任务数降序）TOP N */
    public List<UserContributionStats> contributionRanking(int limit) {
        return userContributionStatsRepository
                .findAll(PageRequest.of(0, limit, Sort.by("totalAigcTasks").descending()))
                .getContent();
    }

    /** 查询单个用户的贡献统计 */
    public UserContributionStats userContribution(Long userId) {
        return userContributionStatsRepository.findById(userId).orElse(null);
    }

    /** 用户贡献量分页（管理员用） */
    public PageResult<UserContributionStats> contributionPage(
            int pageNo, int pageSize, String sortBy) {
        var sort =
                switch (sortBy != null ? sortBy : "totalAigcTasks") {
                    case "creditBalance" -> Sort.by("creditBalance").descending();
                    case "totalSpentCredits" -> Sort.by("totalSpentCredits").descending();
                    case "totalMediaAssets" -> Sort.by("totalMediaAssets").descending();
                    default -> Sort.by("totalAigcTasks").descending();
                };
        Page<UserContributionStats> page =
                userContributionStatsRepository.findAll(PageRequest.of(pageNo - 1, pageSize, sort));
        return new PageResult<>(page.getContent(), page.getTotalElements());
    }

    // ========== 积分消耗统计（credits-analytics 仪表盘） ==========

    /**
     * 积分消耗统计概览：余额 / 本月消耗 / 本月充值 / 环比变化率。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>余额：所有 credit_account.balance 之和
     *   <li>本月/上月消耗：credit_transaction type=SPEND，按 create_time 月份分组
     *   <li>本月/上月充值：credit_transaction type=EARN，按 create_time 月份分组
     * </ul>
     */
    public CreditsOverviewVO creditsOverview() {
        // 余额汇总
        Long balance =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(balance), 0) FROM credit_account WHERE deleted = FALSE",
                        Long.class);

        // 本月 & 上月消耗/充值
        var row =
                jdbcTemplate.queryForMap(
                        """
                SELECT
                    COALESCE(SUM(CASE WHEN type='SPEND'
                        AND DATE_TRUNC('month', create_time) = DATE_TRUNC('month', NOW())
                        THEN amount ELSE 0 END), 0) AS month_consumed,
                    COALESCE(SUM(CASE WHEN type='SPEND'
                        AND DATE_TRUNC('month', create_time) = DATE_TRUNC('month', NOW() - INTERVAL '1 month')
                        THEN amount ELSE 0 END), 0) AS last_month_consumed,
                    COALESCE(SUM(CASE WHEN type='EARN'
                        AND DATE_TRUNC('month', create_time) = DATE_TRUNC('month', NOW())
                        THEN amount ELSE 0 END), 0) AS month_recharged,
                    COALESCE(SUM(CASE WHEN type='EARN'
                        AND DATE_TRUNC('month', create_time) = DATE_TRUNC('month', NOW() - INTERVAL '1 month')
                        THEN amount ELSE 0 END), 0) AS last_month_recharged
                FROM credit_transaction WHERE deleted = FALSE
                """);

        long monthConsumed = ((Number) row.get("month_consumed")).longValue();
        long lastMonthConsumed = ((Number) row.get("last_month_consumed")).longValue();
        long monthRecharged = ((Number) row.get("month_recharged")).longValue();
        long lastMonthRecharged = ((Number) row.get("last_month_recharged")).longValue();

        double consumedRate =
                lastMonthConsumed == 0
                        ? 0
                        : Math.round(
                                        (monthConsumed - lastMonthConsumed)
                                                * 1000.0
                                                / lastMonthConsumed)
                                / 10.0;
        double rechargedRate =
                lastMonthRecharged == 0
                        ? 0
                        : Math.round(
                                        (monthRecharged - lastMonthRecharged)
                                                * 1000.0
                                                / lastMonthRecharged)
                                / 10.0;

        return new CreditsOverviewVO(
                balance == null ? 0 : balance,
                monthConsumed,
                monthRecharged,
                consumedRate,
                rechargedRate);
    }

    /**
     * 积分消耗按分类分布（饼图数据）。
     *
     * <p>按 credit_transaction.category 分组汇总 SPEND 类流水，category 为 NULL 时归为"其他"。
     */
    public CreditsCategoryVO creditsByCategory() {
        var items =
                jdbcTemplate.query(
                        """
                SELECT COALESCE(category, '其他') AS name,
                       SUM(amount) AS value
                FROM credit_transaction
                WHERE type = 'SPEND' AND deleted = FALSE
                GROUP BY COALESCE(category, '其他')
                ORDER BY value DESC
                """,
                        (rs, rowNum) ->
                                new CreditsCategoryVO.Item(
                                        rs.getString("name"), rs.getLong("value")));

        long total = items.stream().mapToLong(CreditsCategoryVO.Item::value).sum();
        return new CreditsCategoryVO(items, total);
    }

    /**
     * 积分流水分页（管理员视角，含用户信息和脱敏手机号）。
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页条数
     * @param type 流水类型过滤，null 表示全部
     */
    public PageResult<CreditRecordVO> creditsRecords(int pageNo, int pageSize, String type) {
        String typeFilter = type != null ? " AND ct.type = '" + type + "'" : "";
        String countSql =
                """
                SELECT COUNT(*)
                FROM credit_transaction ct
                JOIN credit_account ca ON ca.id = ct.account_id AND ca.deleted = FALSE
                WHERE ct.deleted = FALSE
                """
                        + typeFilter;

        String dataSql =
                """
                SELECT ct.id, u.nickname AS user_name,
                       CASE WHEN u.phone IS NOT NULL
                            THEN CONCAT(LEFT(u.phone, 3), '****', RIGHT(u.phone, 4))
                            ELSE NULL END AS phone,
                       ct.type, ct.category, ct.amount, ct.balance_after,
                       ct.source, ct.remark, ct.create_time
                FROM credit_transaction ct
                JOIN credit_account ca ON ca.id = ct.account_id AND ca.deleted = FALSE
                LEFT JOIN sys_user u ON u.id = ca.user_id AND u.deleted = FALSE
                WHERE ct.deleted = FALSE
                """
                        + typeFilter
                        + """
                ORDER BY ct.create_time DESC
                LIMIT ? OFFSET ?
                """;

        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        int offset = (pageNo - 1) * pageSize;
        var records =
                jdbcTemplate.query(
                        dataSql,
                        (rs, rowNum) ->
                                new CreditRecordVO(
                                        rs.getLong("id"),
                                        rs.getString("user_name"),
                                        rs.getString("phone"),
                                        rs.getString("type"),
                                        rs.getString("category"),
                                        rs.getLong("amount"),
                                        rs.getLong("balance_after"),
                                        rs.getString("source"),
                                        rs.getString("remark"),
                                        rs.getObject("create_time", java.time.LocalDateTime.class)),
                        pageSize,
                        offset);

        return new PageResult<>(records, total == null ? 0 : total);
    }
}
