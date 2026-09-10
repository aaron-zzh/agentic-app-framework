package com.xuejiai.aaf.module.billing.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;

public interface SubscriptionSkuRepository extends CrudEntityRepository<SubscriptionSku> {

    Optional<SubscriptionSku> findByCode(String code);

    List<SubscriptionSku> findByPlanIdInAndStatusOrderBySortAsc(
            Collection<Long> planIds, String status);
}
