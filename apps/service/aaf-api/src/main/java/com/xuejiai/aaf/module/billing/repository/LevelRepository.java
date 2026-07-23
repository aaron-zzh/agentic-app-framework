package com.xuejiai.aaf.module.billing.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.billing.domain.Level;

public interface LevelRepository extends CrudEntityRepository<Level> {

    Optional<Level> findByCode(String code);

    Optional<Level> findByExpMinLessThanEqualAndExpMaxGreaterThanEqual(int exp, int exp2);

    List<Level> findAllByOrderBySortAsc();
}
