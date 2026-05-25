package com.xuejiai.aaf.framework.engine.dataprocess.table;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 动态数据表定义仓储。 */
public interface DataTableRepository extends JpaRepository<DataTableDefinition, Long> {

    Optional<DataTableDefinition> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
