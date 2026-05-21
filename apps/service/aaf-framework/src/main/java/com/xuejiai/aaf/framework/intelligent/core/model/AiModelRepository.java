/**
 * 模型仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** AI 模型数据访问。 */
public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    Optional<AiModel> findByModelId(String modelId);

    Optional<AiModel> findByModelIdAndEnabledTrue(String modelId);

    List<AiModel> findByEnabledTrueOrderBySortOrder();

    List<AiModel> findByProviderAndEnabledTrue(String provider);

    @Query("""
            SELECT m FROM AiModel m
            WHERE (:provider IS NULL OR m.provider = :provider)
              AND (:enabled IS NULL OR m.enabled = :enabled)
            ORDER BY m.sortOrder ASC
            """)
    Page<AiModel> findByFilter(
            @Param("provider") String provider,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
