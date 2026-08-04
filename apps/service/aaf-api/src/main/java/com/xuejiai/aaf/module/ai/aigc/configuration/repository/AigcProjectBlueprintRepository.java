package com.xuejiai.aaf.module.ai.aigc.configuration.repository;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;

/**
 * 项目蓝图仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AigcProjectBlueprintRepository extends CrudEntityRepository<AigcProjectBlueprint> {

    java.util.Optional<AigcProjectBlueprint> findFirstByCodeAndStatusOrderByIdDesc(
            String code, String status);

    java.util.Optional<AigcProjectBlueprint>
            findFirstByCodeAndBlueprintVersionAndStatusOrderByIdDesc(
                    String code, String blueprintVersion, String status);

    java.util.Optional<AigcProjectBlueprint> findByIdAndStatusAndDeletedFalse(
            Long id, String status);

    java.util.Optional<AigcProjectBlueprint>
            findFirstByProjectTypeCodeAndProductionModeAndStatusAndDeletedFalseOrderByIdDesc(
                    String projectTypeCode, String productionMode, String status);
}
