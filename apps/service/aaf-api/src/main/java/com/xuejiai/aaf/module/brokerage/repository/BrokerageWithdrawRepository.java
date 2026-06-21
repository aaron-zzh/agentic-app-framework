package com.xuejiai.aaf.module.brokerage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;

public interface BrokerageWithdrawRepository
        extends JpaRepository<BrokerageWithdraw, Long>,
                JpaSpecificationExecutor<BrokerageWithdraw> {

    Page<BrokerageWithdraw> findByContactId(Long contactId, Pageable pageable);
}
