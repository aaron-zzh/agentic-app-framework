package com.xuejiai.aaf.framework.engine.credit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

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

    @Override
    @Transactional
    public void earn(Long userId, long amount, String source, String bizId) {
        if (amount <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "赚取金额必须大于 0");
        }
        var account = getOrCreateAccount(userId);
        account.setBalance(account.getBalance() + amount);
        account.setTotalEarned(account.getTotalEarned() + amount);
        accountRepository.save(account);
        recordTransaction(account, CreditTransactionType.EARN, amount, source, bizId);
        log.info("积分赚取: userId={}, amount={}, source={}, bizId={}", userId, amount, source, bizId);
    }

    @Override
    @Transactional
    public void spend(Long userId, long amount, String source, String bizId) {
        if (amount <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "消费金额必须大于 0");
        }
        var account =
                accountRepository
                        .findByUserIdForUpdate(userId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "积分账户不存在"));
        if (account.getBalance() < amount) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "积分余额不足");
        }
        account.setBalance(account.getBalance() - amount);
        account.setTotalSpent(account.getTotalSpent() + amount);
        accountRepository.save(account);
        recordTransaction(account, CreditTransactionType.SPEND, amount, source, bizId);
        log.info("积分消费: userId={}, amount={}, source={}, bizId={}", userId, amount, source, bizId);
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

    /** 获取或创建账户（悲观锁保证并发安全） */
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

    /** 记录流水 */
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
        transactionRepository.save(tx);
    }
}
