package com.xuejiai.aaf.module.brokerage.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRecord;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRecordRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRecordPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRecordVO;

import lombok.RequiredArgsConstructor;

/** 佣金流水 CRUD 服务（只读为主，写操作由 BrokerageService 核心服务处理）。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageRecordCrudService
        extends BaseCrudService<
                BrokerageRecord, BrokerageRecordVO, Void, Void, BrokerageRecordPageParam> {

    private final BrokerageRecordRepository brokerageRecordRepository;

    @Override
    protected JpaRepository<BrokerageRecord, Long> getRepository() {
        return brokerageRecordRepository;
    }

    @Override
    protected JpaSpecificationExecutor<BrokerageRecord> getSpecExecutor() {
        return brokerageRecordRepository;
    }

    @Override
    protected BrokerageRecordVO toVO(BrokerageRecord e) {
        return new BrokerageRecordVO(
                e.getId(),
                e.getContactId(),
                e.getSourceContactId(),
                e.getSourceLevel(),
                e.getBizType(),
                e.getBizId(),
                e.getTitle(),
                e.getAmount(),
                e.getStatus(),
                e.getFrozenDays(),
                e.getUnfreezeTime(),
                e.getRuleId(),
                e.getAppliedRate(),
                e.getCalcBaseAmount(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected BrokerageRecord toEntity(Void dto) {
        throw new com.xuejiai.aaf.common.exception.BusinessException(
                com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST, "佣金流水不支持手动创建");
    }

    @Override
    protected void updateEntity(BrokerageRecord e, Void dto) {
        throw new com.xuejiai.aaf.common.exception.BusinessException(
                com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST, "佣金流水不支持手动更新");
    }

    @Override
    protected Specification<BrokerageRecord> buildSpec(BrokerageRecordPageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (p.getContactId() != null)
                predicates.add(cb.equal(root.get("contactId"), p.getContactId()));
            if (StringUtils.hasText(p.getBizType()))
                predicates.add(cb.equal(root.get("bizType"), p.getBizType()));
            if (p.getStatus() != null) predicates.add(cb.equal(root.get("status"), p.getStatus()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "佣金流水";
    }
}
