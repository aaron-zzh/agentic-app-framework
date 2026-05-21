package com.xuejiai.aaf.module.system.dict.repository;

import com.xuejiai.aaf.module.system.dict.domain.DictData;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DictDataRepository
        extends JpaRepository<DictData, Long>, JpaSpecificationExecutor<DictData> {

    List<DictData> findByDictTypeAndDeletedFalseOrderBySort(String dictType);

    long countByDictTypeAndDeletedFalse(String dictType);
}
