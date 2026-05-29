package com.xuejiai.aaf.module.billing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.billing.domain.Level;

public interface LevelRepository extends JpaRepository<Level, Long> {

    Optional<Level> findByCode(String code);

    /** 查找 exp 所在区间的等级 */
    Optional<Level> findByExpMinLessThanEqualAndExpMaxGreaterThanEqual(int exp, int exp2);

    List<Level> findAllByOrderBySortAsc();
}
