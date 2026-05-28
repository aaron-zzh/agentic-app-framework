package com.xuejiai.aaf.module.system.dict.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.system.dict.domain.DictData;

public interface DictDataRepository
        extends JpaRepository<DictData, Long>, JpaSpecificationExecutor<DictData> {

    List<DictData> findByDictTypeAndStatusAndDeletedFalseOrderBySort(
            String dictType, Integer status);

    long countByDictTypeAndDeletedFalse(String dictType);

    Optional<DictData> findByDictTypeAndValueAndDeletedFalse(String dictType, String value);

    Optional<DictData> findByDictTypeAndLabelAndDeletedFalse(String dictType, String label);

    /**
     * 全部启用数据，按 dictType + sort 排序。
     *
     * @author AaronZZH & Kiro
     */
    @Query(
            "SELECT d FROM DictData d WHERE d.status = 0 AND d.deleted = false"
                    + " ORDER BY d.dictType, d.sort")
    List<DictData> findAllEnabledOrderByDictTypeAndSort();
}
