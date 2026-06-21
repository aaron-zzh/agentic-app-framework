package com.xuejiai.aaf.module.brokerage.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageInviteCode;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageInviteCodeRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodeDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodePageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodeVO;

import lombok.RequiredArgsConstructor;

/** 邀请码 CRUD 服务（后台管理用）。生成逻辑由 BrokerageInviteCodeService 负责。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageInviteCodeCrudService
        extends BaseCrudService<
                BrokerageInviteCode,
                BrokerageInviteCodeVO,
                BrokerageInviteCodeDTO,
                BrokerageInviteCodeDTO,
                BrokerageInviteCodePageParam> {

    private final BrokerageInviteCodeRepository inviteCodeRepository;
    private final BrokerageInviteCodeService inviteCodeService;

    @Override
    protected JpaRepository<BrokerageInviteCode, Long> getRepository() {
        return inviteCodeRepository;
    }

    @Override
    protected JpaSpecificationExecutor<BrokerageInviteCode> getSpecExecutor() {
        return inviteCodeRepository;
    }

    @Override
    protected BrokerageInviteCodeVO toVO(BrokerageInviteCode e) {
        return new BrokerageInviteCodeVO(
                e.getId(),
                e.getContactId(),
                e.getCode(),
                e.getChannel(),
                e.getUsedCount(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    /** 创建时通过 getOrCreate 保证幂等，不直接 new 实体。 */
    @Override
    @Transactional
    public BrokerageInviteCodeVO create(BrokerageInviteCodeDTO request) {
        var entity = inviteCodeService.getOrCreate(request.contactId(), request.channel());
        return toVO(entity);
    }

    @Override
    protected BrokerageInviteCode toEntity(BrokerageInviteCodeDTO dto) {
        // create() 已覆写，此方法不会被调用
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateEntity(BrokerageInviteCode e, BrokerageInviteCodeDTO dto) {
        if (StringUtils.hasText(dto.channel())) e.setChannel(dto.channel());
    }

    @Override
    protected Specification<BrokerageInviteCode> buildSpec(BrokerageInviteCodePageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (p.getContactId() != null)
                predicates.add(cb.equal(root.get("contactId"), p.getContactId()));
            if (StringUtils.hasText(p.getChannel()))
                predicates.add(cb.equal(root.get("channel"), p.getChannel()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "邀请码";
    }
}
