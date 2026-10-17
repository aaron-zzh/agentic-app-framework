package com.xuejiai.aaf.module.elder.profile.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.profile.domain.Elder;

/** 长者档案数据访问层。 */
public interface ElderRepository extends CrudEntityRepository<Elder> {

    List<Elder> findAllByUserIdOrderByIdAsc(Long userId);
}
