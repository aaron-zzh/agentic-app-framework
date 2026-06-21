package com.xuejiai.aaf.module.brokerage.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRule;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageRuleRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRuleDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRulePageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRuleVO;

import lombok.RequiredArgsConstructor;

/** 佣金规则 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageRuleCrudService
        extends BaseCrudService<
                BrokerageRule,
                BrokerageRuleVO,
                BrokerageRuleDTO,
                BrokerageRuleDTO,
                BrokerageRulePageParam> {

    private final BrokerageRuleRepository brokerageRuleRepository;

    @Override
    protected JpaRepository<BrokerageRule, Long> getRepository() {
        return brokerageRuleRepository;
    }

    @Override
    protected JpaSpecificationExecutor<BrokerageRule> getSpecExecutor() {
        return brokerageRuleRepository;
    }

    @Override
    protected BrokerageRuleVO toVO(BrokerageRule e) {
        return new BrokerageRuleVO(
                e.getId(),
                e.getName(),
                e.getBizType(),
                e.getBizTargetType(),
                e.getBizTargetId(),
                e.getLevel1Rate(),
                e.getLevel2Rate(),
                e.getCalcBase(),
                e.getFixedAmount(),
                e.getFrozenDays(),
                e.getPriority(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected BrokerageRule toEntity(BrokerageRuleDTO dto) {
        var e = new BrokerageRule();
        e.setName(dto.name());
        e.setBizType(dto.bizType());
        e.setBizTargetType(dto.bizTargetType());
        e.setBizTargetId(dto.bizTargetId());
        e.setLevel1Rate(dto.level1Rate());
        e.setLevel2Rate(dto.level2Rate());
        if (StringUtils.hasText(dto.calcBase())) e.setCalcBase(dto.calcBase());
        e.setFixedAmount(dto.fixedAmount());
        if (dto.frozenDays() != null) e.setFrozenDays(dto.frozenDays());
        if (dto.priority() != null) e.setPriority(dto.priority());
        if (StringUtils.hasText(dto.status())) e.setStatus(dto.status());
        return e;
    }

    @Override
    protected void updateEntity(BrokerageRule e, BrokerageRuleDTO dto) {
        if (StringUtils.hasText(dto.name())) e.setName(dto.name());
        if (dto.bizTargetType() != null) e.setBizTargetType(dto.bizTargetType());
        if (dto.bizTargetId() != null) e.setBizTargetId(dto.bizTargetId());
        if (dto.level1Rate() != null) e.setLevel1Rate(dto.level1Rate());
        if (dto.level2Rate() != null) e.setLevel2Rate(dto.level2Rate());
        if (StringUtils.hasText(dto.calcBase())) e.setCalcBase(dto.calcBase());
        if (dto.fixedAmount() != null) e.setFixedAmount(dto.fixedAmount());
        if (dto.frozenDays() != null) e.setFrozenDays(dto.frozenDays());
        if (dto.priority() != null) e.setPriority(dto.priority());
        if (StringUtils.hasText(dto.status())) e.setStatus(dto.status());
    }

    @Override
    protected Specification<BrokerageRule> buildSpec(BrokerageRulePageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(p.getBizType()))
                predicates.add(cb.equal(root.get("bizType"), p.getBizType()));
            if (StringUtils.hasText(p.getStatus()))
                predicates.add(cb.equal(root.get("status"), p.getStatus()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "佣金规则";
    }
}
