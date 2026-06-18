package com.xuejiai.aaf.framework.engine.credit.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.credit.CreditAccount;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.engine.credit.CreditTransaction;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionRepository;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 积分服务实现——基于 JPA 的真实记账。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditAccountRepository accountRepository;
    private final CreditTransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public long getBalance(Long userId) {
        return accountRepository.findByUserId(userId).map(CreditAccount::getBalance).orElse(0L);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBudget(Long userId, long estimatedCost) {
        return getBalance(userId) >= estimatedCost;
    }

    /** 充值积分有效期（天） */
    private static final int TOPUP_EXPIRE_DAYS = 365 * 2;

    @Override
    @Transactional
    public void earn(Long userId, long amount, String source, String bizId) {
        // 充值积分有效期 2 年
        earnBatch(
                userId,
                amount,
                "TOPUP",
                source,
                bizId,
                LocalDateTime.now().plusDays(TOPUP_EXPIRE_DAYS));
    }

    @Override
    @Transactional
    public void earnBatch(
            Long userId,
            long amount,
            String batchType,
            String source,
            String bizId,
            LocalDateTime expireAt) {
        if (amount <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "赚取金额必须大于 0");
        }
        var account = getOrCreateAccount(userId);
        account.setBalance(account.getBalance() + amount);
        account.setTotalEarned(account.getTotalEarned() + amount);
        accountRepository.save(account);

        var tx = new CreditTransaction();
        tx.setAccountId(account.getId());
        tx.setType(CreditTransactionType.EARN);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getBalance());
        tx.setSource(source);
        tx.setBizId(bizId);
        tx.setBatchType(batchType);
        tx.setExpireAt(expireAt);
        tx.setRemain(amount);
        transactionRepository.save(tx);

        log.info(
                "积分赚取: userId={}, amount={}, batchType={}, expireAt={}",
                userId,
                amount,
                batchType,
                expireAt);
    }

    /** 消费积分——按批次优先扣减（最快到期的批次优先，永久积分最后）。 */
    @Override
    @Transactional
    public void spend(Long userId, long amount, String source, String bizId) {
        spendInternal(userId, amount, source, null, bizId, 0L);
    }

    @Override
    @Transactional
    public void spend(Long userId, long amount, String source, String category, String bizId) {
        spendInternal(userId, amount, source, category, bizId, 0L);
    }

    @Override
    @Transactional
    public void spendAllowOverdraft(
            Long userId, long amount, String source, String bizId, long overdraftLimit) {
        spendInternal(userId, amount, source, null, bizId, overdraftLimit);
    }

    @Override
    @Transactional
    public void spendAllowOverdraft(
            Long userId,
            long amount,
            String source,
            String category,
            String bizId,
            long overdraftLimit) {
        spendInternal(userId, amount, source, category, bizId, overdraftLimit, null);
    }

    @Override
    @Transactional
    public void spendAllowOverdraft(
            Long userId,
            long amount,
            String source,
            String category,
            String bizId,
            long overdraftLimit,
            String remark) {
        spendInternal(userId, amount, source, category, bizId, overdraftLimit, remark);
    }

    /** 内部扣减实现，overdraftLimit=0 表示不允许透支 */
    private void spendInternal(
            Long userId,
            long amount,
            String source,
            String category,
            String bizId,
            long overdraftLimit) {
        spendInternal(userId, amount, source, category, bizId, overdraftLimit, null);
    }

    private void spendInternal(
            Long userId,
            long amount,
            String source,
            String category,
            String bizId,
            long overdraftLimit,
            String remark) {
        if (amount <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "消费金额必须大于 0");
        }
        var account =
                accountRepository
                        .findByUserIdForUpdate(userId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "积分账户不存在"));
        if (account.getBalance() + overdraftLimit < amount) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "积分余额不足");
        }

        // 按批次优先扣减（余额可能不够，批次扣完为止）
        var batches = transactionRepository.findActiveBatchesByAccountId(account.getId());
        long remaining = amount;
        for (var batch : batches) {
            if (remaining <= 0) break;
            long deduct = Math.min(batch.getRemain(), remaining);
            batch.setRemain(batch.getRemain() - deduct);
            transactionRepository.save(batch);
            remaining -= deduct;
        }
        // remaining > 0 说明进入透支区间，批次已清空，余额直接扣负

        account.setBalance(account.getBalance() - amount);
        account.setTotalSpent(account.getTotalSpent() + amount);
        accountRepository.save(account);

        var tx = new CreditTransaction();
        tx.setAccountId(account.getId());
        tx.setType(CreditTransactionType.SPEND);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getBalance());
        tx.setSource(source);
        tx.setCategory(category);
        tx.setBizId(bizId);
        tx.setRemark(remark);
        tx.setRemain(0L);
        transactionRepository.save(tx);

        log.info(
                "积分消费: userId={}, amount={}, source={}, balanceAfter={}",
                userId,
                amount,
                source,
                account.getBalance());
    }

    @Override
    @Transactional
    public void freeze(Long userId, long amount, String bizId) {
        if (amount <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "冻结金额必须大于 0");
        }
        var account =
                accountRepository
                        .findByUserIdForUpdate(userId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "积分账户不存在"));
        if (account.getBalance() < amount) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "积分余额不足，无法冻结");
        }
        account.setBalance(account.getBalance() - amount);
        account.setFrozen(account.getFrozen() + amount);
        accountRepository.save(account);
        recordTransaction(account, CreditTransactionType.FREEZE, amount, null, bizId);
        log.info("积分冻结: userId={}, amount={}, bizId={}", userId, amount, bizId);
    }

    @Override
    @Transactional
    public void unfreeze(Long userId, long amount, String bizId) {
        if (amount <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "解冻金额必须大于 0");
        }
        var account =
                accountRepository
                        .findByUserIdForUpdate(userId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "积分账户不存在"));
        if (account.getFrozen() < amount) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "冻结金额不足，无法解冻");
        }
        account.setFrozen(account.getFrozen() - amount);
        account.setBalance(account.getBalance() + amount);
        accountRepository.save(account);
        recordTransaction(account, CreditTransactionType.UNFREEZE, amount, null, bizId);
        log.info("积分解冻: userId={}, amount={}, bizId={}", userId, amount, bizId);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditAccount getAccount(Long userId) {
        return accountRepository.findByUserId(userId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CreditTransaction> getTransactions(
            Long userId, org.springframework.data.domain.Pageable pageable) {
        var account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            return org.springframework.data.domain.Page.empty(pageable);
        }
        return transactionRepository.findByAccountId(account.getId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getGroupedBalance(Long userId) {
        var account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null) return java.util.Map.of();
        var rows = transactionRepository.sumRemainByBatchType(account.getId());
        var result = new java.util.LinkedHashMap<String, Long>();
        for (var row : rows) {
            result.put(row[0] != null ? (String) row[0] : "MANUAL", ((Number) row[1]).longValue());
        }
        return result;
    }

    private CreditAccount getOrCreateAccount(Long userId) {
        return accountRepository
                .findByUserIdForUpdate(userId)
                .orElseGet(
                        () -> {
                            var account = new CreditAccount();
                            account.setUserId(userId);
                            return accountRepository.saveAndFlush(account);
                        });
    }

    private void recordTransaction(
            CreditAccount account,
            CreditTransactionType type,
            long amount,
            String source,
            String bizId) {
        var tx = new CreditTransaction();
        tx.setAccountId(account.getId());
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getBalance());
        tx.setSource(source);
        tx.setBizId(bizId);
        tx.setRemain(0L);
        transactionRepository.save(tx);
    }
}
