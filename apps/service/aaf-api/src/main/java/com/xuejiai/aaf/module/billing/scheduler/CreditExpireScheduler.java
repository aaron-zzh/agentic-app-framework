package com.xuejiai.aaf.module.billing.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransaction;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionType;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 积分过期清理定时任务。
 *
 * <p>每日凌晨 00:10 扫描已过期且仍有剩余量的批次，将 remain 清零并同步扣减账户余额。
 * 可通过系统配置 {@code member.credit_expire_enabled} 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditExpireScheduler {

    private final CreditTransactionRepository transactionRepository;
    private final CreditAccountRepository accountRepository;
    private final SystemConfigService systemConfigService;

    @Scheduled(cron = "0 10 0 * * *")
    @Transactional
    public void expireCredits() {
        if (!systemConfigService.getBoolean(SysConfigKeys.Member.CREDIT_EXPIRE_ENABLED, true)) {
            log.info("积分过期清理已关闭（{}=false）", SysConfigKeys.Member.CREDIT_EXPIRE_ENABLED);
            return;
        }

        var now = LocalDateTime.now();
        var expiredBatches = transactionRepository.findExpiredBatches(now);

        int count = 0;
        for (var batch : expiredBatches) {
            long expiredAmount = batch.getRemain();
            if (expiredAmount <= 0) continue;

            var accountOpt = accountRepository.findById(batch.getAccountId());
            if (accountOpt.isEmpty()) continue;
            var account = accountOpt.get();

            long deduct = expiredAmount;
            account.setBalance(account.getBalance() - deduct);
            accountRepository.save(account);

            var tx = new CreditTransaction();
            tx.setAccountId(account.getId());
            tx.setType(CreditTransactionType.EXPIRE);
            tx.setAmount(deduct);
            tx.setBalanceAfter(account.getBalance());
            tx.setSource("EXPIRE");
            tx.setBizId(String.valueOf(batch.getId()));
            tx.setRemain(0L);
            transactionRepository.save(tx);

            batch.setRemain(0L);
            transactionRepository.save(batch);
            count++;
        }
        log.info("积分过期清理完成，处理 {} 个批次", count);
    }
}
