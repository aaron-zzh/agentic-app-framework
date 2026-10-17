package com.xuejiai.aaf.module.elder.profile.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.profile.domain.ElderContact;

/** 长者联系人数据访问层。 */
public interface ElderContactRepository extends CrudEntityRepository<ElderContact> {

    List<ElderContact> findAllByElderIdOrderByPriorityAscIdAsc(Long elderId);

    List<ElderContact> findAllByUserIdOrderByElderIdAscPriorityAsc(Long userId);
}
