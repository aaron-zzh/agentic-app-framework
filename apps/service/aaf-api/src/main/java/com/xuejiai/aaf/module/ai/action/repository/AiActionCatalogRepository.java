package com.xuejiai.aaf.module.ai.action.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.action.domain.AiActionCatalog;

public interface AiActionCatalogRepository extends JpaRepository<AiActionCatalog, Long> {

    Optional<AiActionCatalog> findByEntitySlugAndActionKeyAndDeletedFalse(
            String entitySlug, String actionKey);

    List<AiActionCatalog> findByEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc();
}
