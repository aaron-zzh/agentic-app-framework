package com.xuejiai.aaf.module.ai.aigc.configuration.repository;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;

/**
 * 项目类型仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AigcProjectTypeRepository extends CrudEntityRepository<AigcProjectType> {

    java.util.Optional<AigcProjectType> findByIdAndStatusAndDeletedFalse(Long id, String status);

    java.util.Optional<AigcProjectType> findFirstByCodeAndStatusAndDeletedFalseOrderByIdDesc(
            String code, String status);
}
