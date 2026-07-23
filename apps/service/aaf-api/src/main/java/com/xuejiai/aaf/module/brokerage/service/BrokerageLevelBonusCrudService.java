package com.xuejiai.aaf.module.brokerage.service;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageLevelBonus;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageLevelBonusRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageLevelBonusDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageLevelBonusPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageLevelBonusVO;

import lombok.RequiredArgsConstructor;

/** 会员等级佣金加成 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageLevelBonusCrudService
        extends BaseCrudService<
                BrokerageLevelBonus,
                BrokerageLevelBonusVO,
                BrokerageLevelBonusDTO,
                BrokerageLevelBonusDTO,
                BrokerageLevelBonusPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "createTime",
                    "updateTime",
                    "ruleId",
                    "planId",
                    "level1Rate",
                    "level2Rate");

    private final BrokerageLevelBonusRepository brokerageLevelBonusRepository;

    @Override
    protected BrokerageLevelBonusRepository getRepository() {
        return brokerageLevelBonusRepository;
    }

    @Override
    protected BrokerageLevelBonusVO toVO(BrokerageLevelBonus e) {
        return new BrokerageLevelBonusVO(
                e.getId(),
                e.getRuleId(),
                e.getPlanId(),
                e.getLevel1Rate(),
                e.getLevel2Rate(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected BrokerageLevelBonus toEntity(BrokerageLevelBonusDTO dto) {
        var e = new BrokerageLevelBonus();
        e.setRuleId(dto.ruleId());
        e.setPlanId(dto.planId());
        e.setLevel1Rate(dto.level1Rate());
        e.setLevel2Rate(dto.level2Rate());
        return e;
    }

    @Override
    protected void updateEntity(BrokerageLevelBonus e, BrokerageLevelBonusDTO dto) {
        if (dto.level1Rate() != null) e.setLevel1Rate(dto.level1Rate());
        if (dto.level2Rate() != null) e.setLevel2Rate(dto.level2Rate());
    }

    @Override
    protected Specification<BrokerageLevelBonus> buildSpec(BrokerageLevelBonusPageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (p.getRuleId() != null) predicates.add(cb.equal(root.get("ruleId"), p.getRuleId()));
            if (p.getPlanId() != null) predicates.add(cb.equal(root.get("planId"), p.getPlanId()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
