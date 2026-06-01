package com.xuejiai.aaf.framework.engine.credit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 积分流水仓储 */
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    Page<CreditTransaction> findByAccountId(Long accountId, Pageable pageable);

    /**
     * 查询账户下所有剩余量 > 0 的批次，按过期时间升序（NULL 排最后）。
     * 用于 spend() 按批次优先扣减。
     */
    @Query("SELECT t FROM CreditTransaction t WHERE t.accountId = :accountId AND t.remain > 0 AND t.deleted = false ORDER BY t.expireAt ASC NULLS LAST")
    List<CreditTransaction> findActiveBatchesByAccountId(Long accountId);

    /**
     * 查询已过期且仍有剩余量的批次（供过期清理定时任务使用）。
     */
    @Query("SELECT t FROM CreditTransaction t WHERE t.expireAt < :now AND t.remain > 0 AND t.deleted = false")
    List<CreditTransaction> findExpiredBatches(LocalDateTime now);

    /**
     * 按 batch_type 汇总账户下有效批次的剩余积分。
     * 返回 [batchType, sumRemain] 对。
     */
    @Query("SELECT t.batchType, SUM(t.remain) FROM CreditTransaction t WHERE t.accountId = :accountId AND t.remain > 0 AND t.deleted = false GROUP BY t.batchType")
    List<Object[]> sumRemainByBatchType(Long accountId);
}
