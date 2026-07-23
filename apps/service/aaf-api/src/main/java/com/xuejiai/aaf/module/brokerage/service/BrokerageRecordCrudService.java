package com.xuejiai.aaf.module.brokerage.service;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRecord;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRecordRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRecordPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRecordVO;

import lombok.RequiredArgsConstructor;

/** 佣金流水只读服务，写入仅由 BrokerageService 处理。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageRecordCrudService
        extends ReadonlyCrudService<BrokerageRecord, BrokerageRecordVO, BrokerageRecordPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "createTime",
                    "updateTime",
                    "contactId",
                    "sourceContactId",
                    "sourceLevel",
                    "bizType",
                    "amount",
                    "status",
                    "frozenDays",
                    "unfreezeTime",
                    "ruleId");

    private final BrokerageRecordRepository brokerageRecordRepository;

    @Override
    protected BrokerageRecordRepository getRepository() {
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
}
