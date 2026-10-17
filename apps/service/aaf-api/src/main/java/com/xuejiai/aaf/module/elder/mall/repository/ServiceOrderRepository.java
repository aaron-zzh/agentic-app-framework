package com.xuejiai.aaf.module.elder.mall.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceOrder;

/** 服务预约数据访问层。 */
public interface ServiceOrderRepository extends CrudEntityRepository<ServiceOrder> {

    List<ServiceOrder> findByElderIdOrderByCreateTimeDescIdDesc(Long elderId, Pageable pageable);
}
