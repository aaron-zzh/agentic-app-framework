package com.xuejiai.aaf.module.brokerage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;

public interface BrokerageWithdrawRepository extends CrudEntityRepository<BrokerageWithdraw> {

    Page<BrokerageWithdraw> findByContactId(Long contactId, Pageable pageable);
}
