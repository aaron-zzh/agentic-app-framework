package com.xuejiai.aaf.module.brokerage.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.brokerage.domain.BrokerageInviteCode;

public interface BrokerageInviteCodeRepository
        extends JpaRepository<BrokerageInviteCode, Long>,
                JpaSpecificationExecutor<BrokerageInviteCode> {

    Optional<BrokerageInviteCode> findByCode(String code);

    List<BrokerageInviteCode> findByContactId(Long contactId);

    Optional<BrokerageInviteCode> findByContactIdAndChannel(Long contactId, String channel);
}
