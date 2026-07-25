package com.xuejiai.aaf.framework.engine.credit.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import com.xuejiai.aaf.framework.engine.credit.UpgradeSettlement;

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

    /** 升级新批次默认有效期（天） */
    private static final int UPGRADE_NEW_BATCH_EXPIRE_DAYS = 30;

    /** 退还流水来源标记，用于幂等检测。 */
    private static final String REFUND_SOURCE = "REFUND_AIGC_FAIL";

    /** 退还兜底有效期（天）：账户无活跃批次时使用。 */
    private static final int REFUND_FALLBACK_EXPIRE_DAYS = 30;

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

    @Override
    @Transactional
    public Long spend(
            Long userId,
            long amount,
            String source,
            String category,
            String bizId,
            long overdraftLimit,
            String remark,
            String bizType) {
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

        // 按批次优先扣减（最快到期的批次优先）
        long remaining = amount;
        for (var batch : transactionRepository.findActiveBatchesByAccountId(account.getId())) {
            if (remaining <= 0) break;
            long deduct = Math.min(batch.getRemain(), remaining);
            batch.setRemain(batch.getRemain() - deduct);
            transactionRepository.save(batch);
            remaining -= deduct;
        }

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
        tx.setBizType(bizType);
        tx.setBizId(bizId);
        tx.setRemark(remark);
        tx.setRemain(0L);
        Long txId = transactionRepository.save(tx).getId();

        log.info(
                "积分消费: userId={}, amount={}, source={}, category={}, bizType={}, balanceAfter={}",
                userId,
                amount,
                source,
                category,
                bizType,
                account.getBalance());
        return txId;
    }

    @Override
    @Transactional
    public Long refund(Long creditTxId, String reason) {
        if (creditTxId == null) return null;

        var original = transactionRepository.findById(creditTxId).orElse(null);
        if (original == null) {
            log.warn("[refund] 原扣款流水不存在: creditTxId={}", creditTxId);
            return null;
        }
        if (original.getType() != CreditTransactionType.SPEND) {
            log.warn(
                    "[refund] 原流水非 SPEND 类型，跳过退还: creditTxId={}, type={}",
                    creditTxId,
                    original.getType());
            return null;
        }
        // 幂等：同一原扣款流水仅退一次
        if (transactionRepository.existsRefundForOriginalTx(
                REFUND_SOURCE, String.valueOf(creditTxId))) {
            log.info("[refund] 该流水已退还过，跳过: creditTxId={}", creditTxId);
            return null;
        }

        long amount = original.getAmount();
        if (amount <= 0) return null;

        var account =
                accountRepository
                        .findById(original.getAccountId())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "积分账户不存在"));

        // 退还有效期：取最近到期的活跃批次的 expire_at；无则 30 天兜底
        LocalDateTime refundExpireAt = pickRefundExpireAt(account.getId());

        // 余额回补
        account.setBalance(account.getBalance() + amount);
        account.setTotalEarned(account.getTotalEarned() + amount);
        accountRepository.save(account);

        // 写反向 EARN 流水（保留审计可溯）
        var tx = new CreditTransaction();
        tx.setAccountId(account.getId());
        tx.setType(CreditTransactionType.EARN);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getBalance());
        tx.setSource(REFUND_SOURCE);
        tx.setBizType(original.getBizType());
        tx.setBizId(String.valueOf(creditTxId)); // 反向关联到原扣款流水
        tx.setBatchType("REFUND");
        tx.setExpireAt(refundExpireAt);
        tx.setRemain(amount);
        tx.setRemark(reason);
        Long refundTxId = transactionRepository.save(tx).getId();

        log.info(
                "积分退还: userId={}, amount={}, originalTxId={}, refundTxId={}, expireAt={}",
                account.getUserId(),
                amount,
                creditTxId,
                refundTxId,
                refundExpireAt);
        return refundTxId;
    }

    @Override
    @Transactional
    public UpgradeSettlement settleSubscriptionUpgrade(
            Long userId, long newAmount, Long newSubId, LocalDateTime newExpireAt) {
        if (newAmount <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "新月度积分必须大于 0");
        }
        var account =
                accountRepository
                        .findByUserIdForUpdate(userId)
                        .orElseGet(
                                () -> {
                                    var fresh = new CreditAccount();
                                    fresh.setUserId(userId);
                                    return accountRepository.saveAndFlush(fresh);
                                });
        LocalDateTime effectiveExpireAt =
                newExpireAt != null
                        ? newExpireAt
                        : LocalDateTime.now().plusDays(UPGRADE_NEW_BATCH_EXPIRE_DAYS);

        // === Step 1 EXPIRE：清零所有旧 SUBSCRIPTION 批次 ===
        var oldBatches = new ArrayList<CreditTransaction>();
        for (var batch : transactionRepository.findActiveBatchesByAccountId(account.getId())) {
            if ("SUBSCRIPTION".equals(batch.getBatchType())) {
                oldBatches.add(batch);
            }
        }
        long oldRemain = 0L;
        long oldAmountTotal = 0L;
        for (var batch : oldBatches) {
            oldRemain += batch.getRemain() != null ? batch.getRemain() : 0L;
            oldAmountTotal += batch.getAmount() != null ? batch.getAmount() : 0L;
        }
        long oldUsed = Math.max(0L, oldAmountTotal - oldRemain);

        Long expireTxId = null;
        if (oldRemain > 0) {
            for (var batch : oldBatches) {
                batch.setRemain(0L);
                transactionRepository.save(batch);
            }
            account.setBalance(account.getBalance() - oldRemain);
            accountRepository.save(account);
            var expireTx = new CreditTransaction();
            expireTx.setAccountId(account.getId());
            expireTx.setType(CreditTransactionType.EXPIRE);
            expireTx.setAmount(oldRemain);
            expireTx.setBalanceAfter(account.getBalance());
            expireTx.setSource("UPGRADE_OLD_BATCH_EXPIRE");
            expireTx.setBizId(String.valueOf(newSubId));
            expireTx.setBatchType("SUBSCRIPTION");
            expireTx.setRemark("升级清算：旧批次未消费部分作废");
            expireTx.setRemain(0L);
            expireTxId = transactionRepository.save(expireTx).getId();
        }

        // === Step 2 EARN：发新月度积分（新批次） ===
        account.setBalance(account.getBalance() + newAmount);
        account.setTotalEarned(account.getTotalEarned() + newAmount);
        accountRepository.save(account);
        var earnTx = new CreditTransaction();
        earnTx.setAccountId(account.getId());
        earnTx.setType(CreditTransactionType.EARN);
        earnTx.setAmount(newAmount);
        earnTx.setBalanceAfter(account.getBalance());
        earnTx.setSource("UPGRADE_NEW_MONTHLY");
        earnTx.setBizId(String.valueOf(newSubId));
        earnTx.setBatchType("SUBSCRIPTION");
        earnTx.setExpireAt(effectiveExpireAt);
        earnTx.setRemain(newAmount);
        earnTx = transactionRepository.save(earnTx);
        Long earnTxId = earnTx.getId();

        // === Step 3 SPEND：升级继承已用部分 ===
        Long spendTxId = null;
        if (oldUsed > 0) {
            // 直接从新批次扣减（新批次是当前唯一活跃批次），余额同步扣
            long deduct = Math.min(oldUsed, earnTx.getRemain());
            earnTx.setRemain(earnTx.getRemain() - deduct);
            transactionRepository.save(earnTx);
            account.setBalance(account.getBalance() - deduct);
            account.setTotalSpent(account.getTotalSpent() + deduct);
            accountRepository.save(account);
            var spendTx = new CreditTransaction();
            spendTx.setAccountId(account.getId());
            spendTx.setType(CreditTransactionType.SPEND);
            spendTx.setAmount(deduct);
            spendTx.setBalanceAfter(account.getBalance());
            spendTx.setSource("UPGRADE_INHERIT_USAGE");
            spendTx.setCategory("UPGRADE_INHERIT_USAGE");
            spendTx.setBizType("SUBSCRIPTION_UPGRADE");
            spendTx.setBizId(String.valueOf(newSubId));
            spendTx.setRemark("升级继承已用 (旧已用 " + deduct + ")");
            spendTx.setRemain(0L);
            spendTxId = transactionRepository.save(spendTx).getId();
        }

        log.info(
                "订阅升级三笔流水：userId={}, newSubId={}, oldRemain={}, newAmount={}, oldUsed={},"
                        + " expireTxId={}, earnTxId={}, spendTxId={}",
                userId,
                newSubId,
                oldRemain,
                newAmount,
                oldUsed,
                expireTxId,
                earnTxId,
                spendTxId);
        return new UpgradeSettlement(expireTxId, earnTxId, spendTxId, oldUsed);
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

    /**
     * 退还时选择有效期：
     *
     * <ul>
     *   <li>取账户最近到期的活跃批次 expire_at（保守保护，永远不会让退还的积分比原扣的批次活得更久）
     *   <li>无活跃批次时使用 30 天兜底
     * </ul>
     */
    private LocalDateTime pickRefundExpireAt(Long accountId) {
        List<CreditTransaction> active =
                transactionRepository.findActiveBatchesByAccountId(accountId);
        for (CreditTransaction batch : active) {
            if (batch.getExpireAt() != null) {
                return batch.getExpireAt();
            }
        }
        return LocalDateTime.now().plusDays(REFUND_FALLBACK_EXPIRE_DAYS);
    }
}
