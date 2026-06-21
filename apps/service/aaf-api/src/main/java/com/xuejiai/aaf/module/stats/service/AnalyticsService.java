package com.xuejiai.aaf.module.stats.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.stats.domain.CreditSpendRanking;
import com.xuejiai.aaf.module.stats.domain.UserContributionStats;
import com.xuejiai.aaf.module.stats.repository.CreditSpendRankingRepository;
import com.xuejiai.aaf.module.stats.repository.UserContributionStatsRepository;

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
}
