package com.xuejiai.aaf.module.brokerage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;

public interface BrokerageWithdrawRepository
        extends JpaRepository<BrokerageWithdraw, Long>,
                JpaSpecificationExecutor<BrokerageWithdraw> {}
