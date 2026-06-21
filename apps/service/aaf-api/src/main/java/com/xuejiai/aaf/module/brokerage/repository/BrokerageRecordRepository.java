package com.xuejiai.aaf.module.brokerage.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageRecordStatusEnum;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRecord;

public interface BrokerageRecordRepository
        extends JpaRepository<BrokerageRecord, Long>, JpaSpecificationExecutor<BrokerageRecord> {

    /** 查询到期待解冻的流水 */
    List<BrokerageRecord> findByStatusAndUnfreezeTimeLessThanEqual(
            BrokerageRecordStatusEnum status, LocalDateTime now);

    /** 按业务类型和业务ID查询（用于退款冲回） */
    List<BrokerageRecord> findByBizTypeAndBizId(String bizType, String bizId);

    /** 批量更新状态为 VALID */
    @Modifying
    @Transactional
    @Query(
            "UPDATE BrokerageRecord r SET r.status = :newStatus WHERE r.id IN :ids AND r.status = :oldStatus")
    int batchUpdateStatus(
            @Param("ids") List<Long> ids,
            @Param("oldStatus") BrokerageRecordStatusEnum oldStatus,
            @Param("newStatus") BrokerageRecordStatusEnum newStatus);
}
