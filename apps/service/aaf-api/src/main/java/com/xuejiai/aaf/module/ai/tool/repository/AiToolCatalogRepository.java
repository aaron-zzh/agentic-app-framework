package com.xuejiai.aaf.module.ai.tool.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.tool.domain.AiToolCatalog;

public interface AiToolCatalogRepository extends JpaRepository<AiToolCatalog, Long> {

    Optional<AiToolCatalog> findByToolNameAndDeletedFalse(String toolName);

    List<AiToolCatalog> findByEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc();
}
