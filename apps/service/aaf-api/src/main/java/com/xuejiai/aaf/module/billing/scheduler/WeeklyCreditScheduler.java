package com.xuejiai.aaf.module.billing.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.module.billing.service.CreditGrantService;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 每周积分发放定时任务。
 *
 * <p>每周一 00:01 为所有有积分账户的用户发放每周积分（规则 code=WEEKLY）。
 * 可通过系统配置 {@code member.weekly_grant_enabled} 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyCreditScheduler {

    private final CreditAccountRepository accountRepository;
    private final CreditGrantService creditGrantService;
    private final SystemConfigService systemConfigService;

    @Scheduled(cron = "0 1 0 * * MON")
    @Transactional
    public void issueWeeklyCredits() {
        if (!systemConfigService.getBoolean(SysConfigKeys.Member.WEEKLY_GRANT_ENABLED, true)) {
            log.info("每周积分发放已关闭（{}=false）", SysConfigKeys.Member.WEEKLY_GRANT_ENABLED);
            return;
        }

        List<Long> userIds = accountRepository.findAll().stream()
                .map(a -> a.getUserId())
                .toList();

        int issued = 0;
        for (Long userId : userIds) {
            long amount = creditGrantService.grant(userId, "WEEKLY", "WEEKLY_" + userId);
            if (amount > 0) issued++;
        }
        log.info("每周积分发放完成，共发放 {} 个用户", issued);
    }
}
