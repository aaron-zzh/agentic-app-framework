package com.xuejiai.aaf.module.system.repository;

import com.xuejiai.aaf.module.system.domain.PageDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 页面定义 Repository。 */
public interface PageDefRepository extends JpaRepository<PageDef, Long> {

    List<PageDef> findAllByDeletedFalse();

    Optional<PageDef> findByIdAndDeletedFalse(Long id);

    Optional<PageDef> findBySlugAndStatusAndDeletedFalse(String slug, String status);

    boolean existsBySlugAndDeletedFalse(String slug);
}
