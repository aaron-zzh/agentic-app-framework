package com.xuejiai.aaf.module.brokerage.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageInviteCode;

public interface BrokerageInviteCodeRepository extends CrudEntityRepository<BrokerageInviteCode> {

    Optional<BrokerageInviteCode> findByCode(String code);

    List<BrokerageInviteCode> findByContactId(Long contactId);

    Optional<BrokerageInviteCode> findByContactIdAndChannel(Long contactId, String channel);
}
