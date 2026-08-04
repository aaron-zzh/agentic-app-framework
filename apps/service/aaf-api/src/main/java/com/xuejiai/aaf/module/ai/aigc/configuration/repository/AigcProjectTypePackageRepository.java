package com.xuejiai.aaf.module.ai.aigc.configuration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectTypePackage;

/** 项目类型兼容包仓储，仅访问 configuration 子域实体。 */
public interface AigcProjectTypePackageRepository
        extends CrudEntityRepository<AigcProjectTypePackage> {

    List<AigcProjectTypePackage> findByStatusAndDeletedFalseOrderByIdDesc(String status);

    @Query(
            """
            SELECT packageEntity
            FROM AigcProjectTypePackage packageEntity, AigcProjectType projectType
            WHERE packageEntity.projectTypeId = projectType.id
              AND packageEntity.status = :status
              AND packageEntity.deleted = false
              AND projectType.code = :projectTypeCode
              AND projectType.status = :status
              AND projectType.deleted = false
            ORDER BY packageEntity.id DESC
            """)
    List<AigcProjectTypePackage> findPublishedByProjectTypeCode(
            @Param("projectTypeCode") String projectTypeCode, @Param("status") String status);
}
