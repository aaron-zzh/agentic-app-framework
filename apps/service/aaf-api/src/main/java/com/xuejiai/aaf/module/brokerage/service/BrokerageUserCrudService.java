package com.xuejiai.aaf.module.brokerage.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserVO;

import lombok.RequiredArgsConstructor;

/** 分销员 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageUserCrudService
        extends BaseCrudService<
                BrokerageUser,
                BrokerageUserVO,
                BrokerageUserDTO,
                BrokerageUserDTO,
                BrokerageUserPageParam> {

    private final BrokerageUserRepository brokerageUserRepository;

    @Override
    protected JpaRepository<BrokerageUser, Long> getRepository() {
        return brokerageUserRepository;
    }

    @Override
    protected JpaSpecificationExecutor<BrokerageUser> getSpecExecutor() {
        return brokerageUserRepository;
    }

    @Override
    protected BrokerageUserVO toVO(BrokerageUser e) {
        return new BrokerageUserVO(
                e.getId(),
                e.getContactId(),
                e.getReferrerContactId(),
                e.getReferrerBindTime(),
                e.getBrokerageEnabled(),
                e.getBrokerageTime(),
                e.getBalance(),
                e.getFrozen(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected BrokerageUser toEntity(BrokerageUserDTO dto) {
        var e = new BrokerageUser();
        e.setContactId(dto.contactId());
        e.setReferrerContactId(dto.referrerContactId());
        if (dto.brokerageEnabled() != null) e.setBrokerageEnabled(dto.brokerageEnabled());
        return e;
    }

    @Override
    protected void updateEntity(BrokerageUser e, BrokerageUserDTO dto) {
        if (dto.referrerContactId() != null) e.setReferrerContactId(dto.referrerContactId());
        if (dto.brokerageEnabled() != null) e.setBrokerageEnabled(dto.brokerageEnabled());
    }

    @Override
    protected Specification<BrokerageUser> buildSpec(BrokerageUserPageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (p.getContactId() != null)
                predicates.add(cb.equal(root.get("contactId"), p.getContactId()));
            if (p.getReferrerContactId() != null)
                predicates.add(cb.equal(root.get("referrerContactId"), p.getReferrerContactId()));
            if (p.getBrokerageEnabled() != null)
                predicates.add(cb.equal(root.get("brokerageEnabled"), p.getBrokerageEnabled()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "分销员";
    }
}
