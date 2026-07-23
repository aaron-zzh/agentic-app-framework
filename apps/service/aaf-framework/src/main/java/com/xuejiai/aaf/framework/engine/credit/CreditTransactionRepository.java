package com.xuejiai.aaf.framework.engine.credit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;

/** 积分流水仓储。 */
public interface CreditTransactionRepository extends CrudEntityRepository<CreditTransaction> {

    Page<CreditTransaction> findByAccountId(Long accountId, Pageable pageable);

    @Query(
            "SELECT t FROM CreditTransaction t WHERE t.accountId = :accountId AND t.remain > 0 AND t.deleted = false ORDER BY CASE t.batchType WHEN 'REWARD' THEN 1 WHEN 'WEEKLY' THEN 2 WHEN 'MANUAL' THEN 3 WHEN 'SUBSCRIPTION' THEN 4 WHEN 'TOPUP' THEN 5 ELSE 6 END ASC, t.expireAt ASC NULLS LAST")
    List<CreditTransaction> findActiveBatchesByAccountId(Long accountId);

    @Query(
            "SELECT t FROM CreditTransaction t WHERE t.expireAt < :now AND t.remain > 0 AND t.deleted = false")
    List<CreditTransaction> findExpiredBatches(LocalDateTime now);

    @Query(
            "SELECT t.batchType, SUM(t.remain) FROM CreditTransaction t WHERE t.accountId = :accountId AND t.remain > 0 AND t.deleted = false GROUP BY t.batchType")
    List<Object[]> sumRemainByBatchType(Long accountId);

    @Query(
            "SELECT COUNT(t) > 0 FROM CreditTransaction t WHERE t.type = com.xuejiai.aaf.framework.engine.credit.CreditTransactionType.EARN AND t.source = :refundSource AND t.bizId = :originalTxIdStr AND t.deleted = false")
    boolean existsRefundForOriginalTx(String refundSource, String originalTxIdStr);
}
