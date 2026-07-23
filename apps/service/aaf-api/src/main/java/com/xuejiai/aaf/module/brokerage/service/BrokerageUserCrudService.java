package com.xuejiai.aaf.module.brokerage.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserUpdateDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserVO;

import lombok.RequiredArgsConstructor;

/** 分销员管理服务，仅允许管理员读取和调整分销资格。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageUserCrudService
        extends BaseCrudService<
                BrokerageUser,
                BrokerageUserVO,
                Void,
                BrokerageUserUpdateDTO,
                BrokerageUserPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "createTime",
                    "updateTime",
                    "contactId",
                    "referrerContactId",
                    "referrerBindTime",
                    "brokerageEnabled",
                    "brokerageTime",
                    "balance",
                    "frozen");

    private final BrokerageUserRepository brokerageUserRepository;

    @Override
    protected BrokerageUserRepository getRepository() {
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
    protected BrokerageUser toEntity(Void dto) {
        return unsupported("创建");
    }

    @Override
    protected void updateEntity(BrokerageUser e, BrokerageUserUpdateDTO dto) {
        if (dto.brokerageEnabled() != null) {
            e.setBrokerageEnabled(dto.brokerageEnabled());
        }
    }

    @Override
    public BrokerageUserVO create(Void request) {
        return unsupported("创建");
    }

    @Override
    public void delete(Long id) {
        unsupported("删除");
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        unsupported("批量删除");
    }

    @Override
    public void archive(List<Long> ids) {
        unsupported("归档");
    }

    @Override
    public void restore(List<Long> ids) {
        unsupported("恢复");
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

    private <T> T unsupported(String operation) {
        throw exception(GlobalErrorCode.CRUD_OPERATION_UNSUPPORTED, getEntityName(), operation);
    }
}
